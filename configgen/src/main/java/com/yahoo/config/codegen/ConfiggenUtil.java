// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class ConfiggenUtil {

    /**
     * Create class name from def name
     * @param defName The file name without the '.def' suffix
     */
    public static String createClassName(String defName) {
        String className =  Arrays.stream(defName.split("-"))
                .map(ConfiggenUtil::capitalize)
                .collect(Collectors.joining())
                + "Config";

        if (! isLegalJavaIdentifier(className))
            throw new CodegenRuntimeException("Illegal config definition file name '" + defName +
                                                      "'. Must be a legal Java identifier.");

        return className;
    }

    static String capitalize(String in) {
        StringBuilder sb = new StringBuilder(in);
        sb.setCharAt(0, Character.toTitleCase(in.charAt(0)));
        return sb.toString();
    }

    private static boolean isLegalJavaIdentifier(String name) {
        if (name.isEmpty()) return false;
        if (! Character.isJavaIdentifierStart(name.charAt(0))) return false;

        for (char c : name.substring(1).toCharArray()) {
            if (! Character.isJavaIdentifierPart(c)) return false;
        }
        return true;
    }
}
