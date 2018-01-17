// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads the semantic rules from the application package by delegating to SemanticRules.
 *
 * @author bratseth
 */
// TODO: Move into SemanticRules
public class SemanticRuleBuilder {

    /** Build the set of semantic rules for an application package */
    public SemanticRules build(ApplicationPackage applicationPackage) {
        List<NamedReader> ruleBaseFiles = null;
        try {
            ruleBaseFiles = applicationPackage.getFiles(ApplicationPackage.RULES_DIR, "sr");
            return new SemanticRules(ruleBaseFiles.stream().map(this::toRuleBaseConfigView).collect(Collectors.toList()));
        }
        finally {
            NamedReader.closeAll(ruleBaseFiles);
        }
    }

    private SemanticRules.RuleBase toRuleBaseConfigView(NamedReader reader) {
        try {
            String ruleBaseString = IOUtils.readAll(reader.getReader());
            boolean isDefault = ruleBaseString.contains("@default");
            return new SemanticRules.RuleBase(toName(reader.getName()), isDefault, ruleBaseString);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not load rules bases", e);
        }
    }

    private String toName(String fileName) {
        String shortName = new File(fileName).getName();
        return shortName.substring(0, shortName.length()-".sr".length());
    }

}
