// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Reserved words that cannot be used as variable names in a config definition file.
 *
 * @author hmusum
 */
public class ReservedWords {

    public static final String INTERNAL_PREFIX = "__";
    final static Pattern internalPrefixPattern = Pattern.compile("^" + INTERNAL_PREFIX + ".*");
    final static Pattern capitalizedPattern    = Pattern.compile("^[A-Z].*");

    private static final String[] cKeywords =
            {"asm", "auto", "bool", "break", "case", "catch",
                    "char", "class", "const", "const_cast", "continue", "default",
                    "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit",
                    "export", "extern", "false", "float", "for", "friend", "goto", "if",
                    "inline", "int", "item", "long", "mutable", "namespace", "new", "operator",
                    "private", "protected", "public", "register", "reinterpret_cast",
                    "return", "short", "signed", "sizeof", "static", "static_cast",
                    "struct", "switch", "template", "this", "throw", "true", "try",
                    "typedef", "typeid", "typename", "union", "unsigned",
                    "using", "virtual", "void", "volatile", "wchar_t", "while", "and", "bitor",
                    "not", "or", "xor", "and_eq", "compl", "not_eq", "or_eq", "xor_eq",
                    "bitand"};

    private static final String[] javaKeywords =
            {"abstract", "boolean", "break", "byte", "case",
                    "catch", "char", "class","continue", "default", "do", "double",
                    "else", "extends","false", "final", "finally", "float", "for",
                    "if","implements", "import", "instanceof", "int", "interface",
                    "item", "long","native", "new", "null", "package", "private",
                    "protected","public", "return", "short", "static",
                    "strictfp","super","switch", "synchronized", "this",
                    "throw","throws","transient", "true", "try", "void",
                    "volatile","while", "byvalue", "cast", "const", "future",
                    "generic","goto", "inner", "operator", "outer", "rest", "var"};

    private static final HashMap<String, String> allKeyWords;

    static {
        allKeyWords = new HashMap<>();
        for (String s : cKeywords) {
            allKeyWords.put(s, "C");
        }
        for (String s : javaKeywords) {
            if (allKeyWords.containsKey(s)) {
                allKeyWords.put(s, "C and Java");
            } else {
                allKeyWords.put(s, "Java");
            }
        }
    }


    public static boolean isReservedWord(String word) {
        return allKeyWords.containsKey(word);
    }

    public static String getLanguageForReservedWord(String word) {
        return allKeyWords.get(word);
    }

}
