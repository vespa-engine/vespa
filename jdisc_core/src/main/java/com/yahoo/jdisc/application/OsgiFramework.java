// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import java.util.Collection;
import java.util.List;

/**
 * This is an abstraction of the OSGi framework that hides the actual implementation details. If you need access to
 * this interface, simply inject it into your Application. In most cases, however, you are better of injecting a
 * {@link BundleInstaller} since that provides common convenience methods.
 *
 * @author Simon Thoresen Hult
 * @author gjoranv
 */
public interface OsgiFramework {

    /**
     * <p>Installs a bundle from the specified location. The specified location identifier will be used as the identity
     * of the bundle. If a bundle containing the same location identifier is already installed, the <code>Bundle</code>
     * object for that bundle is returned. All bundles listed in the {@link OsgiHeader#PREINSTALL_BUNDLE} manifest
     * header are also installed. The bundle at index 0 of the returned list matches the <code>bundleLocation</code>
     * argument.</p>
     *
     * <p><b>NOTE:</b> When this method installs more than one bundle, <em>AND</em> one of those bundles throw an
     * exception during installation, the bundles installed prior to throwing the exception will remain installed. To
     * enable the caller to recover from such a situation, this method wraps any thrown exception within a {@link
     * BundleInstallationException} that contains the list of successfully installed bundles.</p>
     *
     * <p>It would be preferable if this method was exception-safe (that it would roll-back all installed bundles in the
     * case of an exception), but that can not be implemented thread-safely since an <code>Application</code> may choose to
     * install bundles concurrently through any available <code>BundleContext</code>.</p>
     *
     * @param bundleLocation the location identifier of the bundle to install
     * @return the list of Bundle objects installed, the object at index 0 matches the given location
     * @throws BundleInstallationException if the input stream cannot be read, or the installation of a bundle failed,
     *                                     or the caller does not have the appropriate permissions, or the system {@link
     *                                     BundleContext} is no longer valid
     */
    List<Bundle> installBundle(String bundleLocation) throws BundleException;

    /**
     * Starts the given {@link Bundle}s. The parameter <code>privileged</code> tells the framework whether or not
     * privileges are available, and is checked against the {@link OsgiHeader#PRIVILEGED_ACTIVATOR} header of each
     * Bundle being started. Any bundle that is a fragment is silently ignored.
     *
     * @param bundles    the bundles to start
     * @param privileged whether or not privileges are available
     * @throws BundleException       if a bundle could not be started. This could be because a code dependency could not
     *                               be resolved or the specified BundleActivator could not be loaded or threw an
     *                               exception.
     * @throws SecurityException     if the caller does not have the appropriate permissions
     * @throws IllegalStateException if this bundle has been uninstalled or this bundle tries to change its own state
     */
    void startBundles(List<Bundle> bundles, boolean privileged) throws BundleException;

    /**
     * Synchronously refresh all bundles currently loaded. Once this method returns, the
     * class loaders of all bundles will reflect on the current set of loaded bundles.
     *
     * NOTE: This method is no longer used by the Jdisc container framework, but kept for completeness.
     */
    void refreshPackages();

    /**
     * Returns the BundleContext of this framework's system bundle. The returned BundleContext can be used by the
     * caller to act on behalf of this bundle. This method may return <code>null</code> if it has no valid
     * BundleContext.
     *
     * @return a <code>BundleContext</code> for the system bundle, or <code>null</code>
     * @throws SecurityException if the caller does not have the appropriate permissions
     */
    BundleContext bundleContext();

    /**
     * Returns an iterable collection of all installed bundles. This method returns a list of all bundles installed
     * in the OSGi environment at the time of the call to this method. However, since the OsgiFramework is a very
     * dynamic environment, bundles can be installed or uninstalled at anytime.
     *
     * @return an iterable collection of Bundle objects, one object per installed bundle
     */
    List<Bundle> bundles();

    /**
     * Returns all installed bundles that are visible to the requesting bundle. Bundle visibility
     * is controlled via implementations of {@link org.osgi.framework.hooks.bundle.FindHook};
     */
    List<Bundle> getBundles(Bundle requestingBundle);

    /**
     * Allows this framework to install duplicates of the given collection of bundles. Duplicate detection
     * is handled by the {@link com.yahoo.jdisc.core.BundleCollisionHook}.
     *
     * @param bundles The bundles to allow duplicates of. An empty collection will prohibit any duplicates.
     */
    void allowDuplicateBundles(Collection<Bundle> bundles);

    /**
     * This method starts the framework instance. Before this method is called, any call to {@link
     * #installBundle(String)} or {@link #bundles()} will generate a {@link NullPointerException}.
     *
     * @throws BundleException if any error occurs
     */
    void start() throws BundleException;

    /**
     * Synchronously shut down the framework. It must be called at the end of a session to shutdown all active bundles.
     *
     * @throws BundleException if any error occurs
     */
    void stop() throws BundleException;

    /**
     * Returns true if this is a Felix based framework and not e.g. a test framework.
     */
    default boolean isFelixFramework() {
        return false;
    }

}
