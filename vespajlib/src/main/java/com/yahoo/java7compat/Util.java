// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.java7compat;

/**
 * @author baldersheim
 * @since 5.2
 */

public class Util {
    private static final int javaVersion = Integer.valueOf(System.getProperty("java.version").substring(2,3));
    public static boolean isJava7Compatible() { return javaVersion >= 7; }
    /**
     * Takes the double value and prints it in a way that is compliant with the way java7 prints them.
     * This is due to java7 finally fixing the trailing zero problem
     * @param d the double value
     * @return string representation of the double value
     */
    public static String toJava7String(double d) {
        String s =  String.valueOf(d);
        if ( ! isJava7Compatible() ) {
            s = nonJava7CompatibleString(s);
        }
        return s;
    }
    
    static String nonJava7CompatibleString(String s) {
        if ((s.length() >= 3) && s.contains(".")) {
            int l = s.length();
            for(; l > 2 && (s.charAt(l-1) == '0') && (s.charAt(l-2) >= '0') && (s.charAt(l-1) <= '9'); l--);
            if (l != s.length()) {
                s = s.substring(0, l);
            }
        }
        return s;
    }

}
