// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.filedistribution.maintenance.FileDistributionCleanup;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilities related to file distribution on config servers.
 *
 * @author musum
 * @author gjoranv
 */
public class FileDistributionUtil {

    public static List<String> getOtherConfigServersInCluster(ConfigserverConfig configserverConfig) {
        return ConfigServerSpec.fromConfig(configserverConfig)
                               .stream()
                               .filter(spec -> !spec.getHostName().equals(HostName.getLocalhost()))
                               .map(spec -> "tcp/" + spec.getHostName() + ":" + spec.getConfigServerPort())
                               .collect(Collectors.toList());
    }

    public static boolean fileReferenceExistsOnDisk(File downloadDirectory, FileReference applicationPackageReference) {
        return FileDistributionCleanup.getFileReferencesOnDisk(downloadDirectory).contains(applicationPackageReference.value());
    }

}
