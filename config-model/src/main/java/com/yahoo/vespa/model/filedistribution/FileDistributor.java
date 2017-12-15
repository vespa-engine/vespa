// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.Host;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for directing distribution of files to hosts.
 *
 * @author Tony Vaagenes
 */
public class FileDistributor {

    private final FileRegistry fileRegistry;
    private final List<ConfigServerSpec> configServerSpecs;

    /** A map from files to the hosts to which that file should be distributed */
    private final Map<FileReference, Set<Host>> filesToHosts = new LinkedHashMap<>();

    /**
     * Adds the given file to the associated application packages' registry of file and marks the file
     * for distribution to the given hosts.
     * <b>Note: This class receives ownership of the given collection.</b>
     *
     * @return the reference to the file, created by the application package
     */
    public FileReference sendFileToHosts(String relativePath, Collection<Host> hosts) {
        FileReference reference = fileRegistry.addFile(relativePath);
        addToFilesToDistribute(reference, hosts);

        return reference;
    }

    /**
     * Adds the given file to the associated application packages' registry of file and marks the file
     * for distribution to the given hosts.
     * <b>Note: This class receives ownership of the given collection.</b>
     *
     * @return the reference to the file, created by the application package
     */
    public FileReference sendUriToHosts(String uri, Collection<Host> hosts) {
        FileReference reference = fileRegistry.addUri(uri);
        addToFilesToDistribute(reference, hosts);

        return reference;
    }

    /** Same as sendFileToHost(relativePath,Collections.singletonList(host) */
    public FileReference sendFileToHost(String relativePath, Host host) {
        return sendFileToHosts(relativePath, Arrays.asList(host));
    }

    public FileReference sendUriToHost(String uri, Host host) {
        return sendUriToHosts(uri, Arrays.asList(host));
    }

    private void addToFilesToDistribute(FileReference reference, Collection<Host> hosts) {
        Set<Host> oldHosts = getHosts(reference);
        oldHosts.addAll(hosts);
    }

    private Set<Host> getHosts(FileReference reference) {
        Set<Host> hosts = filesToHosts.get(reference);
        if (hosts == null) {
            hosts = new HashSet<>();
            filesToHosts.put(reference, hosts);
        }
        return hosts;
    }

    public FileDistributor(FileRegistry fileRegistry, List<ConfigServerSpec> configServerSpecs) {
        this.fileRegistry = fileRegistry;
        this.configServerSpecs = configServerSpecs;
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

    public Set<String> getTargetHostnames() {
        return getTargetHosts().stream().map(Host::getHostname).collect(Collectors.toSet());
    }

    /** Returns the host which is the source of the files */
    public String fileSourceHost() {
        return fileRegistry.fileSourceHost();
    }

    public Set<FileReference> allFilesToSend() {
        return filesToHosts.keySet();
    }

    // should only be called during deploy
    public void sendDeployedFiles(FileDistribution dbHandler) {
        String fileSourceHost = fileSourceHost();
        for (Host host : getTargetHosts()) {
            if ( ! host.getHostname().equals(fileSourceHost)) {
                dbHandler.sendDeployedFiles(host.getHostname(), filesToSendToHost(host));
                dbHandler.startDownload(host.getHostname(), ConfigProxy.BASEPORT, filesToSendToHost(host));
            }
        }
        // Ask other config server to download, for redundancy
        // TODO: Enable the below when config server is able to receive files properly
        /*
        if (configServerSpecs != null)
            configServerSpecs.stream()
                    .filter(configServerSpec -> !configServerSpec.getHostName().equals(fileSourceHost))
                    .forEach(spec -> dbHandler.startDownload(spec.getHostName(), spec.getConfigServerPort(), allFilesToSend()));
                    */

        dbHandler.sendDeployedFiles(fileSourceHost, allFilesToSend());
        dbHandler.removeDeploymentsThatHaveDifferentApplicationId(getTargetHostnames());
    }

    // should only be called during deploy, and only once, since it leads to file distributor
    // rescanning all files, which is very expensive ATM (April 2016)
    public void reloadDeployFileDistributor(FileDistribution dbHandler) {
        dbHandler.reloadDeployFileDistributor();
    }

}
