// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * @author arnej
 */
public class MlInheritsSplit {
    private static final String toFind = " inherits ";
    public final List<String> inherits = new ArrayList<>();
    public final String features;
    public MlInheritsSplit(String input) {
        int idx = input.indexOf(toFind);
        if (idx < 0) throw new IllegalArgumentException("Did not find ' inherits ' in input: " + input);
        idx += toFind.length();
        int lbrace = input.indexOf("{");
        if (lbrace < idx) throw new IllegalArgumentException("Did not find '{' after inherits in input: " + input);
        int rbrace = input.lastIndexOf("}");
        if (rbrace < lbrace)  throw new IllegalArgumentException("Did not find '}' after '{' in input: " + input);
        String part1 = input.substring(idx, lbrace);
        String part2 = input.substring(lbrace + 1, rbrace);
        for (String i : part1.split(",")) {
            inherits.add(i.trim());
        }
        features = part2;
    }
}
