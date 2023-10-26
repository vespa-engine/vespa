// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author baldersheim
 */
public class ImportPackageInfo {
    private static final String VERSION = "version";
    private final List<String> packages;
    public ImportPackageInfo(String importPackage) {
        List<String> packages = new ArrayList<>();
        List<String> tokens = new TokenizeAndDeQuote(";,=", "\"'").tokenize(importPackage);
        for (int i = 0; i < tokens.size(); ++i) {
            String token = tokens.get(i);
            if (VERSION.equals(token)) {
                ++i; // skip the optional version
            } else {
                packages.add(token);
            }
        }
        this.packages = List.copyOf(packages);
    }
    Collection<String> packages() { return packages; }
}
