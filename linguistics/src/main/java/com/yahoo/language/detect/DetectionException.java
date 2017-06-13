// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

/**
 * Exception that is thrown when detection fails.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public final class DetectionException extends RuntimeException {

    public DetectionException(String str) {
        super(str);
    }
}
