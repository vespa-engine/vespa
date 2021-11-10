// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * <p>Provides classes and interfaces for implementing a {@link com.yahoo.jdisc.service.ClientProvider ClientProvider} or
 * a {@link com.yahoo.jdisc.service.ServerProvider ServerProvider}.</p>
 *
 * <h2>ServerProvider</h2>
 * <p>All {@link com.yahoo.jdisc.Request Requests} that are processed in a jDISC application are created by
 * ServerProviders. These are components created by the {@link com.yahoo.jdisc.application.Application Application}, and
 * they are the parts of jDISC that accept incoming connections. The ServerProvider creates and dispatches Request
 * instances to the {@link com.yahoo.jdisc.service.CurrentContainer CurrentContainer}. No Request is ever dispatched to a
 * ServerProvider, so a ServerProvider is considered part of the Application and not part of a Container (as opposed to
 * {@link com.yahoo.jdisc.handler.RequestHandler RequestHandlers} and ClientProviders). To create a Request the
 * ServerProvider first composes a URI on the form <code>&lt;scheme&gt;://localhost[:&lt;port&gt;]/&lt;path&gt;</code>
 * that matches the content of the accepted connection, and passes that URI to the CurrentContainer interface. This
 * creates a com.yahoo.jdisc.core.ContainerSnapshot that holds a reference to the {@link
 * com.yahoo.jdisc.Container Container} that is currently active, and resolves the appropriate {@link
 * com.yahoo.jdisc.application.BindingSet BindingSet} for the given URI through the Application's {@link
 * com.yahoo.jdisc.application.BindingSetSelector BindingSetSelector}. This snapshot becomes the context of the new
 * Request to ensure that all further processing of that Request happens within the same Container instace. Finally, the
 * appropriate RequestHandler is resolved by the selected BindingSet, and the Request is dispatched.</p>
 *
<pre>
private final ServerProvider server;

&#64;Inject
MyApplication(CurrentContainer container) {
    server = new MyServerProvider(container);
    server.start();
}
</pre>
 *
 * <h3>ClientProvider</h3>
 * <p>A ClientProvider extends the RequestHandler interface, adding a method for initiating the startup of the provider.
 * This is to allow an Application to develop a common ClientProvider install path. As opposed to RequestHandlers that
 * are bound to URIs with the "localhost" hostname that the ServerProviders use when creating a Request, a
 * ClientProvider is typically bound using a hostname wildcard (the '*' character). Because BindingSet considers a
 * wildcard match to be weaker than a verbatim match, only Requests with URIs that are not bound to a local
 * RequestHandler are passed to the ClientProvider.</p>
 *
<pre>
private final ClientProvider client;

&#64;Inject
MyApplication(ContainerActivator activator, CurrentContainer container) {
    client = new MyClientProvider();
    client.start();

    ContainerBuilder builder = activator.newContainerBuilder();
    builder.serverBindings().bind("http://localhost/*", new MyRequestHandler());
    builder.clientBindings().bind("http://&#42;/*", client);
    activator.activateContainer(builder);
}
</pre>
 *
 * <p>Because the dispatch to a ClientProvider uses the same mechanics as the dispatch to an ordinary RequestHandler
 * (i.e. the BindingSet), it is possible to create a test-mode BindingSet and a test-aware BindingSetSelector which
 * dispatches to mock-up RequestHandlers instead of remote servers. The immediate benefit of this is that regression
 * tests can be run on an Application otherwise configured for production traffic, allowing you to stress actual
 * production code instead of targeted-only unit tests. This is how you would install a custom BindingSetSelector:</p>
 *
<pre>
&#64;Inject
MyApplication(ContainerActivator activator, CurrentContainer container) {
    ContainerBuilder builder = activator.newContainerBuilder();
    builder.clientBindings().bind("http://bing.com/*", new BingClientProvider());
    builder.clientBindings("test").bind("http://bing.com/*", new BingMockupProvider());
    builder.guiceModules().install(new MyBindingSetSelector());
    activator.activateContainer(builder);
}
</pre>
 *
 * @see com.yahoo.jdisc
 * @see com.yahoo.jdisc.application
 * @see com.yahoo.jdisc.handler
 */
@com.yahoo.api.annotations.PublicApi
package com.yahoo.jdisc.service;
