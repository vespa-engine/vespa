// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.protect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Static utility methods to validate class properties.
 *
 * <p>
 * Do note, this class will not be a reliable guarantee for correctness if you
 * have a forest of methods only differing by return type (as
 * contradistinguished from name and argument types), the current implementation
 * is minimal.
 * </p>
 *
 * @author Steinar Knutsen
 */
public final class ClassValidator {

    /**
     * Check all protected, public and package private declared methods of
     * maskedClass is implemented in testClass. Note, this will by definition
     * blow up on final methods in maskedClass.
     *
     * @param testClass class which wraps or masks another class
     * @param maskedClass class which is masked or wrapped
     * @return the methods which seem to miss from testClass to be complete
     */
    public static List<Method> unmaskedMethods(Class<?> testClass,
            Class<?> maskedClass) {
        List<Method> unmasked = new ArrayList<>();
        Method[] methodsToMask = maskedClass.getDeclaredMethods();
        for (Method m : methodsToMask) {
            int modifiers = m.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                continue;
            }
            try {
                testClass.getDeclaredMethod(m.getName(), m.getParameterTypes());
            } catch (NoSuchMethodException e) {
                unmasked.add(m);
            }
        }
        return unmasked;
    }

    /**
     * Check testClass overrides all protected, public and package private
     * methods of its immediate super class. See unmaskedMethods().
     *
     * @param testClass the class to check whether completely masks its super class
     * @return the methods missing from testClass to completely override its
     *         immediate super class
     */
    public static List<Method> unmaskedMethodsFromSuperclass(Class<?> testClass) {
        return unmaskedMethods(testClass, testClass.getSuperclass());
    }

}
