// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.FileAcquirerFactory;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.vespa.config.ConfigTransformer;
import com.yahoo.vespa.config.UrlDownloader;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The container instance. This is a Vespa internal object, external code should
 * only depend on this if there are no other options, and must be prepared to
 * see it change at no warning.
 *
 * @author bratseth
 */
public class Container {

    private volatile boolean usingCustomFileAcquirer = false;

    private volatile ComponentRegistry<RequestHandler> requestHandlerRegistry;
    private volatile ComponentRegistry<ClientProvider> clientProviderRegistry;
    private volatile ComponentRegistry<ServerProvider> serverProviderRegistry;
    private volatile ComponentRegistry<AbstractComponent> componentRegistry;
    private volatile FileAcquirer fileAcquirer;
    private volatile UrlDownloader urlDownloader;

    private static final Logger logger = Logger.getLogger(Container.class.getName());

    // TODO: Make this final again.
    private static Container instance = new Container();

    public static Container get() { return instance; }

    public void shutdown() {
        if (fileAcquirer != null)
            fileAcquirer.shutdown();
        if (urlDownloader != null)
            urlDownloader.shutdown();
    }

    //Used to acquire files originating from the application package.
    public FileAcquirer getFileAcquirer() {
        return fileAcquirer;
    }

    /**
     * Hack. For internal use only, will be removed later.
     *
     * Used by Application to be able to repeatedly set up containers.
     */
    public static void resetInstance() {
        instance = new Container();
    }

    public ComponentRegistry<RequestHandler> getRequestHandlerRegistry() {
        return requestHandlerRegistry;
    }

    public void setRequestHandlerRegistry(ComponentRegistry<RequestHandler> requestHandlerRegistry) {
        this.requestHandlerRegistry = requestHandlerRegistry;
    }

    public ComponentRegistry<ClientProvider> getClientProviderRegistry() {
        return clientProviderRegistry;
    }

    public void setClientProviderRegistry(ComponentRegistry<ClientProvider> clientProviderRegistry) {
        this.clientProviderRegistry = clientProviderRegistry;
    }

    public ComponentRegistry<ServerProvider> getServerProviderRegistry() {
        return serverProviderRegistry;
    }

    public void setServerProviderRegistry(ComponentRegistry<ServerProvider> serverProviderRegistry) {
        this.serverProviderRegistry = serverProviderRegistry;
    }

    public ComponentRegistry<AbstractComponent> getComponentRegistry() {
        return componentRegistry;
    }

    public void setComponentRegistry(ComponentRegistry<AbstractComponent> registry) {
        registry.freeze();
        this.componentRegistry = registry;
    }

    // Only intended for use by the Server instance.
    public void setupFileAcquirer(QrConfig.Filedistributor filedistributorConfig) {
        if (usingCustomFileAcquirer)
            return;

        if (filedistributorConfig.configid().isEmpty()) {
            if (fileAcquirer != null)
                logger.warning("Disabling file distribution");
            fileAcquirer = null;
        } else {
            fileAcquirer = FileAcquirerFactory.create(filedistributorConfig.configid());
        }

        setPathAcquirer(fileAcquirer);
    }

    /** Only for internal use. */
    public void setCustomFileAcquirer(FileAcquirer fileAcquirer) {
        if (this.fileAcquirer != null) {
            throw new RuntimeException("Can't change file acquirer. Is " +
                                       this.fileAcquirer + " attempted to set to " + fileAcquirer);
        }
        usingCustomFileAcquirer = true;
        this.fileAcquirer = fileAcquirer;
        setPathAcquirer(fileAcquirer);
    }

    private static void setPathAcquirer(FileAcquirer fileAcquirer) {
        ConfigTransformer.setPathAcquirer(fileReference -> {
            try {
                return fileAcquirer.waitFor(fileReference, 15, TimeUnit.MINUTES).toPath();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }

    public void setupUrlDownloader() {
        this.urlDownloader = new UrlDownloader();
        ConfigTransformer.setUrlDownloader(urlDownloader);
    }

}
