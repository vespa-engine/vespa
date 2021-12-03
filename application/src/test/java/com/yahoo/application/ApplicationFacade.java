// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.api.annotations.Beta;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.component.Component;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.Container;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.DocumentOperation;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

/**
 * Convenience access methods into some application content.
 * These are not generally applicable and are therefore in test.
 * This can also auto close its application.
 *
 * @author bratseth
 */
public class ApplicationFacade implements AutoCloseable {

    private static final String DEFAULT_CHAIN = "default";

    private final Application application;

    public ApplicationFacade(Application application) {
        this.application = application;
    }

    /** Returns the application wrapped by this */
    public Application application() { return application; }

    /**
     * Process a single document through the default chain.
     *
     * @param document the document to process
     * @return the docproc return status
     */
    @Beta
    public DocumentProcessor.Progress process(final DocumentOperation document) {
        return process(DEFAULT_CHAIN, document);
    }

    /**
     * Process a single document through the default chain.
     *
     * @param chain process using this chain
     * @param op the document operation to process
     * @return the docproc return status
     */
    @Beta
    public DocumentProcessor.Progress process(String chain, final DocumentOperation op) {
        return application.getJDisc("default").documentProcessing().process(ComponentSpecification.fromString(chain),
                                                                            Processing.of(op));
    }

    /**
     * Pass a processing object through the
     *
     * @param processing the processing object to process
     * @return the docproc return status
     */
    @Beta
    public DocumentProcessor.Progress process(final Processing processing) {
        return process(DEFAULT_CHAIN, processing);
    }

    /**
     * Pass a processing object through the
     *
     * @param chain      process using this chain
     * @param processing the processing object to process
     * @return the docproc return status
     */
    @Beta
    public DocumentProcessor.Progress process(String chain, final Processing processing) {
        return application.getJDisc("default").documentProcessing().process(ComponentSpecification.fromString(chain), processing);
    }

    /**
     * Pass query object to the default search chain
     *
     * @param query the query
     * @return the search result
     */
    @Beta
    public Result search(final Query query) {
        return application.getJDisc("default").search().process(ComponentSpecification.fromString(DEFAULT_CHAIN), query);
    }

    /**
     * Pass query object to the default search chain
     *
     * @param chain the search chain to use
     * @param query the query
     * @return the search result
     */
    @Beta
    public Result search(String chain, final Query query) {
        return application.getJDisc("default").search().process(ComponentSpecification.fromString(chain), query);
    }

    /**
     * Pass query object to the default search chain
     *
     * @param request the request to process
     * @return the response object
     */
    @Beta
    public Response handleRequest(Request request) {
        return application.getJDisc("default").handleRequest(request);
    }

    /**
     * @param componentId name of the component (usually YourClass.class.getName())
     * @return the component object, or null if not found
     */
    @Beta
    public Component getComponentById(String componentId) {
        return Container.get().getComponentRegistry().getComponent(new ComponentId(componentId));
    }

    /**
     * @param componentId name of the component (usually YourClass.class.getName())
     * @return the request handler object, or null if not found
     */
    @Beta
    public RequestHandler getRequestHandlerById(String componentId) {
        return Container.get().getRequestHandlerRegistry().getComponent(new ComponentId(componentId));
    }

    /**
     * @param componentId name of the component (usually YourClass.class.getName())
     * @return the client object, or null if not found
     */
    @Beta
    public ClientProvider getClientById(String componentId) {
        return Container.get().getClientProviderRegistry().getComponent(new ComponentId(componentId));
    }

    /**
     * @param componentId name of the component (usually YourClass.class.getName())
     * @return the client object, or null if not found
     */
    @Beta
    public ServerProvider getServerById(String componentId) {
        return Container.get().getServerProviderRegistry().getComponent(new ComponentId(componentId));
    }

    @Override
    public void close() {
        application.close();
    }

}
