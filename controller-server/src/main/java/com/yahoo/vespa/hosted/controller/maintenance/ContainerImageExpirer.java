// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.container.ContainerImage;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodically expire unused container images.
 *
 * @author mpolden
 */
public class ContainerImageExpirer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(ContainerImageExpirer.class.getName());

    private static final Duration MIN_AGE = Duration.ofDays(14);

    public ContainerImageExpirer(Controller controller, Duration interval) {
        super(controller, interval, null, expiringSystems());
    }

    @Override
    protected boolean maintain() {
        Instant now = controller().clock().instant();
        VersionStatus versionStatus = controller().readVersionStatus();
        List<ContainerImage> imagesToExpire = controller().serviceRegistry().containerRegistry().list().stream()
                                                          .filter(image -> canExpire(image, now, versionStatus))
                                                          .collect(Collectors.toList());
        if (!imagesToExpire.isEmpty()) {
            log.log(Level.INFO, "Expiring container images: " + imagesToExpire);
            controller().serviceRegistry().containerRegistry().deleteAll(imagesToExpire);
        }
        return true;
    }

    /** Returns whether given image can be expired */
    private boolean canExpire(ContainerImage image, Instant now, VersionStatus versionStatus) {
        List<VespaVersion> versions = versionStatus.versions();
        if (versions.isEmpty()) return false;

        if (versionStatus.isActive(image.version())) return false;
        if (image.createdAt().isAfter(now.minus(MIN_AGE))) return false;

        Version maxVersion = versions.stream().map(VespaVersion::versionNumber).max(Comparator.naturalOrder()).get();
        if (image.version().isAfter(maxVersion)) return false; // A future version

        return true;
    }

    /** Returns systems where images can be expired */
    private static Set<SystemName> expiringSystems() {
        // Run only in public and main. Public systems have distinct container registries, while main and CD have
        // shared registries.
        return EnumSet.of(SystemName.Public, SystemName.PublicCd, SystemName.main);
    }

}
