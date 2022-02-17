// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import java.io.File;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class TestUtilities {

    public static ClassFileMetaData analyzeClass(Class<?> clazz) {
        return Analyze.analyzeClass(classFile(name(clazz)));
    }

    public static File classFile(String className) {
        return new File("target/test-classes/" + className.replace('.', '/') + ".class");
    }

    public static String name(Class<?> clazz) {
        return clazz.getName();
    }

}
