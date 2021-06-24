// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.StableOsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (!canTriggerAt(now)) return 1.0;
        for (var cloud : controller().clouds()) {
            Release release = releaseIn(cloud);
            upgradeTo(release, cloud, now);
        }
        return 1.0;
    }

    /** Upgrade to given release in cloud */
    private void upgradeTo(Release release, CloudName cloud, Instant now) {
        Optional<OsVersionTarget> currentTarget = controller().osVersionTarget(cloud);
        if (currentTarget.isEmpty()) return;
        if (upgradingToNewMajor(cloud)) return; // Skip further upgrades until major version upgrade is complete

        controller().upgradeOsIn(cloud,
                                 release.version(currentTarget.get(), now),
                                 release.upgradeBudget(),
                                 false);
    }

    private boolean upgradingToNewMajor(CloudName cloud) {
        Set<Integer> majorVersions = controller().osVersionStatus().versionsIn(cloud).stream()
                                                 .map(Version::getMajor)
                                                 .collect(Collectors.toSet());
        return majorVersions.size() > 1;
    }

    private boolean canTriggerAt(Instant instant) {
        int hourOfDay = instant.atZone(ZoneOffset.UTC).getHour();
        int dayOfWeek = instant.atZone(ZoneOffset.UTC).getDayOfWeek().getValue();
        // Upgrade can only be scheduled between 07:00 and 12:59 UTC, Monday-Thursday
        return hourOfDay >= 7 && hourOfDay <= 12 &&
               dayOfWeek < 5;
    }

    private Release releaseIn(CloudName cloud) {
        boolean useStableRelease = controller().zoneRegistry().zones().reprovisionToUpgradeOs().ofCloud(cloud)
                                               .zones().isEmpty();
        if (useStableRelease) {
            return new StableRelease(controller().system(), controller().serviceRegistry().artifactRepository());
        }
        return new CalendarVersionedRelease(controller().system());
    }

    private interface Release {

        /** The version number of this */
        Version version(OsVersionTarget currentTarget, Instant now);

        /** The budget to use when upgrading to this */
        Duration upgradeBudget();

    }

    /** OS release based on a stable tag */
    private static class StableRelease implements Release {

        private final SystemName system;
        private final ArtifactRepository artifactRepository;

        private StableRelease(SystemName system, ArtifactRepository artifactRepository) {
            this.system = Objects.requireNonNull(system);
            this.artifactRepository = Objects.requireNonNull(artifactRepository);
        }

        @Override
        public Version version(OsVersionTarget currentTarget, Instant now) {
            StableOsVersion stableVersion = artifactRepository.stableOsVersion(currentTarget.osVersion().version().getMajor());
            boolean cooldownPassed = stableVersion.promotedAt().isBefore(now.minus(cooldown()));
            return cooldownPassed ? stableVersion.version() : currentTarget.osVersion().version();
        }

        @Override
        public Duration upgradeBudget() {
            return Duration.ZERO; // Stable releases happen in-place so no budget is required
        }

        /** The cool-down period that must pass before a stable version can be used */
        private Duration cooldown() {
            return system.isCd() ? Duration.ZERO : Duration.ofDays(14);
        }

    }

    /** OS release based on calendar-versioning */
    private static class CalendarVersionedRelease implements Release {

        /** The time to wait before scheduling upgrade to next version */
        private static final Duration SCHEDULING_INTERVAL = Duration.ofDays(45);

        /**
         * The interval at which new versions become available. We use this to avoid scheduling upgrades to a version
         * that has not been released yet. Example: Version N is the latest one and target is set to N+1. If N+1 does
         * not exist the zone will not converge until N+1 has been released and we may end up triggering multiple
         * rounds of upgrades.
         */
        private static final Duration AVAILABILITY_INTERVAL = Duration.ofDays(7);

        private static final DateTimeFormatter CALENDAR_VERSION_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

        private final SystemName system;

        public CalendarVersionedRelease(SystemName system) {
            this.system = Objects.requireNonNull(system);
        }

        @Override
        public Version version(OsVersionTarget currentTarget, Instant now) {
            Instant scheduledAt = currentTarget.scheduledAt();
            if (currentTarget.scheduledAt().equals(Instant.EPOCH)) {
                // TODO(mpolden): Remove this block after 2021-09-01. If we haven't written scheduledAt at least once,
                //                we need to deduce the scheduled instant from the version.
                Version version = currentTarget.osVersion().version();
                String qualifier = version.getQualifier();
                if (!qualifier.matches("^\\d{8,}")) throw new IllegalArgumentException("Could not parse instant from version " + version);

                String dateString = qualifier.substring(0, 8);
                scheduledAt = LocalDate.parse(dateString, CALENDAR_VERSION_PATTERN)
                                       .atStartOfDay(ZoneOffset.UTC)
                                       .toInstant();
            }
            Version currentVersion = currentTarget.osVersion().version();
            if (scheduledAt.isBefore(now.minus(SCHEDULING_INTERVAL))) {
                String calendarVersion = now.minus(AVAILABILITY_INTERVAL)
                                            .atZone(ZoneOffset.UTC)
                                            .format(CALENDAR_VERSION_PATTERN);
                return new Version(currentVersion.getMajor(),
                                   currentVersion.getMinor(),
                                   currentVersion.getMicro(),
                                   calendarVersion);
            }
            return currentVersion; // New version should not be scheduled yet
        }

        @Override
        public Duration upgradeBudget() {
            return system.isCd() ? Duration.ZERO : Duration.ofDays(14);
        }

    }

}
