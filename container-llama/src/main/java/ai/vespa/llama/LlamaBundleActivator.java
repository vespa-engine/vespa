// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.llama;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.logging.Logger;

/**
 * @author arnej
 * Finds native libraries when the bundle is activated.
 **/
public class LlamaBundleActivator implements BundleActivator {

    private static final String PATH_PROPNAME = "de.kherud.llama.lib.path";
    private static final Logger log = Logger.getLogger(LlamaBundleActivator.class.getName());

    @Override
    public void start(BundleContext ctx) {
        log.fine("start bundle");
        if (checkFilenames(
                    "/dev/nvidia0",
                    "/opt/vespa-deps/lib64/cuda/libllama.so",
                    "/opt/vespa-deps/lib64/cuda/libjllama.so")) {
            System.setProperty(PATH_PROPNAME, "/opt/vespa-deps/lib64/cuda");
        } else if (checkFilenames(
                    "/opt/vespa-deps/lib64/libllama.so",
                    "/opt/vespa-deps/lib64/libjllama.so")) {
            System.setProperty(PATH_PROPNAME, "/opt/vespa-deps/lib64");
        } else {
            throw new IllegalArgumentException("Cannot find shared libraries");
        }
    }

    @Override
    public void stop(BundleContext ctx) {
        log.fine("stop bundle");
    }

    private boolean checkFilenames(String... filenames) {
        for (String fn : filenames) {
            var f = new java.io.File(fn);
            if (! f.canRead()) {
                return false;
            }
        }
        return true;
    }

}
