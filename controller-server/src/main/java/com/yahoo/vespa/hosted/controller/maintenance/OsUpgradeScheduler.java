// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.OsRelease;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Automatically schedule upgrades to the next OS version.
 *
 * @author mpolden
 */
public class OsUpgradeScheduler extends ControllerMaintainer {

    public OsUpgradeScheduler(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        Instant now = controller().clock().instant();
        for (var cloud : controller().clouds()) {
            Optional<Change> change = changeIn(cloud);
            if (change.isEmpty()) continue;
            if (!change.get().scheduleAt(now)) continue;
            controller().upgradeOsIn(cloud, change.get().version(), change.get().upgradeBudget(), false);
        }
        return 1.0;
    }

    /** Returns the wanted change for given cloud, if any */
    public Optional<Change> changeIn(CloudName cloud) {
        Optional<OsVersionTarget> currentTarget = controller().osVersionTarget(cloud);
        if (currentTarget.isEmpty()) return Optional.empty();
        if (upgradingToNewMajor(cloud)) return Optional.empty(); // Skip further upgrades until major version upgrade is complete

        Release release = releaseIn(cloud);
        Instant now = controller().clock().instant();
        return release.change(currentTarget.get().version(), now);
    }

    private boolean upgradingToNewMajor(CloudName cloud) {
        return controller().osVersionStatus().versionsIn(cloud).stream()
                           .map(Version::getMajor)
                           .distinct()
                           .count() > 1;
    }

    private Release releaseIn(CloudName cloud) {
        boolean useTaggedRelease = controller().zoneRegistry().zones().all().reprovisionToUpgradeOs().in(cloud)
                                             .zones().isEmpty();
        if (useTaggedRelease) {
            return new TaggedRelease(controller().system(), controller().serviceRegistry().artifactRepository());
        }
        return new CalendarVersionedRelease(controller().system());
    }

    private static boolean canTriggerAt(Instant instant, boolean isCd) {
        ZonedDateTime dateTime = instant.atZone(ZoneOffset.UTC);
        int hourOfDay = dateTime.getHour();
        int dayOfWeek = dateTime.getDayOfWeek().getValue();
        // Upgrade can only be scheduled between 07:00 (02:00 in CD systems) and 12:59 UTC, Monday-Thursday
        int startHour = isCd ? 2 : 7;
        return hourOfDay >= startHour && hourOfDay <= 12 && dayOfWeek < 5;
    }

    /** Returns the earliest time an upgrade can be scheduled on the day of instant, in given system */
    private static Instant schedulingInstant(Instant instant, SystemName system) {
        instant = instant.truncatedTo(ChronoUnit.DAYS);
        while (!canTriggerAt(instant, system.isCd())) {
            instant = instant.plus(Duration.ofHours(1));
        }
        return instant;
    }

    private interface Release {

        /** The pending change for this release at given instant, if any */
        Optional<Change> change(Version currentVersion, Instant instant);

    }

    /** OS version change, its budget and the earliest time it can be scheduled */
    public record Change(Version version, Duration upgradeBudget, Instant scheduleAt) {

        public Change {
            Objects.requireNonNull(version);
            Objects.requireNonNull(upgradeBudget);
            Objects.requireNonNull(scheduleAt);
        }

        /** Returns whether this can be scheduled at given instant */
        public boolean scheduleAt(Instant instant) {
            return !instant.isBefore(scheduleAt);
        }

    }

    /** OS release based on a tag */
    private record TaggedRelease(SystemName system, ArtifactRepository artifactRepository) implements Release {

        public TaggedRelease {
            Objects.requireNonNull(system);
            Objects.requireNonNull(artifactRepository);
        }

