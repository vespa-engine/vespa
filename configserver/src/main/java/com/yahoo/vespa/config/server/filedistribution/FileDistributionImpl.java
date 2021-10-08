// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;

import java.io.File;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author baldersheim
 */
public class FileDistributionImpl implements FileDistribution, RequestWaiter {

    private final static Logger log = Logger.getLogger(FileDistributionImpl.class.getName());
    private final static double rpcTimeout = 1.0;

    private final Supervisor supervisor;
    private final File fileReferencesDir;

    public FileDistributionImpl(File fileReferencesDir, Supervisor supervisor) {
        this.fileReferencesDir = fileReferencesDir;
        this.supervisor = supervisor;
    }

    @Override
    public void startDownload(String hostName, int port, Set<FileReference> fileReferences) {
         startDownloadingFileReferences(hostName, port, fileReferences);
    }

    @Override
    public File getFileReferencesDir() {
        return fileReferencesDir;
    }

    // Notifies config proxy which file references it should start downloading. It's OK if the call does not succeed,
    // as downloading will then start synchronously when a service requests a file reference instead
    private void startDownloadingFileReferences(String hostName, int port, Set<FileReference> fileReferences) {
        Target target = supervisor.connect(new Spec(hostName, port));
        Request request = new Request("filedistribution.setFileReferencesToDownload");
        request.setContext(target);
        request.parameters().add(new StringArray(fileReferences.stream().map(FileReference::value).toArray(String[]::new)));
        log.log(Level.FINE, () -> "Executing " + request.methodName() + " against " + target);
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
