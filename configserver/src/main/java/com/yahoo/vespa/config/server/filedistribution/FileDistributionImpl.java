// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;

import java.time.Duration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author baldersheim
 */
public class FileDistributionImpl implements FileDistribution, RequestWaiter {

    private final static Logger log = Logger.getLogger(FileDistributionImpl.class.getName());
    private final static Duration rpcTimeout = Duration.ofSeconds(11);

    private final Supervisor supervisor;
    private final FlagSource flagSource;

    public FileDistributionImpl(Supervisor supervisor, FlagSource flagSource) {
        this.supervisor = supervisor;
        this.flagSource = flagSource;
    }

    /**
     * Notifies client which file references it should start downloading. It's OK if the call does not succeed,
     * as this is just a hint to the client to start downloading. Currently the only client is the config server
     *
     * @param hostName       host which should be notified about file references to download
     * @param port           port which should be used when notifying
     * @param fileReferences set of file references to start downloading
     */
    @Override
    public void triggerDownload(String hostName, int port, Set<FileReference> fileReferences) {
        if (Flags.CONFIG_SERVER_TRIGGER_DOWNLOAD_WITH_SOURCE.bindTo(flagSource).value())
            triggerDownloadIncludeHost(hostName, port, fileReferences);
        else {
            Target target = supervisor.connect(new Spec(hostName, port));
            Request request = new Request("filedistribution.setFileReferencesToDownload");
            request.setContext(target);
            request.parameters()
                   .add(new StringArray(fileReferences.stream()
                                                      .map(FileReference::value)
                                                      .toArray(String[]::new)));
            log.log(Level.FINE, () -> "Executing " + request.methodName() + " against " + target + ": " + fileReferences);
            target.invokeAsync(request, rpcTimeout, this);
        }
    }

    private void triggerDownloadIncludeHost(String hostName, int port, Set<FileReference> fileReferences) {
        Target target = supervisor.connect(new Spec(hostName, port));
        Request request = new Request("filedistribution.triggerDownload");
        request.setContext(target);
        request.parameters().add(new StringArray(fileReferences.stream().map(FileReference::value).toArray(String[]::new)));
        request.parameters().add(new StringValue(new Spec(com.yahoo.net.HostName.getLocalhost(), 19070).toString()));
        log.log(Level.FINE, () -> "Executing " + request.methodName() + " against " + target + ": " + fileReferences);
        target.invokeAsync(request, rpcTimeout, this);
    }

    @Override
    public void handleRequestDone(Request req) {
        Target target = (Target) req.getContext();
        if (req.isError()) {
            log.log(Level.FINE, () -> req.methodName() + " failed for " + target + ": " + req.errorCode() + " (" + req.errorMessage() + ")");
        }
        if (target != null) target.close();
    }

}
