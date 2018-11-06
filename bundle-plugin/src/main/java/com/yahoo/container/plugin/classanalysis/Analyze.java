// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.yahoo.container.plugin.util.IO.withFileInputStream;

/**
 * Main entry point for class analysis
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class Analyze {
    public static ClassFileMetaData analyzeClass(File classFile) {
        try {
            return withFileInputStream(classFile, Analyze::analyzeClass);
        } catch (RuntimeException e) {
            throw new RuntimeException("An error occurred when analyzing " + classFile.getPath(), e);
        }
    }

    public static ClassFileMetaData analyzeClass(InputStream inputStream) {
        try {
            AnalyzeClassVisitor visitor = new AnalyzeClassVisitor();
            new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_DEBUG);
            return visitor.result();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<String> internalNameToClassName(String internalClassName) {
        if(internalClassName == null) {
            return Optional.empty();
        } else {
            return getClassName(Type.getObjectType(internalClassName));
        }
    }

    static Optional<String> getClassName(Type aType) {
        switch (aType.getSort()) {
        case Type.ARRAY:
            return getClassName(aType.getElementType());
        case Type.OBJECT:
            return Optional.of(aType.getClassName());
        default:
            return Optional.empty();
        }
    }

    static AnnotationVisitor visitAnnotationDefault(ImportCollector collector) {
        return new AnnotationVisitor(Opcodes.ASM7) {
            @Override
            public void visit(String name, Object value) {
            }

            @Override
            public void visitEnum(String name, String desc, String value) {
                collector.addImportWithTypeDesc(desc);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return this;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String desc) {
                collector.addImportWithTypeDesc(desc);
                return this;
            }

            @Override
            public void visitEnd() {
            }
        };
    }
}
