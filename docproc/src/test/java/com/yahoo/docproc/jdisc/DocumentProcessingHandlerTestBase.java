// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.container.jdisc.messagebus.MbusServerProvider;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocprocService;
import com.yahoo.docproc.jdisc.messagebus.MbusRequestContext;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.jdisc.MbusClient;
import com.yahoo.messagebus.jdisc.test.RemoteServer;
import com.yahoo.messagebus.jdisc.test.ServerTestDriver;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.SharedSourceSession;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public abstract class DocumentProcessingHandlerTestBase {

    protected DocumentProcessingHandler handler;
    protected ServerTestDriver driver;
    protected RemoteServer remoteServer;
    protected DocumentTypeManager documentTypeManager = new DocumentTypeManager();
    SessionCache sessionCache;
    private final List<MbusServerProvider> serviceProviders = new ArrayList<>();

    @Before
    public void createHandler() {
        documentTypeManager.register(getType());

        Protocol protocol = new DocumentProtocol(documentTypeManager);

        driver = ServerTestDriver.newInactiveInstanceWithProtocol(protocol, true);

        sessionCache =
                new SessionCache("raw:", driver.client().slobrokId(), "test", "raw:", null, "raw:", documentTypeManager);

        ContainerBuilder builder = driver.parent().newContainerBuilder();
        ComponentRegistry<DocprocService> registry = new ComponentRegistry<>();

        handler = new DocumentProcessingHandler(registry,
                new ComponentRegistry<>(),
                new ComponentRegistry<>(),
                new DocumentProcessingHandlerParameters().
                        setDocumentTypeManager(documentTypeManager).
                        setContainerDocumentConfig(new ContainerDocumentConfig(new ContainerDocumentConfig.Builder())));
        builder.serverBindings().bind("mbus://*/*", handler);

        ReferencedResource<SharedSourceSession> sessionRef = sessionCache.retainSource(new SourceSessionParams());
        MbusClient sourceClient = new MbusClient(sessionRef.getResource());
        builder.clientBindings().bind("mbus://*/source", sourceClient);
        builder.clientBindings().bind("mbus://*/" + MbusRequestContext.internalNoThrottledSource, sourceClient);
        sourceClient.start();

        List<Pair<String, CallStack>> callStacks = getCallStacks();
        List<AbstractResource> resources = new ArrayList<>();
        for (Pair<String, CallStack> callStackPair : callStacks) {
            DocprocService service = new DocprocService(callStackPair.getFirst());
            service.setCallStack(callStackPair.getSecond());
            service.setInService(true);

            ComponentId serviceId = new ComponentId(service.getName());
            registry.register(serviceId, service);

            ComponentId sessionName = ComponentId.fromString("chain." + serviceId);
            MbusServerProvider serviceProvider = new MbusServerProvider(sessionName, sessionCache, driver.parent());
            serviceProvider.get().start();

            serviceProviders.add(serviceProvider);

            MbusClient intermediateClient = new MbusClient(serviceProvider.getSession());
            builder.clientBindings().bind("mbus://*/" + sessionName.stringValue(), intermediateClient);
            intermediateClient.start();
            resources.add(intermediateClient);
        }

        driver.parent().activateContainer(builder);
        sessionRef.getReference().close();
        sourceClient.release();

        for (AbstractResource resource : resources) {
            resource.release();
        }

        remoteServer = RemoteServer.newInstance(driver.client().slobrokId(), "foobar", protocol);
    }

    @After
    public void destroy() {
        for (MbusServerProvider serviceProvider : serviceProviders) {
            serviceProvider.deconstruct();
        }
        driver.close();
        remoteServer.close();
    }

    protected abstract List<Pair<String, CallStack>> getCallStacks();

    protected abstract DocumentType getType();

    public boolean sendMessage(String destinationChainName, DocumentMessage msg) {
        msg.setRoute(Route.parse("test/chain." + destinationChainName + " " + remoteServer.connectionSpec()));
        msg.setPriority(DocumentProtocol.Priority.HIGH_1);
        msg.setLoadType(LoadType.DEFAULT);
        msg.getTrace().setLevel(9);
        msg.setTimeRemaining(60 * 1000);
        return driver.client().sendMessage(msg).isAccepted();
    }
}
