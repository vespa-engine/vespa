// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * <p>Provides classes and interfaces for implementing an {@link com.yahoo.jdisc.application.Application
 * Application}.</p>
 *
 * <h2>Application</h2>
 *
 * <p>In every jDISC process there is exactly one Application instance, it is created during jDISC startup, and it is
 * destroyed during jDISC shutdown. The Application uses the {@link com.yahoo.jdisc.application.ContainerBuilder
 * ContainerBuilder} interface to load OSGi {@link org.osgi.framework.Bundle Bundles}, install Guice {@link
 * com.google.inject.Module Modules}, create and start {@link com.yahoo.jdisc.service.ServerProvider ServerProviders},
 * inject a {@link com.yahoo.jdisc.application.BindingSetSelector BindingSetSelector}, and configure {@link
 * com.yahoo.jdisc.application.BindingRepository BindingSets} with {@link com.yahoo.jdisc.handler.RequestHandler
 * RequestHandlers} and {@link com.yahoo.jdisc.service.ClientProvider ClientProviders}. Once the ContainerBuilder is
 * appropriately configured, it is passed to the local {@link com.yahoo.jdisc.application.ContainerActivator} to perform
 * an atomic switch from current to new {@link com.yahoo.jdisc.Container Container}.</p>
 *
<pre>
&#64;Inject
MyApplication(ContainerActivator activator) {
    ContainerBuilder builder = activator.newContainerBuilder();
    builder.guiceModules().install(new MyBindings());
    Bundle bundle = builder.osgiBundles().install("file:$VESPA_HOME/lib/jars/jdisc_http.jar");
    builder.serverProviders().install(bundle, "com.yahoo.disc.service.http.HttpServer");
    builder.serverBindings().bind("http://localhost/admin/*", new MyAdminHandler());
    builder.serverBindings().bind("http://localhost/*", new MyRequestHandler());
    activator.activateContainer(builder);
}
</pre>
 *
 * <p>Because the {@link com.yahoo.jdisc.Request Request} owns a reference to the Container that was active on Request-
 * construction, jDISC is able to guarantee that no component is shut down as long as there are pending Requests that
 * can reach them. When activating a new Container, the previous Container is returned as a {@link
 * com.yahoo.jdisc.application.DeactivatedContainer DeactivatedContainer} instance - an API that can be used by the
 * Application to asynchronously wait for Container termination in order to completely shut down components that are no
 * longer required. This activation pattern is used both for Application startup, runtime reconfigurations, as well as
 * for Application shutdown. It allows all jDISC Application to continously serve Requests during reconfiguration,
 * causing no down time other than what the Application itself explicitly enforces.</p>
 *
