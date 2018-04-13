// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * <p>Provides the common classes and interfaces of the jDISC core.</p>
 *
 * <p>jDISC is a single-process, multi-threaded application container that consists of exactly one {@link
 * com.yahoo.jdisc.application.Application Application} with an optional {@link com.yahoo.jdisc.Metric Metric}
 * configuration, one or more {@link com.yahoo.jdisc.handler.RequestHandler RequestHandlers}, one or more {@link
 * com.yahoo.jdisc.service.ServerProvider ServerProviders}, and one or more named {@link
 * com.yahoo.jdisc.application.BindingSet BindingSets}. When starting an Application, and whenever else the current
 * configuration changes, it is the responsibility of the Application to create and activate a new {@link
 * com.yahoo.jdisc.Container Container} that matches the most recent configuration. The Container itself is an immutable
 * object, ensuring that the context of a {@link com.yahoo.jdisc.Request Request} never changes during its execution.
 * When a new Container is activated, the previous is deactivated and scheduled for shutdown as soon as it finishes
 * processing all previously accepted Requests. At any time, a jDISC process will therefore have zero (typically during
 * application startup and shutdown) or one active Container, and zero or more deactivated Containers. The currently
 * active Container is available to ServerProviders through an application-scoped singleton, making sure that no new
 * Request is ever passed to a deactivated Container.</p>
 *
 * <p>A Request is created when either a) a ServerProvider accepts an incoming connection, or b) a RequestHandler
 * creates a child Request of another. In the case of the ServerProvider, the {@link
 * com.yahoo.jdisc.service.CurrentContainer CurrentContainer} interface provides a reference to the currently active
 * Container, and the Application's {@link com.yahoo.jdisc.application.BindingSetSelector BindingSetSelector} (provided
 * during configuration) selects a BindingSet based on the Request's URI. The BindingSet is what the Container uses to
 * match a Request's URI to an appropriate RequestHandler. Together, the Container reference and the selected BindingSet
 * make up the context of the Request. When a RequestHandler chooses to create a child Request, it reuses both the
 * Container reference and the BindingSet of the original Request, ensuring that all processing of a single connection
 * happens within the same Container instance. For every dispatched Request there is always exactly one {@link
 * com.yahoo.jdisc.Response Response}. The Response is never routed, it simply follows the call stack of the
 * corresponding Request.</p>
 *
 * <p>Because BindingSets decide on the RequestHandler which is to process a Request, using multiple BindingSets and a
 * property-specific BindingSetSelector, one is able to create a Container capable of rewiring itself on a per-Request
 * basis. This can be used for running production code in a mock-up environment for offline regression tests, and also
 * for features such as Request bucketing (selecting a bucket BindingSet for n percent of the URIs) and rate-limiting
 * (selecting a rejecting-type RequestHandler if the system is in some specific state).</p>
 *
 * <p>Finally, the Container provides a minimal Metric API that consists of a {@link com.yahoo.jdisc.Metric Metric}
 * producer and a {@link com.yahoo.jdisc.application.MetricConsumer MetricConsumer}. Any component may choose to inject
 * and use the Metric API, but all its calls are ignored unless the Application has chosen to inject a MetricConsumer
 * provider during configuration. For efficiency reasons, the Container provides the {@link
 * com.yahoo.jdisc.application.ContainerThread ContainerThread} which offers thread local access to the Metric API. This
 * is a class that needs to be explicitly used in whatever Executor or ThreadFactory the Application chooses to inject
 * into the Container.</p>
 *
 * <p>For unit testing purposes, the {@link com.yahoo.jdisc.test} package provides classes and interfaces to help setup
 * and run a jDISC application in a test environment with as little effort as possible.</p>
 *
 * @see com.yahoo.jdisc.application
 * @see com.yahoo.jdisc.handler
 * @see com.yahoo.jdisc.service
 * @see com.yahoo.jdisc.test
 */
// TODO: Vespa 7 remove internal classes (at least Container) out of this PublicApi package.
@com.yahoo.api.annotations.PublicApi
package com.yahoo.jdisc;
