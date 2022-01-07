// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

/**
 * Special error to be propagated when force-loading a class fails.
 */
public class ForceLoadError extends java.lang.Error {

    /**
     * Create a new force load error
     *
     * @param className full name of offending class
     * @param cause what caused the failure
     */
    public ForceLoadError(String className, Throwable cause) {
        super("Force loading class '" + className + "' failed", cause);
    }

}
