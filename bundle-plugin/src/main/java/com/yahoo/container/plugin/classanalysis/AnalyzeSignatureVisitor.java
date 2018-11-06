// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */

class AnalyzeSignatureVisitor extends SignatureVisitor implements ImportCollector {
    private final AnalyzeClassVisitor analyzeClassVisitor;
    private Set<String> imports = new HashSet<>();

    AnalyzeSignatureVisitor(AnalyzeClassVisitor analyzeClassVisitor) {
        super(Opcodes.ASM7);
        this.analyzeClassVisitor = analyzeClassVisitor;
    }

    public Set<String> imports() {
        return imports;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        analyzeClassVisitor.addImports(imports);
    }

    @Override
    public void visitClassType(String className) {
        addImportWithInternalName(className);
    }

    @Override
    public void visitFormalTypeParameter(String name) {
    }

    @Override
    public SignatureVisitor visitClassBound() {
        return this;
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        return this;
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        return this;
    }

    @Override
    public SignatureVisitor visitInterface() {
        return this;
    }

    @Override
    public SignatureVisitor visitParameterType() {
        return this;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        return this;
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        return this;
    }

    @Override
    public void visitBaseType(char descriptor) {
    }

    @Override
    public void visitTypeVariable(String name) {
    }

    @Override
    public SignatureVisitor visitArrayType() {
        return this;
    }

    @Override
    public void visitInnerClassType(String name) {
    }

    @Override
    public void visitTypeArgument() {
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
        return this;
    }

    static void analyzeClass(String signature, AnalyzeClassVisitor analyzeClassVisitor) {
        if (signature != null) {
            new SignatureReader(signature).accept(new AnalyzeSignatureVisitor(analyzeClassVisitor));
        }
    }

    static void analyzeMethod(String signature, AnalyzeClassVisitor analyzeClassVisitor) {
        analyzeClass(signature, analyzeClassVisitor);
    }

    static void analyzeField(String signature, AnalyzeClassVisitor analyzeClassVisitor) {
        if (signature != null)
            new SignatureReader(signature).acceptType(new AnalyzeSignatureVisitor(analyzeClassVisitor));
    }
}
