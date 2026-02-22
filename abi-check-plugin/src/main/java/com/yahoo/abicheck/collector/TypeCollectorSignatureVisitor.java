// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.collector;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * ASM SignatureVisitor that collects all class types referenced in a generic type signature.
 * Used by {@link LeakageSignatureCollector} to extract types from generic signatures
 * that would otherwise be lost through type erasure.
 */
class TypeCollectorSignatureVisitor extends SignatureVisitor {

    private final Set<String> types = new HashSet<>();

    TypeCollectorSignatureVisitor() {
        super(Opcodes.ASM9);
    }

    Set<String> getTypes() {
        return types;
    }

    @Override
    public void visitClassType(String name) {
        types.add(Type.getObjectType(name).getClassName());
    }

    @Override
    public SignatureVisitor visitClassBound() { return this; }

    @Override
    public SignatureVisitor visitInterfaceBound() { return this; }

    @Override
    public SignatureVisitor visitSuperclass() { return this; }

    @Override
    public SignatureVisitor visitInterface() { return this; }

    @Override
    public SignatureVisitor visitParameterType() { return this; }

    @Override
    public SignatureVisitor visitReturnType() { return this; }

    @Override
    public SignatureVisitor visitExceptionType() { return this; }

    @Override
    public SignatureVisitor visitArrayType() { return this; }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) { return this; }
}
