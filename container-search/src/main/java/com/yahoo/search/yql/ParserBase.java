// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.collect.Sets;

import com.yahoo.yolean.Exceptions;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.BitSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Provides semantic helper functions to Parser.
 */
abstract class ParserBase extends Parser {

    private static String arrayRuleName = "array";

    public ParserBase(TokenStream input) {
        super(input);
    }
    
    private Set<String> arrayParameters = Sets.newHashSet();

    public void registerParameter(String name, String typeName) {
        if (typeName.equals(arrayRuleName)) {
            arrayParameters.add(name);
       }
    }

    public boolean isArrayParameter(ParseTree nameNode) {
        String name = nameNode.getText();
        if (name.startsWith("@")) {
            name = name.substring(1);
        }
        return name != null && arrayParameters.contains(name);
    }
    
}
