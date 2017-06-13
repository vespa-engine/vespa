// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.rpc;

/**
 * Represents a JRT persistence provider method that includes a timestamp in its request
 */
public class TimestampedProviderMethod extends BucketProviderMethod {
    public TimestampedProviderMethod(String name, PersistenceProviderHandler owner) {
        this(name, owner, "", "");
    }

    public TimestampedProviderMethod(String name, PersistenceProviderHandler owner, String paramTypes) {
        this(name, owner, paramTypes, "");
    }

    public TimestampedProviderMethod(String name, PersistenceProviderHandler owner, String paramTypes, String returnTypes) {
        super(name, owner, "l" + paramTypes, returnTypes);
        paramDesc("timestamp", "The timestamp of the operation");
    }
}
