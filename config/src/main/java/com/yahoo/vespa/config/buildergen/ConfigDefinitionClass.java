// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.buildergen;

/**
 * @author Ulf Lilleengen
 */
public class ConfigDefinitionClass {
    private final String name;
    private final String pkg;
    private final String definition;

    ConfigDefinitionClass(String name, String pkg, String definition) {
        this.name = name;
        this.pkg = pkg;
        this.definition = definition;
    }

    String getDefinition() {
        return definition;
    }

    String getSimpleName() {
        return name;
    }

    String getName() {
        return pkg + "." + name;
    }
}
