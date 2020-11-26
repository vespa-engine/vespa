// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

import com.google.common.io.Files;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.codegen.JavaClassBuilder;
import com.yahoo.text.StringUtilities;

import java.io.File;
import java.io.StringReader;

/**
 * Represents a higher level functionality on a config definition to (in the future) hide the InnerCNode class.
 * @author Ulf Lilleengen
 */
public class ConfigDefinition {

    private final String name;
    private final String[] defSchema;
    private final InnerCNode cnode;

    // TODO: Should have namespace
    public ConfigDefinition(String name, String[] defSchema) {
        this.name = name;
        this.defSchema = defSchema;
        this.cnode = new DefParser(name, new StringReader(StringUtilities.implode(defSchema, "\n"))).getTree();
    }

    public InnerCNode getCNode() {
        return cnode;
    }

    public ConfigDefinitionClass generateClass() {
        File tempDir = Files.createTempDir();
        DefParser parser = new DefParser(name, new StringReader(StringUtilities.implode(defSchema, "\n")));
        JavaClassBuilder builder = new JavaClassBuilder(parser.getTree(), parser.getNormalizedDefinition(),  tempDir, null);
        String className = builder.className();
        return new ConfigDefinitionClass(className, builder.javaPackage(), builder.getConfigClass(className));
    }

}
