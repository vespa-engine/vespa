// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.util.List;
import java.util.Map;

/**
 * Input for class analysis tests.
 * @author tonytv
 */
@SuppressWarnings("unused")
public class Methods {
    public void method1() {
        Base b = new Base();
        System.out.println(Fields.field2.size());

        Object o = new Interface1[1][2][3];
        int[][][] arr = new int[1][2][3];
        int[] arr2 = new int[1];

        System.out.println(new int[1].length + "--" + arr2[0]);

        int[][] exerciseTypeInsn = new int[1][];

        Runnable methodHandle = ClassWithMethod::test;
    }

    public static void method2() {
        Derived d = new Derived();
    }

    public void methodTakingGenericArgument(Map<String, List<Dummy>> map) {}
}
