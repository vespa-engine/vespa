package com.yahoo.abicheck.collector;

import com.yahoo.abicheck.signature.JavaClassSignature;
import com.yahoo.abicheck.signature.JavaMethodSignature;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class PublicSignatureCollector extends ClassVisitor {

  private final Map<String, JavaClassSignature> classSignatures = new LinkedHashMap<>();

  private String currentName;
  private int currentAccess;
  private Map<String, JavaMethodSignature> currentMethods;

  public PublicSignatureCollector() {
    super(Opcodes.ASM6);
  }

  private static String methodNameWithArguments(String name, List<String> argumentTypes) {
    return String.format("%s(%s)", name, String.join(", ", argumentTypes));
  }

  private static boolean testBit(long access, long mask) {
    return (access & mask) != 0;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName,
      String[] interfaces) {
    currentName = Type.getObjectType(name).getClassName();
    currentAccess = access;
    currentMethods = new LinkedHashMap<>();
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
      String[] exceptions) {
    if (isVisibleMethod(access)) {
      Type method = Type.getMethodType(descriptor);
      List<String> argumentTypes = Arrays.stream(method.getArgumentTypes()).map(Type::getClassName)
          .collect(Collectors.toList());
      name = methodNameWithArguments(name, argumentTypes);
      currentMethods.put(name,
          new JavaMethodSignature(Util.convertAccess(access, Util.methodFlags),
              method.getReturnType().getClassName()));
    }
    return null;
  }

  private boolean isVisibleMethod(int access) {
    // Public methods are visible
    if (testBit(access, Opcodes.ACC_PUBLIC)) {
      return true;
    }
    // Protected non-static methods are visible if the class is not final (can be called from
    // extending classes)
    if (!testBit(access, Opcodes.ACC_STATIC) && testBit(access, Opcodes.ACC_PROTECTED) && !testBit(
        currentAccess, Opcodes.ACC_FINAL)) {
      return true;
    }
    // Otherwise not visible
    return false;
  }

  @Override
  public void visitEnd() {
    if ((currentAccess & Opcodes.ACC_PUBLIC) != 0) {
      classSignatures.put(currentName,
          new JavaClassSignature(Util.convertAccess(currentAccess, Util.classFlags),
              currentMethods));
    }
  }

  public Map<String, JavaClassSignature> getClassSignatures() {
    return classSignatures;
  }
}
