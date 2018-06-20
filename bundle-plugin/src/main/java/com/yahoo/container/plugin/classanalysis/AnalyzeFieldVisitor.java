// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * @author ollivir
 */
public class AnalyzeFieldVisitor extends FieldVisitor implements ImportCollector {
    private final AnalyzeClassVisitor analyzeClassVisitor;
    private final Set<String> imports = new HashSet<>();

    public AnalyzeFieldVisitor(AnalyzeClassVisitor analyzeClassVisitor) {
        super(Opcodes.ASM6);
        this.analyzeClassVisitor = analyzeClassVisitor;
    }

    @Override
    public Set<String> imports() {
        return imports;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        addImport(Type.getObjectType(attribute.type));
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        addImportWithTypeDesc(desc);

        return Analyze.visitAnnotationDefault(this);
    }

    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        return visitAnnotation(desc, visible);
    }

    @Override
    public void visitEnd() {
        analyzeClassVisitor.addImports(imports);
    }
}
