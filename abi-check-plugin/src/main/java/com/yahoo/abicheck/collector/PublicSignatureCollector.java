// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.collector;

import com.yahoo.abicheck.signature.JavaClassSignature;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class PublicSignatureCollector extends ClassVisitor {

  private final Map<String, JavaClassSignature> classSignatures = new LinkedHashMap<>();

  private String currentName;
  private String currentSuper;
  private Set<String> currentInterfaces;
  private int currentAccess;
  private Set<String> currentMethods;
  private Set<String> currentFields;

  public PublicSignatureCollector() {
    super(Opcodes.ASM9);
  }

  private static boolean testBit(long access, long mask) {
    return (access & mask) != 0;
  }

  private static String describeMethod(String name, int access, String returnType,
      List<String> argumentTypes) {
    return String.format("%s %s %s(%s)", describeAccess(access, Util.methodFlags), returnType, name,
        String.join(", ", argumentTypes));
  }

  private static String describeAccess(int access, List<Util.AccessFlag> possibleFlags) {
    return String.join(" ", Util.convertAccess(access, possibleFlags));
  }

  private static String describeField(String name, int access, String type) {
    return String.format("%s %s %s", describeAccess(access, Util.fieldFlags), type, name);
  }

  private static String internalNameToClassName(String superName) {
    return Type.getObjectType(superName).getClassName();
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName,
      String[] interfaces) {
    currentName = internalNameToClassName(name);
    currentSuper = internalNameToClassName(superName);
    currentInterfaces = Arrays.stream(interfaces)
        .map(PublicSignatureCollector::internalNameToClassName)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    currentAccess = access;
    currentMethods = new LinkedHashSet<>();
    currentFields = new LinkedHashSet<>();
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
      String[] exceptions) {
    if (isVisibleMember(access)) {
      Type method = Type.getMethodType(descriptor);
      List<String> argumentTypes = Arrays.stream(method.getArgumentTypes()).map(Type::getClassName)
          .collect(Collectors.toList());
      currentMethods
          .add(describeMethod(name, access, method.getReturnType().getClassName(), argumentTypes));
    }
    return null;
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature,
      Object value) {
    if (isVisibleMember(access)) {
      currentFields.add(describeField(name, access, Type.getType(descriptor).getClassName()));
    }
    return null;
  }

  private boolean isVisibleMember(int access) {
    // Public members are visible
    if (testBit(access, Opcodes.ACC_PUBLIC)) {
      return true;
    }
    // Protected members are visible if the class is not final (can be accessed from
    // extending classes)
    if (testBit(access, Opcodes.ACC_PROTECTED) && !testBit(currentAccess, Opcodes.ACC_FINAL)) {
      return true;
    }
    // Otherwise not visible
    return false;
  }

  @Override
  public void visitEnd() {
    if ((currentAccess & Opcodes.ACC_PUBLIC) != 0) {
      classSignatures.put(currentName,
          new JavaClassSignature(currentSuper, currentInterfaces,
              Util.convertAccess(currentAccess, Util.classFlags), currentMethods, currentFields));
    }
  }

  public Map<String, JavaClassSignature> getClassSignatures() {
    return classSignatures;
  }
}
