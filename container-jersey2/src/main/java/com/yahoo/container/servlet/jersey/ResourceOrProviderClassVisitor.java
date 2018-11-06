// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ResourceOrProviderClassVisitor extends ClassVisitor {
    private String className = null;
    private boolean isPublic = false;
    private boolean isAbstract = false;

    private boolean isInnerClass = false;
    private boolean isStatic = false;

    private boolean isAnnotated = false;

    public ResourceOrProviderClassVisitor() {
        super(Opcodes.ASM7);
    }

    public Optional<String> getJerseyClassName() {
        if (isJerseyClass()) {
            return Optional.of(getClassName());
        } else {
            return Optional.empty();
        }
    }

    public boolean isJerseyClass() {
        return isAnnotated && isPublic && !isAbstract && (!isInnerClass || isStatic);
    }

    public String getClassName() {
        assert (className != null);
        return org.objectweb.asm.Type.getObjectType(className).getClassName();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isPublic = isPublic(access);
        className = name;
        isAbstract = isAbstract(access);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        assert (className != null);

        if (name.equals(className)) {
            isInnerClass = true;
            isStatic = isStatic(access);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        isAnnotated |= annotationClassDescriptors.contains(desc);
        return null;
    }

    private static Set<String> annotationClassDescriptors = new HashSet<>();

    static {
        annotationClassDescriptors.add(Type.getDescriptor(Path.class));
        annotationClassDescriptors.add(Type.getDescriptor(Provider.class));
    }

    private static boolean isPublic(int access) {
        return isSet(Opcodes.ACC_PUBLIC, access);
    }

    private static boolean isStatic(int access) {
        return isSet(Opcodes.ACC_STATIC, access);
    }

    private static boolean isAbstract(int access) {
        return isSet(Opcodes.ACC_ABSTRACT, access);
    }

    private static boolean isSet(int bits, int access) {
        return (access & bits) == bits;
    }

    public static ResourceOrProviderClassVisitor visit(ClassReader classReader) {
        ResourceOrProviderClassVisitor visitor = new ResourceOrProviderClassVisitor();
        classReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        return visitor;
    }
}