        @Override
        public Optional<Change> change(Version currentVersion, Instant instant) {
            OsRelease release = artifactRepository.osRelease(currentVersion.getMajor(), tag());
            if (!release.version().isAfter(currentVersion)) return Optional.empty();
            Instant scheduleAt = schedulingInstant(release.taggedAt().plus(cooldown()), system);
            return Optional.of(new Change(release.version(), Duration.ZERO, scheduleAt));
        }

        /** Returns the release tag tracked by this system */
        private OsRelease.Tag tag() {
            return system.isCd() ? OsRelease.Tag.latest : OsRelease.Tag.stable;
        }

        /** The cool-down period that must pass before a release can be used */
        private Duration cooldown() {
            return system.isCd() ? Duration.ofDays(1) : Duration.ZERO;
        }

    }

    /** OS release based on calendar-versioning */
    record CalendarVersionedRelease(SystemName system) implements Release {

        /** A fixed point in time which the release schedule is calculated from */
        private static final Instant START_OF_SCHEDULE = LocalDate.of(2022, 1, 1)
                                                                  .atStartOfDay()
                                                                  .toInstant(ZoneOffset.UTC);

        /** The approximate time that should elapse between versions */
        private static final Duration SCHEDULING_STEP = Duration.ofDays(60);

        /** The day of week new releases are published */
        private static final DayOfWeek RELEASE_DAY = DayOfWeek.TUESDAY;

        public CalendarVersionedRelease {
            Objects.requireNonNull(system);
        }

        @Override
        public Optional<Change> change(Version currentVersion, Instant instant) {
            CalendarVersion version = findVersion(instant, currentVersion);
            Instant predicatedInstant = instant;
            while (!version.version().isAfter(currentVersion)) {
                predicatedInstant = predicatedInstant.plus(Duration.ofDays(1));
                version = findVersion(predicatedInstant, currentVersion);
            }
            Duration cooldown = remainingCooldownAt(instant, version);
            Instant schedulingInstant = schedulingInstant(instant.plus(cooldown), system);
            return Optional.of(new Change(version.version(), upgradeBudget(), schedulingInstant));
        }

        private Duration upgradeBudget() {
            return system.isCd() ? Duration.ZERO : Duration.ofDays(14);
        }

        private Duration remainingCooldownAt(Instant instant, CalendarVersion version) {
            Duration minAge = system.isCd()
                    ? Duration.ofDays(1)                          // CD: Give new releases some time to propagate
                    : Duration.ofDays(7 - RELEASE_DAY.ordinal()); // non-CD: Wait until start of the following week
            Duration age = version.age(instant);
            if (age.compareTo(minAge) < 0) {
                return minAge.minus(age);
            }
            return Duration.ZERO;
        }

        /** Find the most recent version available according to the scheduling step, relative to now */
        static CalendarVersion findVersion(Instant now, Version currentVersion) {
            Instant candidate = START_OF_SCHEDULE;
            while (!candidate.plus(SCHEDULING_STEP).isAfter(now)) {
                candidate = candidate.plus(SCHEDULING_STEP);
            }
            LocalDate date = LocalDate.ofInstant(candidate, ZoneOffset.UTC);
            while (date.getDayOfWeek() != RELEASE_DAY) {
                date = date.minusDays(1);
            }
            return CalendarVersion.from(date, currentVersion);
        }

        record CalendarVersion(Version version, LocalDate date) {

            private static final DateTimeFormatter CALENDAR_VERSION_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

            private static CalendarVersion from(LocalDate date, Version currentVersion) {
                String qualifier = date.format(CALENDAR_VERSION_PATTERN);
                return new CalendarVersion(new Version(currentVersion.getMajor(),
                                                       currentVersion.getMinor(),
                                                       currentVersion.getMicro(),
                                                       qualifier),
                                           date);
            }

            /** Returns the age of this at given instant, in whole days */
            private Duration age(Instant instant) {
                return Duration.between(date.atStartOfDay().toInstant(ZoneOffset.UTC),
                                        instant.truncatedTo(ChronoUnit.DAYS));
            }

        }

    }

}
