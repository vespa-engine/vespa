// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.OsRelease;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

        Version version = release.version(currentTarget.get(), now);
        if (!version.isAfter(currentTarget.get().osVersion().version())) return;
        controller().upgradeOsIn(cloud, version, release.upgradeBudget(), false);
    }

    private boolean upgradingToNewMajor(CloudName cloud) {
        return controller().osVersionStatus().versionsIn(cloud).stream()
                           .map(Version::getMajor)
                           .distinct()
                           .count() > 1;
    }

    private boolean canTriggerAt(Instant instant) {
        int hourOfDay = instant.atZone(ZoneOffset.UTC).getHour();
        int dayOfWeek = instant.atZone(ZoneOffset.UTC).getDayOfWeek().getValue();
        // Upgrade can only be scheduled between 07:00 (02:00 in CD systems) and 12:59 UTC, Monday-Thursday
        int startHour = controller().system().isCd() ? 2 : 7;
        return hourOfDay >= startHour && hourOfDay <= 12 && dayOfWeek < 5;
    }

    private Release releaseIn(CloudName cloud) {
        boolean useTaggedRelease = controller().zoneRegistry().zones().reprovisionToUpgradeOs().ofCloud(cloud)
                                               .zones().isEmpty();
        if (useTaggedRelease) {
            return new TaggedRelease(controller().system(), controller().serviceRegistry().artifactRepository());
        }
        return new CalendarVersionedRelease(controller().system());
    }

    private interface Release {

        /** The version number of this */
        Version version(OsVersionTarget currentTarget, Instant now);

        /** The budget to use when upgrading to this */
        Duration upgradeBudget();

    }

    /** OS release based on a tag */
    private static class TaggedRelease implements Release {

        private final SystemName system;
        private final ArtifactRepository artifactRepository;

        private TaggedRelease(SystemName system, ArtifactRepository artifactRepository) {
            this.system = Objects.requireNonNull(system);
            this.artifactRepository = Objects.requireNonNull(artifactRepository);
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