<pre>
void reconfigureApplication() {
   (...)
   reconfiguredContainerBuilder.handlers().install(myRetainedClients);
   reconfiguredContainerBuilder.servers().install(myRetainedServers);
   myExpiredServers.close();
   DeactivatedContainer deactivatedContainer = containerActivator.activateContainer(reconfiguredContainerBuilder);
   deactivatedContainer.notifyTermination(new Runnable() {
       void run() {
           myExpiredClients.destroy();
           myExpiredServers.destroy();
       }
   });
}
</pre>
 *
 * <h2>Application and OSGi</h2>
 * <p>At the heart of jDISC is an OSGi framework. An Application is always packaged as an OSGi bundle. The OSGi
 * technology itself is a set of specifications that define a dynamic component system for Java. These specifications
 * enable a development model where applications are (dynamically) composed of many different (reusable) components. The
 * OSGi specifications enable components to hide their implementations from other components while communicating through
 * common interfaces (in our case, defined by jDISC's core API) or services (which are objects that are explicitly
 * shared between components). Initially this framework is used to load and bootstrap the application from an OSGi
 * bundle specified on deployment, but because it is exposed through the ContainerBuilder interface, an Application
 * itself can load other bundles as required.</p>
 *
 * <p>The OSGi integration in jDISC adds the following manifest instructions:</p>
 * <dl>
 *   <dt>X-JDisc-Privileged-Activator</dt>
 *   <dd>
 *     if "true", this tells jDISC that this bundle requires root privileges for its {@link
 *     org.osgi.framework.BundleActivator BundleActivator}. If privileges can not be provided, this bundle should not be
 *     installed. Only the Application bundle and its dependencies can ever be given privileges, as jDISC itself drops
 *     its privileges after the bootstrapping step.
 *   </dd>
 *   <dt>X-JDisc-Preinstall-Bundle</dt>
 *   <dd>
 *     a comma-separated list of bundle locations that must be installed prior to this. Because the named bundles are
 *     loaded through the same framework, all transitive dependencies are also resolved. This is an extension to the
 *     standard OSGi instruction "Require-Bundle" which simply states that this bundle requires another.
 *
 *     It is fairly tricky to get this right during integration testing, since dependencies might be part of the build
 *     tree instead of being installed on the host. To facilitate this, JDisc will prefix any non-schemed location (e.g.
 *     "my_dependency.jar") with the system property "jdisc.bundle.path". This property defaults to the current
 *     directory when running inside an IDE, but is set to "$VESPA_HOME/lib/jars/" by the jdisc startup scripts.
 *
 *     One may also reference system properties in a bundle location using the syntax "${propertyName}". If the property
 *     is not found, it defaults to an empty string.
 *   </dd>
 *   <dt>X-JDisc-Application</dt>
 *   <dd>
 *     the name of the Application class to load from the bundle. This instruction is ignored unless it is part of the
 *     first loaded bundle.
 *   </dd>
 * </dl>
 *
 * <p>One of the benefits of using OSGi is that it provides Classloader isolation, meaning that one bundle can not
 * inadvertently affect the inernals of another. jDISC leverages this to isolate the different implementations of
 * RequestHandlers, ServerProviders, and jDISC's core internals.</p>
 *
 * <p>The OSGi manifest instruction "X-JDisc-Application" tells jDISC the name of the Application class to inject from
 * the loaded bundle during startup. To this end, it is necessary for the named Application to offer an
 * injection-enabled constructor (annotated with the <code>Inject</code> keyword). At a minimum, an Application
 * typically needs to have the ContainerActivator injected and saved to a member variable. Because of jDISC's additional
 * OSGi manifest instruction "X-JDisc-Preinstall-Bundle", an Application bundle can be built with compile-time
 * dependencies on other OSGi bundles (using the "provided" scope in maven) without having to repack those dependency
 * into the application itself. Unless incompatible API changes are made to 3rd party jDISC components, it should be
 * possible to upgrade dependencies without having to recompile and redeploy the Application.</p>
 *
 * <h2>Application deployment</h2>
 * <p>jDISC allows a single binary to execute any application without having to change the command line parameters.
 * Instead of
 * modifying the parameters of the single application binary, changing the application is achieved by setting a single
 * environment variable. The planned method of deployment is therefore to 1) install the application's OSGi bundle,
 * 2) set the necessary "jdisc.application" environment variable, and 3) restart the package.</p>
 *
<pre>
$ install myapp_jar
$ set jdisc.application="myapp.jar"
$ restart jdisc
</pre>
 *
 * <p>It is the responsibility of the Application itself to create, configure
 * and activate a Container instance. Although jDISC offers an API that allows for- and manages the change of an active
 * Container instance, making the necessary calls to do so is also considered Application logic. When jDISC receives an
 * external signal to shut down, it instructs the running Application to initiate a graceful shutdown, and waits for it
 * to terminate. Any in-flight Requests should complete, and all services will close.</p>
 *
 * <p>Because jDISC runs as a Daemon it has the opportunity to run code with root privileges, and it can be configured
 * to provide these privileges to an application's initialization code. However, 1) deployment-time configuration must
 * explicitly enable this capability (by setting the environment variable "jdisc.privileged" to "true"), and 2) the
 * application bundle must explicitly declare that it requires privileges (by including the manifest header
 * "X-JDisc-Privileged-Activator" with the value "true"). If privileges are required but unavailable, deployment of the
 * application will fail. Code that requires privileges will never be run WITHOUT privileges, and code that does not
 * explicitly request privileges will never be run WITH privileges. Finally, the code snippet that is run with
 * privileges is separate from the Application class to avoid unintentionally passing privileges to third-party
 * code.</p>
 *
 * @see com.yahoo.jdisc
 * @see com.yahoo.jdisc.handler
 * @see com.yahoo.jdisc.service
 */
package com.yahoo.jdisc.application;
