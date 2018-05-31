// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.google.common.annotations.Beta;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.application.container.impl.ClassLoaderOsgiFramework;
import com.yahoo.application.container.impl.StandaloneContainerRunner;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.container.Container;
import com.yahoo.container.standalone.StandaloneContainerApplication;
import com.yahoo.docproc.jdisc.DocumentProcessingHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.processing.handler.ProcessingHandler;
import com.yahoo.search.handler.SearchHandler;

import java.nio.file.Path;

/**
 * A JDisc Container configured from XML.
 *
 * @author Tony Vaagenes
 * @author Einar M R Rosenvinge
 * @author gjoranv
 */
@Beta
public final class JDisc implements AutoCloseable {

    private final ClassLoaderOsgiFramework osgiFramework = new ClassLoaderOsgiFramework();

    private final TestDriver testDriver;
    private final StandaloneContainerApplication application;

    private final Container container = Container.get();  // TODO: This is indeed temporary ... *3 years later* Indeed.

    private final Path path;
    private final boolean deletePathWhenClosing;

    private JDisc(Path path, boolean deletePathWhenClosing, Networking networking, ConfigModelRepo configModelRepo) {
        this.path = path;
        this.deletePathWhenClosing = deletePathWhenClosing;
        testDriver = TestDriver.newInstance(osgiFramework, "", false, //StandaloneContainerApplication.class,
                                            bindings(path, configModelRepo, networking));

        application = (StandaloneContainerApplication) testDriver.application();
    }

    private Module bindings(final Path path, final ConfigModelRepo configModelRepo, final Networking networking) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Path.class).annotatedWith(StandaloneContainerApplication.APPLICATION_PATH_NAME).toInstance(path);
                bind(ConfigModelRepo.class).annotatedWith(StandaloneContainerApplication.CONFIG_MODEL_REPO_NAME).toInstance(configModelRepo);
                bind(Boolean.class).annotatedWith( // below is an ugly hack to access fields from a scala object.
                        Names.named(StandaloneContainerApplication.DISABLE_NETWORKING_ANNOTATION)).toInstance(
                        networking == Networking.disable);
            }
        };
    }

    /**
     * Factory method to create a JDisc from an XML String. Note that any components that are referenced in
     * the XML must be present on the classpath. To deploy OSGi bundles in memory,
     * use {@link #fromPath(java.nio.file.Path, com.yahoo.application.Networking)}.
     *
     * @param xml the XML configuration to use
     * @return a new JDisc instance
     */
    public static JDisc fromServicesXml(String xml, Networking networking) {
        Path applicationDir = StandaloneContainerRunner.createApplicationPackage(xml);
        return new JDisc(applicationDir, true, networking, new ConfigModelRepo());
    }

    /**
     * Factory method to create a JDisc from an application package.
     * This method allows deploying OSGi bundles(contained in the components subdirectory).
     * All the OSGi bundles will share the same class loader.
     *
     *
     *
     * @param path the reference to the application package to use
     * @param networking enabled or disabled
     * @return a new JDisc instance
     */
    public static JDisc fromPath(Path path, Networking networking) {
        return new JDisc(path, false, networking, new ConfigModelRepo());
    }

    /**
     * Create a jDisc instance which is given a config model repo (in which (mock) content clusters
     * can be looked up).
     */
    public static JDisc fromPath(Path path, Networking networking, ConfigModelRepo configModelRepo) {
        return new JDisc(path, false, networking, configModelRepo);
    }

    /**
     * Returns a {@link Search}, used to perform search query operations on this container.
     *
     * @return a Search instance
     * @throws UnsupportedOperationException if this JDisc does not have search configured
     */
    public Search search() {
        SearchHandler searchHandler = getSearchHandler();
        if (searchHandler == null)
            throw new UnsupportedOperationException("This JDisc does not have 'search' " + "configured.");
        return new Search(searchHandler);
    }

    private SearchHandler getSearchHandler() {
        for (RequestHandler h : container.getRequestHandlerRegistry().allComponents()) {
            if (h instanceof SearchHandler) {
                return (SearchHandler) h;
            }
        }
        return null;
    }

    /**
     * Returns a {@link Processing}, used to do generic asynchronous operations in a request/response API.
     *
     * @return a Processing instance
     * @throws UnsupportedOperationException if this JDisc does not have processing configured
     */
    public Processing processing() {
        ProcessingHandler processingHandler = (ProcessingHandler) container
                .getRequestHandlerRegistry()
                .getComponent(ProcessingHandler.class.getName());

        if (processingHandler == null) {
            throw new UnsupportedOperationException("This JDisc does not have 'processing' " +
                    "configured.");
        }

        return new Processing(processingHandler);
    }

    /**
     * Returns a {@link DocumentProcessing}, used to process objects of type {@link com.yahoo.document.Document},
     * {@link com.yahoo.document.DocumentRemove} and {@link com.yahoo.document.DocumentUpdate}.
     *
     * @return a DocumentProcessing instance
     * @throws UnsupportedOperationException if this JDisc does not have document processing configured
     */
    public DocumentProcessing documentProcessing() {
        DocumentProcessingHandler docprocHandler = (DocumentProcessingHandler) container
                .getRequestHandlerRegistry()
                .getComponent(DocumentProcessingHandler.class.getName());

        if (docprocHandler == null) {
            throw new UnsupportedOperationException("This JDisc does not have 'document-processing' " +
                    "configured.");
        }
        return new DocumentProcessing(docprocHandler);
    }

    /**
     * Returns a registry of all components available in this
     */
    public ComponentRegistry<AbstractComponent> components() {
        return container.getComponentRegistry();
    }

    /**
     * Handles the given {@link com.yahoo.application.container.handler.Request} by passing it to the {@link RequestHandler}
     * that is bound to the request's URI.
     *
     * @param request the request to process
     * @return a response for the given request
     */
    public Response handleRequest(Request request) {
        SynchronousRequestResponseHandler handler = new SynchronousRequestResponseHandler();
        return handler.handleRequest(request, testDriver);
    }

    /**
     * Closes the current JDisc.
     */
    @Override
    public void close() {
        try {
            testDriver.close();
        } finally {
            Container.resetInstance();

            if (deletePathWhenClosing)
                IOUtils.recursiveDeleteDir(path.toFile());
        }
    }

}
