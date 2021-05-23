// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.Host;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final FileRegistry fileRegistry;
    private final List<ConfigServerSpec> configServerSpecs;
    private final boolean isHosted;

    /** A map from file reference to the hosts to which that file reference should be distributed */
    private final Map<FileReference, Set<Host>> filesToHosts = new LinkedHashMap<>();

    public FileDistributor(FileRegistry fileRegistry, List<ConfigServerSpec> configServerSpecs, boolean isHosted) {
        this.fileRegistry = fileRegistry;
        this.configServerSpecs = configServerSpecs;
        this.isHosted = isHosted;
    }

    /**
     * Adds the given file to the associated application packages' registry of file and marks the file
     * for distribution to the given host.
     *
     * @return the reference to the file, created by the application package
     */
    public FileReference sendFileToHost(String relativePath, Host host) {
        return addFileReference(fileRegistry.addFile(relativePath), host);
    }

    /**
     * Adds the given file to the associated application packages' registry of file and marks the file
     * for distribution to the given host.
     *
     * @return the reference to the file, created by the application package
     */
    public FileReference sendUriToHost(String uri, Host host) {
        return addFileReference(fileRegistry.addUri(uri), host);
    }

    private FileReference addFileReference(FileReference reference, Host host) {
        filesToHosts.computeIfAbsent(reference, k -> new HashSet<>()).add(host);
        return reference;
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

    /** Returns the host which is the source of the files */
    public String fileSourceHost() {
        return fileRegistry.fileSourceHost();
    }

    public Set<FileReference> allFilesToSend() {
        return Set.copyOf(filesToHosts.keySet());
    }

    // should only be called during deploy
    public void sendDeployedFiles(FileDistribution dbHandler) {
        String fileSourceHost = fileSourceHost();

        // Ask other config servers to download, for redundancy
        configServerSpecs.stream()
                .filter(spec -> !spec.getHostName().equals(fileSourceHost))
                .forEach(spec -> dbHandler.startDownload(spec.getHostName(), spec.getConfigServerPort(), allFilesToSend()));

        // Skip starting download for application hosts when on hosted, since this is just a hint and requests for files
        // will fail until the application is activated (this call is done when preparing an application deployment)
        // due to authorization of RPC requests on config servers only considering files belonging to active applications
        if (isHosted) return;

        getTargetHosts().stream()
                .filter(host -> ! host.getHostname().equals(fileSourceHost))
                .forEach(host -> dbHandler.startDownload(host.getHostname(), ConfigProxy.BASEPORT, filesToSendToHost(host)));
    }

}
