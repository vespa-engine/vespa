// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import java.util.List;

/**
 * <p>This is an abstraction of the OSGi framework that hides the actual implementation details. If you need access to
 * this interface, simply inject it into your Application. In most cases, however, you are better of injecting a
 * {@link BundleInstaller} since that provides common convenience methods.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface OsgiFramework {

    /**
     * <p>Installs a bundle from the specified location. The specified location identifier will be used as the identity
     * of the bundle. If a bundle containing the same location identifier is already installed, the <tt>Bundle</tt>
     * object for that bundle is returned. All bundles listed in the {@link OsgiHeader#PREINSTALL_BUNDLE} manifest
     * header are also installed. The bundle at index 0 of the returned list matches the <tt>bundleLocation</tt>
     * argument.</p>
     *
     * <p><b>NOTE:</b> When this method installs more than one bundle, <em>AND</em> one of those bundles throw an
     * exception during installation, the bundles installed prior to throwing the expcetion will remain installed. To
     * enable the caller to recover from such a situation, this method wraps any thrown exception within a {@link
     * BundleInstallationException} that contains the list of successfully installed bundles.</p>
     *
     * <p>It would be preferable if this method was exception-safe (that it would roll-back all installed bundles in the
     * case of an exception), but that can not be implemented thread-safely since an <tt>Application</tt> may choose to
     * install bundles concurrently through any available <tt>BundleContext</tt>.</p>
     *
     * @param bundleLocation The location identifier of the bundle to install.
     * @return The list of Bundle objects installed, the object at index 0 matches the given location.
     * @throws BundleInstallationException If the input stream cannot be read, or the installation of a bundle failed,
     *                                     or the caller does not have the appropriate permissions, or the system {@link
     *                                     BundleContext} is no longer valid.
     */
    List<Bundle> installBundle(String bundleLocation) throws BundleException;

    /**
     * <p>Starts the given {@link Bundle}s. The parameter <tt>privileged</tt> tells the framework whether or not
     * privileges are available, and is checked against the {@link OsgiHeader#PRIVILEGED_ACTIVATOR} header of each
     * Bundle being started. Any bundle that is a fragment is silently ignored.</p>
     *
     * @param bundles    The bundles to start.
     * @param privileged Whether or not privileges are available.
     * @throws BundleException       If a bundle could not be started. This could be because a code dependency could not
     *                               be resolved or the specified BundleActivator could not be loaded or threw an
     *                               exception.
     * @throws SecurityException     If the caller does not have the appropriate permissions.
     * @throws IllegalStateException If this bundle has been uninstalled or this bundle tries to change its own state.
     */
    void startBundles(List<Bundle> bundles, boolean privileged) throws BundleException;

    /**
     * <p>This method <em>synchronously</em> refreshes all bundles currently loaded. Once this method returns, the
     * class loaders of all bundles will reflect on the current set of loaded bundles.</p>
     */
    void refreshPackages();

    /**
     * <p>Returns the BundleContext of this framework's system bundle. The returned BundleContext can be used by the
     * caller to act on behalf of this bundle. This method may return <tt>null</tt> if it has no valid
     * BundleContext.</p>
     *
     * @return A <tt>BundleContext</tt> for the system bundle, or <tt>null</tt>.
     * @throws SecurityException If the caller does not have the appropriate permissions.
     * @since 2.0
     */
    BundleContext bundleContext();

    /**
     * <p>Returns an iterable collection of all installed bundles. This method returns a list of all bundles installed
     * in the OSGi environment at the time of the call to this method. However, since the OsgiFramework is a very
     * dynamic environment, bundles can be installed or uninstalled at anytime.</p>
     *
     * @return An iterable collection of Bundle objects, one object per installed bundle.
     */
    List<Bundle> bundles();

    /**
     * <p>This method starts the framework instance. Before this method is called, any call to {@link
     * #installBundle(String)} or {@link #bundles()} will generate a {@link NullPointerException}.</p>
     *
     * @throws BundleException If any error occurs.
     */
    void start() throws BundleException;

    /**
     * <p>This method <em>synchronously</em> shuts down the framework. It must be called at the end of a session in
     * order to shutdown all active bundles.</p>
     *
     * @throws BundleException If any error occurs.
     */
    void stop() throws BundleException;

}
