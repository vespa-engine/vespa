// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;


import ai.vespa.hosted.cd.ProductionTest;
import ai.vespa.hosted.cd.StagingSetup;
import ai.vespa.hosted.cd.StagingTest;
import ai.vespa.hosted.cd.SystemTest;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes test classes and tracks all classes containing hosted Vespa test annotations ({@link ai.vespa.hosted.cd}).
 *
 * @author bjorncs
 */
class TestAnnotationAnalyzer {

    private final List<String> systemTests = new ArrayList<>();
    private final List<String> stagingTests = new ArrayList<>();
    private final List<String> stagingSetupTests = new ArrayList<>();
    private final List<String> productionTests = new ArrayList<>();

    List<String> systemTests() { return systemTests; }
    List<String> stagingTests() { return stagingTests; }
    List<String> stagingSetupTests() { return stagingSetupTests; }
    List<String> productionTests() { return productionTests; }

    void analyzeClass(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile)) {
            new ClassReader(in).accept(new AsmClassVisitor(), ClassReader.SKIP_DEBUG);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class AsmClassVisitor extends ClassVisitor {

        private String className;

        AsmClassVisitor() { super(Opcodes.ASM7); }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            Type type = Type.getObjectType(name);
            if (type.getSort() == Type.OBJECT) {
                this.className = type.getClassName();
                super.visit(version, access, name, signature, superName, interfaces);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String annotationClassName = Type.getType(descriptor).getClassName();
            if (ProductionTest.class.getName().equals(annotationClassName)) {
                productionTests.add(className);
            } else if (StagingTest.class.getName().equals(annotationClassName)) {
                stagingTests.add(className);
            } else if (StagingSetup.class.getName().equals(annotationClassName)) {
                stagingSetupTests.add(className);
            } else if (SystemTest.class.getName().equals(annotationClassName)) {
                systemTests.add(className);
            }
            return null;
        }
    }
}
