// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Main entry point for class analysis
 *
 * @author Tony Vaagenes
 * @author ollivir
 * @author gjoranv
 */
public class Analyze {

    public enum JdkVersionCheck {
        ENABLED,
        DISABLED
    }


    static ClassFileMetaData analyzeClass(File classFile) {
        return analyzeClass(classFile, JdkVersionCheck.DISABLED, null);
    }

    public static ClassFileMetaData analyzeClass(File classFile, JdkVersionCheck jdkVersionCheck, ArtifactVersion artifactVersion) {
        try {
            return analyzeClass(new FileInputStream(classFile), jdkVersionCheck, artifactVersion);
        } catch (Exception e) {
            throw new RuntimeException("An error occurred when analyzing " + classFile.getPath(), e);
        }
    }

    public static ClassFileMetaData analyzeClass(InputStream inputStream, JdkVersionCheck jdkVersionCheck, ArtifactVersion artifactVersion) {
        try {
            AnalyzeClassVisitor visitor = new AnalyzeClassVisitor(artifactVersion, jdkVersionCheck);
            new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_DEBUG);
            return visitor.result();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<String> internalNameToClassName(String internalClassName) {
        if (internalClassName == null) {
            return Optional.empty();
        } else {
            return getClassName(Type.getObjectType(internalClassName));
        }
    }

    static Optional<String> getClassName(Type aType) {
        return switch (aType.getSort()) {
            case Type.ARRAY -> getClassName(aType.getElementType());
            case Type.OBJECT -> Optional.of(aType.getClassName());
            case Type.METHOD -> getClassName(aType.getReturnType());
            default -> Optional.empty();
        };
    }

    static AnnotationVisitor visitAnnotationDefault(ImportCollector collector) {
        return new AnnotationVisitor(Opcodes.ASM9) {
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
