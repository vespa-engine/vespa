// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import java.util.List;
import java.util.ArrayList;

public class FooBarIdClass extends Identifiable
{
    public static final int classId = registerClass(17, FooBarIdClass.class);

    private String foo = "def-foo";
    private int bar = 42;

    private List<Integer> lst = new ArrayList<>();

    public FooBarIdClass() {
        lst.add(17);
        lst.add(42);
        lst.add(666);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("foo", foo);
        visitor.visit("bar", bar);
        visitor.visit("lst", lst);
    }

}
