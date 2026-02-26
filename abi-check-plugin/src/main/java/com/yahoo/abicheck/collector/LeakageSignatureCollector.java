// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.collector;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * ASM ClassVisitor that collects all types referenced in the public API surface of public classes.
 * Extracts types from superclass, interfaces, method signatures, field types, thrown exceptions,
 * and generic type parameters. Can be reused across multiple class files; results accumulate.
 */
public class LeakageSignatureCollector extends ClassVisitor {

    private final Map<String, Set<String>> referencedTypes = new LinkedHashMap<>();

    private String currentClassName;
    private int currentAccess;
    private Set<String> currentTypes;

    public LeakageSignatureCollector() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        currentClassName = Type.getObjectType(name).getClassName();
        currentAccess = access;
        currentTypes = new TreeSet<>();

        if (superName != null) {
            addObjectType(superName);
        }
        if (interfaces != null) {
            for (String iface : interfaces) {
                addObjectType(iface);
            }
        }
        if (signature != null) {
            collectFromSignature(signature, false);
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                     String[] exceptions) {
        if (isVisibleMember(access)) {
            Type methodType = Type.getMethodType(descriptor);
            addType(methodType.getReturnType());
            for (Type argType : methodType.getArgumentTypes()) {
                addType(argType);
            }
            if (exceptions != null) {
                for (String exception : exceptions) {
                    addObjectType(exception);
                }
            }
            if (signature != null) {
                collectFromSignature(signature, false);
            }
        }
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                   Object value) {
        if (isVisibleMember(access)) {
            addType(Type.getType(descriptor));
            if (signature != null) {
                collectFromSignature(signature, true);
            }
        }
        return null;
    }

    @Override
    public void visitEnd() {
        if ((currentAccess & Opcodes.ACC_PUBLIC) != 0) {
            referencedTypes.put(currentClassName, currentTypes);
        }
    }

    /** Returns a map from fully qualified class name to the set of types referenced in its public API. */
    public Map<String, Set<String>> getReferencedTypes() {
        return referencedTypes;
    }

    private void addObjectType(String internalName) {
        currentTypes.add(Type.getObjectType(internalName).getClassName());
    }

    private void addType(Type type) {
        while (type.getSort() == Type.ARRAY) {
            type = type.getElementType();
        }
        if (type.getSort() == Type.OBJECT) {
            currentTypes.add(type.getClassName());
        }
    }

    private void collectFromSignature(String signature, boolean isFieldSignature) {
        TypeCollectorSignatureVisitor visitor = new TypeCollectorSignatureVisitor();
        SignatureReader reader = new SignatureReader(signature);
        if (isFieldSignature) {
            reader.acceptType(visitor);
        } else {
            reader.accept(visitor);
        }
        currentTypes.addAll(visitor.getTypes());
    }

    private boolean isVisibleMember(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return true;
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0 && (currentAccess & Opcodes.ACC_FINAL) == 0) {
            return true;
        }
        return false;
    }
}
