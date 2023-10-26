// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.server.ConfigServerSpec;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilities related to file distribution on config servers.
 *
 * @author musum
 * @author gjoranv
 */
public class FileDistributionUtil {

    /**
     * Returns all files in the given directory, non-recursive.
     */
    public static Set<String> getFileReferencesOnDisk(File directory) {
        Set<String> fileReferencesOnDisk = new HashSet<>();
        File[] filesOnDisk = directory.listFiles();
        if (filesOnDisk != null)
            fileReferencesOnDisk.addAll(Arrays.stream(filesOnDisk).map(File::getName).collect(Collectors.toSet()));
        return fileReferencesOnDisk;
    }

    public static List<String> getOtherConfigServersInCluster(ConfigserverConfig configserverConfig) {
        return ConfigServerSpec.fromConfig(configserverConfig)
                               .stream()
                               .filter(spec -> !spec.getHostName().equals(HostName.getLocalhost()))
                               .map(spec -> "tcp/" + spec.getHostName() + ":" + spec.getConfigServerPort())
                               .toList();
    }

    public static boolean fileReferenceExistsOnDisk(File downloadDirectory, FileReference applicationPackageReference) {
        return getFileReferencesOnDisk(downloadDirectory).contains(applicationPackageReference.value());
    }

}
