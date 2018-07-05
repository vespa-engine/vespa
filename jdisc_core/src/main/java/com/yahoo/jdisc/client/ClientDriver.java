// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.client;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.core.ApplicationLoader;
import com.yahoo.jdisc.core.FelixFramework;
import com.yahoo.jdisc.core.FelixParams;
import com.yahoo.jdisc.test.NonWorkingOsgiFramework;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>This class provides a unified way to set up and run a {@link ClientApplication}. It provides you with a
 * programmable interface to instantiate and run the whole jDISC framework as if it was started as a Daemon, and it
 * provides you with a thread in which to run your application logic. Once your return from the {@link
 * ClientApplication#run()} method, the ClientProvider will initiate {@link Application} shutdown.</p>
 *
 * <p>A ClientApplication is typically a self-contained JAR file that bundles all of its dependencies, and contains a
 * single "main" method. The typical implementation of that method is:</p>
 * <pre>
 * public static void main(String[] args) throws Exception {
 *     ClientDriver.runApplication(MyApplication.class);
 * }
 * </pre>
 *
 * <p>Alternatively, the ClientApplication can be created up front:</p>
 * <pre>
 * public static void main(String[] args) throws Exception {
 *     MyApplication app = new MyApplication();
 *     (... configure app ...)
 *     ClientDriver.runApplication(app);
 * }
 * </pre>
 *
 * <p>Because all of the dependencies of a ClientApplication is expected to be part of the application JAR, the OSGi
 * framework created by this ClientDriver is disabled. Calling any method on that framework will throw an
 * exception. If you need OSGi support, use either of the runApplicationWithOsgi() methods.</p>
 *
 * @author Simon Thoresen Hult
 */
public abstract class ClientDriver {

    /**
     * <p>Creates and runs the given {@link ClientApplication}.</p>
     *
     * @param app          The ClientApplication to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @throws Exception If an exception was thrown by the ClientApplication.
     */
    public static void runApplication(ClientApplication app, Module... guiceModules)
            throws Exception
    {
        runApplication(newNonWorkingOsgiFramework(), newModuleList(app, guiceModules));
    }

    /**
     * <p>Creates and runs an instance of the given {@link ClientApplication} class.</p>
     *
     * @param appClass     The ClientApplication class to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @throws Exception If an exception was thrown by the ClientApplication.
     */
    public static void runApplication(Class<? extends ClientApplication> appClass, Module... guiceModules)
            throws Exception
    {
        runApplication(newNonWorkingOsgiFramework(), newModuleList(appClass, guiceModules));
    }

    /**
     * <p>Creates and runs an instance of the the given {@link ClientApplication} class with OSGi support.</p>
     *
     * @param cachePath    The path to use for the OSGi bundle cache.
     * @param appClass     The ClientApplication class to inject.
     * @param guiceModules The Guice {@link Module Modules} to install prior to startup.
     * @throws Exception If an exception was thrown by the ClientApplication.
     */
    public static void runApplicationWithOsgi(String cachePath, Class<? extends ClientApplication> appClass,
                                              Module... guiceModules) throws Exception
    {
        runApplication(newOsgiFramework(cachePath), newModuleList(appClass, guiceModules));
    }

    private static OsgiFramework newNonWorkingOsgiFramework() {
        return new NonWorkingOsgiFramework();
    }

    private static FelixFramework newOsgiFramework(String cachePath) {
        return new FelixFramework(new FelixParams().setCachePath(cachePath));
    }

    private static List<Module> newModuleList(final ClientApplication appInstance, Module... guiceModules) {
        List<Module> lst = new LinkedList<>(Arrays.asList(guiceModules));
        lst.add(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Application.class).toInstance(appInstance);
            }
        });
        return lst;
    }

    private static List<Module> newModuleList(final Class<? extends ClientApplication> appClass,
                                              Module... guiceModules)
    {
        List<Module> lst = new LinkedList<>(Arrays.asList(guiceModules));
        lst.add(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Application.class).to(appClass);
            }
        });
        return lst;
    }

    private static void runApplication(OsgiFramework osgi, List<Module> modules) throws Exception {
        ApplicationLoader loader = new ApplicationLoader(osgi, modules);
        loader.init(null, false);
        try {
            loader.start();
            ((ClientApplication)loader.application()).run();
            loader.stop();
        } finally {
            loader.destroy();
        }
    }
}
