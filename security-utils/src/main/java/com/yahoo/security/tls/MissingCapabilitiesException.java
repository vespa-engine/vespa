// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

/**
 * Intentionally checked to force caller to handle missing permissions at call site.
 *
 * @author bjorncs
 */
public class MissingCapabilitiesException extends Exception {

    public MissingCapabilitiesException(String message) { super(message); }

}
