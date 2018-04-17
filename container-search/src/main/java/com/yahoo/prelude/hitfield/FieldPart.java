// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public interface FieldPart {
    public abstract boolean isFinal();
    public abstract boolean isToken();
    public abstract String getContent();
    public abstract String toString();
}
