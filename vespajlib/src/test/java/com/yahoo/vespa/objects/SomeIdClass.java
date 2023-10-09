// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

public class SomeIdClass extends Identifiable
{
    public static final int classId = registerClass(1234321, SomeIdClass.class);

    @Override
    protected int onGetClassId() {
        return classId;
    }

}
