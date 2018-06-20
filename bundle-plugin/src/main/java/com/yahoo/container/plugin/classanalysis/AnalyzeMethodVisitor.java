// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Picks up classes used in method bodies.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
class AnalyzeMethodVisitor extends MethodVisitor implements ImportCollector {
    private final Set<String> imports = new HashSet<>();
    private final AnalyzeClassVisitor analyzeClassVisitor;

    AnalyzeMethodVisitor(AnalyzeClassVisitor analyzeClassVisitor) {
        super(Opcodes.ASM6);
        this.analyzeClassVisitor = analyzeClassVisitor;
    }

    public Set<String> imports() {
        return imports;
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
        return visitAnnotation(desc, visible);
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        return Analyze.visitAnnotationDefault(this);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        addImport(Type.getObjectType(attribute.type));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        addImportWithTypeDesc(desc);

        return Analyze.visitAnnotationDefault(this);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        analyzeClassVisitor.addImports(imports);
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        addImportWithTypeDesc(desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        addImportWithInternalName(owner);
        Arrays.asList(Type.getArgumentTypes(desc)).forEach(this::addImport);
        addImport(Type.getReturnType(desc));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        addImportWithInternalName(owner);
        addImportWithTypeDesc(desc);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        addImportWithInternalName(type);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null) { //null means finally block
            addImportWithInternalName(type);
        }
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        addImportWithTypeDesc(desc);
    }

    @Override
    public void visitLdcInsn(Object constant) {
        if (constant instanceof Type) {
            addImport((Type) constant);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrapMethod, Object... bootstrapMethodArgs) {
        for (Object arg : bootstrapMethodArgs) {
            if (arg instanceof Type) {
                addImport((Type) arg);
            } else if (arg instanceof Handle) {
                addImportWithInternalName(((Handle) arg).getOwner());
                Arrays.asList(Type.getArgumentTypes(desc)).forEach(this::addImport);
            } else if ((arg instanceof Number) == false && (arg instanceof String) == false) {
                throw new AssertionError("Unexpected type " + arg.getClass() + " with value '" + arg + "'");
            }
        }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
    }

    @Override
    public void visitLineNumber(int line, Label start) {
    }

    //only for debugging
    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitIincInsn(int variable, int increment) {
    }

    @Override
    public void visitLabel(Label label) {
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
    }

    @Override
    public void visitVarInsn(int opcode, int variable) {
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
    }

    @Override
    public void visitInsn(int opcode) {
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
    }

    @Override
    public void visitCode() {
    }
}
