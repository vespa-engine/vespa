// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.vespa.model.Host;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * Sends RPC requests to hosts (tenant hosts and config servers) asking them to start download of files. This is used
 * during prepare of an application. Services themselves will also request files, the methods in this class are used
 * so that hosts can start downloading files before services gets new config that needs these files. It also tries
 * to make sure that all config servers (not just the one where the application was deployed) have the files available.
 *
 * @author Tony Vaagenes
 */
public class FileDistributor {


    /** A map from file reference to the hosts to which that file reference should be distributed */
    private final Map<FileReference, Set<Host>> filesToHosts = new LinkedHashMap<>();

    public FileDistributor() { }

    public void sendFileReference(FileReference reference, Host host) {
        filesToHosts.computeIfAbsent(reference, k -> new HashSet<>()).add(host);
    }

    /** Returns the files which has been marked for distribution to the given host */
    public Set<FileReference> filesToSendToHost(Host host) {
        Set<FileReference> files = new HashSet<>();

        for (Map.Entry<FileReference,Set<Host>> e : filesToHosts.entrySet()) {
            if (e.getValue().contains(host)) {
                files.add(e.getKey());
            }
        }
        return files;
    }

    public Set<Host> getTargetHosts() {
        Set<Host> hosts = new HashSet<>();
        for (Set<Host> hostSubset: filesToHosts.values())
            hosts.addAll(hostSubset);
        return hosts;
    }

    public Set<FileReference> allFilesToSend() {
        return Set.copyOf(filesToHosts.keySet());
    }

}
