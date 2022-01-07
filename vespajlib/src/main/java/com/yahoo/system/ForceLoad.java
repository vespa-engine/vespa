// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

/**
 * Utility class used to force the loading of other classes.
 */
public class ForceLoad {

    /**
     * Force the loading of the given classes. If any of the named
     * classes can not be loaded, an error will be thrown.
     *
     * @param packageName the name of the package for which we want to forceload classes.
     * @param classNames array of names of classes (without package prefix) to force load.
     */
    public static void forceLoad(String packageName, String[] classNames, ClassLoader loader) throws ForceLoadError {
        String fullClassName = "";
        try {
            for (String className : classNames) {
                fullClassName = packageName + "." + className;
                Class.forName(fullClassName, true, loader);
            }
        } catch (Exception e) {
            throw new ForceLoadError(fullClassName, e);
        }
    }

}
