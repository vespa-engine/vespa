// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.onnxruntime;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.logging.Logger;

/**
 * @author arnej
 * Loads native libraries when the bundle is activated.
 * Use system properties to ensure onnxruntime won't try
 * to load them itself.
 **/
public class OnnxBundleActivator implements BundleActivator {

    private static final String SKIP_PREFIX = "onnxruntime.native.";
    private static final String SKIP_SUFFIX = ".skip";
    private static final String SKIP_VALUE = "true";
    private static final String[] LIBRARY_NAMES = { "onnxruntime", "onnxruntime4j_jni" };
    private static final Logger log = Logger.getLogger(OnnxBundleActivator.class.getName());

    @Override
    public void start(BundleContext ctx) {
        for (String libName : LIBRARY_NAMES) {
            String skipProp = SKIP_PREFIX + libName + SKIP_SUFFIX;
            if (SKIP_VALUE.equals(System.getProperty(skipProp))) {
                log.info("already loaded native library "+libName+", skipping");
            } else {
                System.setProperty(skipProp, SKIP_VALUE);
                log.info("loading native library: "+libName);
                try {
                    System.loadLibrary(libName);
                    log.fine("loaded native library OK: "+libName);
                } catch (Exception e) {
                    log.warning("Could not load native library '"+libName+"' because: "+e.getMessage());
                }
            }
        }
    }

    @Override
    public void stop(BundleContext ctx) {
        // not sure how to test that loading and unloading multiple times actually works,
        // but this should in theory do the necessary thing.
        for (String libName : LIBRARY_NAMES) {
            String skipProp = SKIP_PREFIX + libName + SKIP_SUFFIX;
            System.clearProperty(skipProp);
            log.info("will unload native library: "+libName);
        }
    }
}
