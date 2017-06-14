// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

/**
 * @author baldersheim
 * TODO: remove on vespa 7 or before
 * Use com.yahoo.yolean.concurrent.ResourceFactory instead.
 */
@Deprecated
public abstract class ResourceFactory<T> {

    public abstract T create();
}
