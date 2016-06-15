// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

/**
 * @author <a href="mailto:balder@yahoo-inc.com">Henning Baldersheim</a>
 * @since 5.2
 */
public abstract class ResourceFactory<T> {

    public abstract T create();
}
