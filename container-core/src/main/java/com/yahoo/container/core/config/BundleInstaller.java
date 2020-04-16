package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.List;

/**
 * @author gjoranv
 */
public interface BundleInstaller {

    /**
     * Installs the bundle with the given file reference, plus all bundles in its X-JDisc-Preinstall-Bundle directive.
     * Returns all bundles installed to the given OSGi framework as a result of this call.
     */
    List<Bundle> installBundles(FileReference reference, Osgi osgi) throws InterruptedException;

}
