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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds test classes by looking for one of four category annotations: {@link SystemTest}, {@link StagingTest},
 * {@link StagingSetup}, {@link ProductionTest}. A class counts as a test if any of these annotations is reachable
 * from it, either directly, through a meta-annotation (an annotation on an annotation), or through a superclass.
 *
 * <p>Usage is two-pass: call {@link #visitClass} for every {@code .class} file under {@code target/test-classes}
 * to build the class graph, then call {@link #resolve} to bucket each class. Two passes are needed because a class
 * may be annotated indirectly through a base class or a meta-annotation that hasn't been read yet.</p>
 *
 * <p>Only classes under {@code target/test-classes} are inspected; superclasses or annotations defined in
 * external JARs are not followed.</p>
 *
 * @author bjorncs
 */
class TestAnnotationAnalyzer {

    private static final Set<String> CATEGORY_ANNOTATIONS = Set.of(
            SystemTest.class.getName(),
            StagingTest.class.getName(),
            StagingSetup.class.getName(),
            ProductionTest.class.getName());

    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();

    private final List<String> systemTests = new ArrayList<>();
    private final List<String> stagingTests = new ArrayList<>();
    private final List<String> stagingSetupTests = new ArrayList<>();
    private final List<String> productionTests = new ArrayList<>();

    List<String> systemTests() { return systemTests; }
    List<String> stagingTests() { return stagingTests; }
    List<String> stagingSetupTests() { return stagingSetupTests; }
    List<String> productionTests() { return productionTests; }

    /** Pass 1: read a {@code .class} file and record its superclass, direct class-level annotations and whether it is itself an annotation. */
    void visitClass(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile)) {
            new ClassReader(in).accept(new AsmClassVisitor(), ClassReader.SKIP_DEBUG);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Pass 2: compute the effective category annotations for every visited class and bucket it accordingly. */
    void resolve() {
        for (Map.Entry<String, ClassInfo> entry : classes.entrySet()) {
            ClassInfo info = entry.getValue();
            if (info.isAnnotation) continue;
            Set<String> effective = effectiveAnnotations(entry.getKey());
            if (effective.contains(SystemTest.class.getName())) systemTests.add(entry.getKey());
            if (effective.contains(StagingTest.class.getName())) stagingTests.add(entry.getKey());
            if (effective.contains(StagingSetup.class.getName())) stagingSetupTests.add(entry.getKey());
            if (effective.contains(ProductionTest.class.getName())) productionTests.add(entry.getKey());
        }
    }

    private Set<String> effectiveAnnotations(String className) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String current = className; current != null; current = parentOf(current)) {
            ClassInfo info = classes.get(current);
            if (info == null) break;
            for (String annotation : info.directAnnotations) {
                expand(annotation, result, seen);
            }
        }
        result.retainAll(CATEGORY_ANNOTATIONS);
        return result;
    }

    private void expand(String annotationClassName, Set<String> out, Set<String> seen) {
        if (!seen.add(annotationClassName)) return;
        out.add(annotationClassName);
        ClassInfo info = classes.get(annotationClassName);
        if (info == null) return;
        for (String meta : info.directAnnotations) {
            expand(meta, out, seen);
        }
    }

    private String parentOf(String className) {
        ClassInfo info = classes.get(className);
        if (info == null) return null;
        String parent = info.superName;
        if (parent == null || parent.equals("java.lang.Object")) return null;
        return parent;
    }

    private static final class ClassInfo {
        String superName;
        boolean isAnnotation;
        final Set<String> directAnnotations = new LinkedHashSet<>();
    }

    private class AsmClassVisitor extends ClassVisitor {

        private ClassInfo current;

        // TODO: bump to ASM9 on Vespa 9
        AsmClassVisitor() { super(Opcodes.ASM8); }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            Type type = Type.getObjectType(name);
            if (type.getSort() != Type.OBJECT) return;
            String className = type.getClassName();
            current = classes.computeIfAbsent(className, k -> new ClassInfo());
            current.isAnnotation = (access & Opcodes.ACC_ANNOTATION) != 0;
            if (superName != null) {
                current.superName = Type.getObjectType(superName).getClassName();
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (current != null) {
                current.directAnnotations.add(Type.getType(descriptor).getClassName());
            }
            return null;
        }
    }
}
