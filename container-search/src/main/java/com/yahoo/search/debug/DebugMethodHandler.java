// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import com.yahoo.jrt.MethodHandler;

/**
 * A method handler that can describe its signature.
 *
 * @author tonytv
 */
interface DebugMethodHandler extends MethodHandler {
    JrtMethodSignature getSignature();
}
