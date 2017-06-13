// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.rpc;

/**
 * Class to represent a persistence provider method that has a bucket
 * as its first parameter.
 */
public class BucketProviderMethod extends PersistenceProviderMethod {
    public BucketProviderMethod(String name, PersistenceProviderHandler owner) {
        this(name, owner, "", "");
    }

    public BucketProviderMethod(String name, PersistenceProviderHandler owner, String paramTypes) {
        this(name, owner, paramTypes, "");
    }

    public BucketProviderMethod(String name, PersistenceProviderHandler owner, String paramTypes, String returnTypes) {
        super(name, owner, "ll" + paramTypes, returnTypes);
        paramDesc("bucketId", "The bucket id to perform operation on");
        paramDesc("partitionId", "The partition to perform operation on");
    }
}
