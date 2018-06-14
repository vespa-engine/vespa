// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.yahoo.osgi.annotation.ExportPackage;
import com.yahoo.osgi.annotation.Version;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Picks up classes used in class files.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
class AnalyzeClassVisitor extends ClassVisitor implements ImportCollector {
    private String name = null;
    private Set<String> imports = new HashSet<>();
    private Optional<ExportPackageAnnotation> exportPackageAnnotation = Optional.empty();

    AnalyzeClassVisitor() {
        super(Opcodes.ASM6);
    }

    @Override
    public Set<String> imports() {
        return imports;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        addImport(Type.getObjectType(attribute.type));
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        Analyze.getClassName(Type.getReturnType(desc)).ifPresent(imports::add);
        Arrays.asList(Type.getArgumentTypes(desc)).forEach(argType -> Analyze.getClassName(argType).ifPresent(imports::add));
        if (exceptions != null) {
            Arrays.asList(exceptions).forEach(ex -> Analyze.internalNameToClassName(ex).ifPresent(imports::add));
        }

        AnalyzeSignatureVisitor.analyzeMethod(signature, this);
        return new AnalyzeMethodVisitor(this);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        Analyze.getClassName(Type.getType(desc)).ifPresent(imports::add);

        AnalyzeSignatureVisitor.analyzeField(signature, this);
        return new AnalyzeFieldVisitor(this);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.name = Analyze.internalNameToClassName(name)
                .orElseThrow(() -> new RuntimeException("Unable to resolve class name for " + name));

        addImportWithInternalName(superName);
        Arrays.asList(interfaces).forEach(this::addImportWithInternalName);

        AnalyzeSignatureVisitor.analyzeClass(signature, this);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
    }

    @Override
    public void visitSource(String source, String debug) {
    }

    @Override
    public void visitEnd() {
    }

    @SuppressWarnings("unchecked")
    private static <T> T defaultVersionValue(String name) {
        try {
            return (T) Version.class.getMethod(name).getDefaultValue();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Could not locate method " + name);
        }
    }

    private AnnotationVisitor visitExportPackage() {
        return new AnnotationVisitor(Opcodes.ASM6) {
            private int major = defaultVersionValue("major");
            private int minor = defaultVersionValue("minor");
            private int micro = defaultVersionValue("micro");
            private String qualifier = defaultVersionValue("qualifier");

            @Override
            public void visit(String name, Object value) {
                if (name != null) {
                    switch (name) {
                    case "major":
                        major = (int) value;
                        break;
                    case "minor":
                        minor = (int) value;
                        break;
                    case "micro":
                        micro = (int) value;
                        break;
                    case "qualifier":
                        qualifier = (String) value;
                        break;
                    }
                }
            }

            @Override
            public void visitEnd() {
                exportPackageAnnotation = Optional.of(new ExportPackageAnnotation(major, minor, micro, qualifier));
            }

            @Override
            public void visitEnum(String name, String desc, String value) {
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return this;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String desc) {
                return this;
            }
        };
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (ExportPackage.class.getName().equals(Type.getType(desc).getClassName())) {
            return visitExportPackage();
        } else {
            addImportWithTypeDesc(desc);
            return Analyze.visitAnnotationDefault(this);
        }
    }

    ClassFileMetaData result() {
        assert (!imports.contains("int"));
        return new ClassFileMetaData(name, imports, exportPackageAnnotation);
    }
}
