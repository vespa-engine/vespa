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
        Instant instant = controller().clock().instant();
        Version wantedVersion = release.version(currentTarget.get(), instant);
        Version currentVersion = currentTarget.get().version();
        if (release instanceof CalendarVersionedRelease) {
            // Estimate the next change
            while (!wantedVersion.isAfter(currentVersion)) {
                instant = instant.plus(Duration.ofDays(1));
                wantedVersion = release.version(currentTarget.get(), instant);
            }
        } else if (!wantedVersion.isAfter(currentVersion)) {
            return Optional.empty(); // No change right now, and we cannot predict the next change for this kind of release
        }
        // Find trigger time
        while (!canTriggerAt(instant)) {
            instant = instant.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
        }
        return Optional.of(new Change(wantedVersion, release.upgradeBudget(), instant));
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

    private boolean canTriggerAt(Instant instant) {
        ZonedDateTime dateTime = instant.atZone(ZoneOffset.UTC);
        int hourOfDay = dateTime.getHour();
        int dayOfWeek = dateTime.getDayOfWeek().getValue();
        // Upgrade can only be scheduled between 07:00 (02:00 in CD systems) and 12:59 UTC, Monday-Thursday
        int startHour = controller().system().isCd() ? 2 : 7;
        return hourOfDay >= startHour && hourOfDay <= 12 && dayOfWeek < 5;
    }

    private interface Release {

        /** The version number of this */
        Version version(OsVersionTarget currentTarget, Instant now);

        /** The budget to use when upgrading to this */
        Duration upgradeBudget();

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
        public Version version(OsVersionTarget currentTarget, Instant now) {
            OsRelease release = artifactRepository.osRelease(currentTarget.osVersion().version().getMajor(), tag());
            boolean cooldownPassed = !release.taggedAt().plus(cooldown()).isAfter(now);
            return cooldownPassed ? release.version() : currentTarget.osVersion().version();
        }

        @Override
        public Duration upgradeBudget() {
            return Duration.ZERO; // Upgrades to tagged releases happen in-place so no budget is required
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

        /** The time that should elapse between versions */
        private static final Duration SCHEDULING_STEP = Duration.ofDays(60);

        /** The day of week new releases are published */
        private static final DayOfWeek RELEASE_DAY = DayOfWeek.MONDAY;

        private static final DateTimeFormatter CALENDAR_VERSION_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

        public CalendarVersionedRelease {
            Objects.requireNonNull(system);
        }

        @Override
        public Version version(OsVersionTarget currentTarget, Instant now) {
            Version currentVersion = currentTarget.osVersion().version();
            Version wantedVersion = asVersion(dateOfWantedVersion(now), currentVersion);
            return wantedVersion.isAfter(currentVersion) ? wantedVersion : currentVersion;
        }

        @Override
        public Duration upgradeBudget() {
            return system.isCd() ? Duration.ZERO : Duration.ofDays(14);
        }

        /**
         * Calculate the date of the wanted version relative to now. A given zone will choose the oldest release
         * available which is not older than this date.
         */
        static LocalDate dateOfWantedVersion(Instant now) {
            Instant candidate = START_OF_SCHEDULE;
            while (!candidate.plus(SCHEDULING_STEP).isAfter(now)) {
                candidate = candidate.plus(SCHEDULING_STEP);
            }
            LocalDate date = LocalDate.ofInstant(candidate, ZoneOffset.UTC);
            return releaseDayOf(date);
        }

        private static LocalDate releaseDayOf(LocalDate date) {
            int releaseDayDelta = RELEASE_DAY.getValue() - date.getDayOfWeek().getValue();
            return date.plusDays(releaseDayDelta);
        }

        private static Version asVersion(LocalDate dateOfVersion, Version currentVersion) {
            String calendarVersion = dateOfVersion.format(CALENDAR_VERSION_PATTERN);
            return new Version(currentVersion.getMajor(),
                               currentVersion.getMinor(),
                               currentVersion.getMicro(),
                               calendarVersion);
        }

    }

}
