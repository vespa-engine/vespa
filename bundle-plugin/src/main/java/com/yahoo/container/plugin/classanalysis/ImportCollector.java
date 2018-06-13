// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.objectweb.asm.Type;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * @author ollivir
 */
public interface ImportCollector {
    Set<String> imports();

    default void addImportWithTypeDesc(String typeDescriptor) {
        addImport(Type.getType(typeDescriptor));
    }

    default void addImport(Type type) {
        addImport(Analyze.getClassName(type));
    }

    default void addImportWithInternalName(String name) {
        addImport(Analyze.internalNameToClassName(name));
    }

    default void addImports(Collection<String> imports) {
        imports().addAll(imports);
    }

    default void addImport(Optional<String> anImport) {
        anImport.ifPresent(pkg -> imports().add(pkg));
    }
}
