// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

/**
 * Represents the signatures of a jrt method.
 *
 * @author tonytv
 */
final class JrtMethodSignature {
    final String returnTypes;
    final String parametersTypes;

    JrtMethodSignature(String returnTypes, String parametersTypes) {
        this.returnTypes = returnTypes;
        this.parametersTypes = parametersTypes;
    }
}
