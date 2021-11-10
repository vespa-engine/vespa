// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * <p>Provides classes and interfaces for implementing a {@link com.yahoo.jdisc.handler.RequestHandler
 * RequestHandler}.</p>
 *
 * <h2>RequestHandler</h2>
 * <p>All {@link com.yahoo.jdisc.Request Requests} in a jDISC application are processed by RequestHandlers. These are
 * components created by the {@link com.yahoo.jdisc.application.Application Application}, and bound to one or more URI
 * patterns through the {@link com.yahoo.jdisc.application.ContainerBuilder ContainerBuilder} API. Upon receiving a
 * Request, a RequestHandler must return a {@link com.yahoo.jdisc.handler.ContentChannel ContentChannel} into which the
 * caller can asynchronously write the Request's payload. The ContentChannel is an asynchronous API for ByteBuffer
 * hand-over, with support for asynchronous completion-notification (through the {@link
 * com.yahoo.jdisc.handler.CompletionHandler CompletionHandler} interface). Once the Request has been processed (which
 * may or may not involve dispatching one or more child-Requests), the RequestHandler must prepare a {@link
 * com.yahoo.jdisc.Response Response} object and asynchronously pass that to the corresponding {@link
 * com.yahoo.jdisc.handler.ResponseHandler ResponseHandler}. One of the most vital parts of the RequestHandler definition
 * is that it must provide exactly one Response for every Request. This guarantee simplifies the usage pattern of
 * RequestHandlers, and allows other components to skip a lot of bookkeeping. If a RequestHandler decides to create and
 * dispatch a child-Request, it is done through the same {@link com.yahoo.jdisc.application.BindingSet BindingSet}
 * mechanics that was used to resolve the current RequestHandler. Because all {@link
 * com.yahoo.jdisc.service.ServerProvider ServerProviders} use "localhost" for Request URI hostname, most RequestHandlers
 * are also bound to "localhost". Those that are not typically provide a specific service for one or more remote hosts
 * (these are {@link com.yahoo.jdisc.service.ClientProvider ClientProviders}).</p>
 *
<pre>
&#64;Inject
MyApplication(ContainerActivator activator, CurrentContainer container) {
    ContainerBuilder builder = activator.newContainerBuilder();
    builder.serverBindings().bind("http://localhost/*", new MyRequestHandler());
    activator.activateContainer(builder);
}
</pre>
 *
 * <p>Because the entirety of the RequestHandler stack (RequestHandler, ResponseHandler, ContentChannel and
 * CompletionHandler) is asynchronous, an active {@link com.yahoo.jdisc.Container Container} can handle as many
 * concurrent Requests as the sum capacity of all installed ServerProviders. Furthermore, the APIs have been designed in
 * such a way that the ContentChannel returned back to the initial call to a RequestHandler can be the very same
 * ContentChannel as is returned by the final destination of a Request. This means that, unless explicitly implemented
 * otherwise, a jDISC application that is intended to forward large streams of data can do so without having to make any
 * copies of that data as it is passing through.</p>
 *
 * <h2>ResponseHandler</h2>
 * <p>The complement of the Request is the Response. A Response is a numeric status code and a set of header fields.
 * Just as Requests are processed by RequestHandlers, Responses are processed by ResponseHandlers. The ResponseHandler
 * interface is fully asynchronous, and uses the ContentChannel class to encapsulate the asynchronous passing of
 * Response content. Where the RequestHandler is part of the Container and it's BindingSets, the ResponseHandler is part
 * of the Request context. With every call to a RequestHandler you must also provide a ResponseHandler. Because the
 * Request itself is not part of the ResponseHandler API, there is no built-in feature to tell a ResponseHandler which
 * Request the Response corresponds to. Instead, one should create per-Request light-weight ResponseHandler objects that
 * encapsulate the necessary context for Response processing. This was a deliberate design choice based on observed
 * usage patterns of a different but similar architecture (the messaging layer of the Vespa platform).</p>
 *
 * <p>A Request may or may not have an assigned timeout. Both a ServerProvider and a RequestHandler may choose to assign
 * a timeout to a Request, but only the first to assign it has an effect. The timeout is the maximum allowed time for a
 * RequestHandler to wait before calling the ResponseHandler. There is no monitoring of the associated ContentChannels
 * of either Request or Response, so once a Response has been dispatched a ContentChannel can stay open indefinetly.
 * Timeouts are managed by a jDISC core component, but a RequestHandler may ask a Request at any time whether or not it
 * has timed out. This allows RequestHandlers to terminate CPU-intensive processing of Requests whose Response will be
 * discarded anyway. Once timeout occurs, the timeout manager calls the appropriate {@link
 * com.yahoo.jdisc.handler.RequestHandler#handleTimeout(Request, ResponseHandler)} method. All future calls to that
 * ResponseHandler is blocked, as to uphold the guarantee that a Request should have exactly one Response.</p>
 *
 * @see com.yahoo.jdisc
 * @see com.yahoo.jdisc.application
 * @see com.yahoo.jdisc.service
 */
@com.yahoo.api.annotations.PublicApi
package com.yahoo.jdisc.handler;
