// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.collector;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

public class Util {

  public static final List<AccessFlag> classFlags = List.of(
      AccessFlag.make(Opcodes.ACC_PUBLIC, "public"),
      AccessFlag.make(Opcodes.ACC_PRIVATE, "private"),
      AccessFlag.make(Opcodes.ACC_PROTECTED, "protected"),
      AccessFlag.make(Opcodes.ACC_FINAL, "final"),
      AccessFlag.ignored(Opcodes.ACC_SUPER), // Ignored, always set by modern Java
      AccessFlag.make(Opcodes.ACC_INTERFACE, "interface"),
      AccessFlag.make(Opcodes.ACC_ABSTRACT, "abstract"),
      AccessFlag.make(Opcodes.ACC_SYNTHETIC, "synthetic"), // FIXME: Do we want this?
      AccessFlag.make(Opcodes.ACC_ANNOTATION, "annotation"),
      AccessFlag.make(Opcodes.ACC_ENUM, "enum"),
      AccessFlag.make(Opcodes.ACC_RECORD, "record"),
// FIXME: Module support
//      AccessFlag.make(Opcodes.ACC_MODULE, "module")
      AccessFlag.ignored(Opcodes.ACC_DEPRECATED)
  );

  public static final List<AccessFlag> methodFlags = List.of(
      AccessFlag.make(Opcodes.ACC_PUBLIC, "public"),
      AccessFlag.make(Opcodes.ACC_PRIVATE, "private"),
      AccessFlag.make(Opcodes.ACC_PROTECTED, "protected"),
      AccessFlag.make(Opcodes.ACC_STATIC, "static"),
      AccessFlag.make(Opcodes.ACC_FINAL, "final"),
      AccessFlag.make(Opcodes.ACC_SYNCHRONIZED, "synchronized"),
      AccessFlag.make(Opcodes.ACC_BRIDGE, "bridge"),
      AccessFlag.make(Opcodes.ACC_VARARGS, "varargs"), // FIXME: Do we want this?
      AccessFlag.make(Opcodes.ACC_NATIVE, "native"),
      AccessFlag.make(Opcodes.ACC_ABSTRACT, "abstract"),
      AccessFlag.make(Opcodes.ACC_STRICT, "strict"), // FIXME: Do we want this?
      AccessFlag.make(Opcodes.ACC_SYNTHETIC, "synthetic"), // FIXME: Do we want this?
      AccessFlag.ignored(Opcodes.ACC_DEPRECATED)
  );

  public static final List<AccessFlag> fieldFlags = List.of(
      AccessFlag.make(Opcodes.ACC_PUBLIC, "public"),
      AccessFlag.make(Opcodes.ACC_PRIVATE, "private"),
      AccessFlag.make(Opcodes.ACC_PROTECTED, "protected"),
      AccessFlag.make(Opcodes.ACC_STATIC, "static"),
      AccessFlag.make(Opcodes.ACC_FINAL, "final"),
      AccessFlag.make(Opcodes.ACC_VOLATILE, "volatile"),
      AccessFlag.make(Opcodes.ACC_TRANSIENT, "transient"),
      AccessFlag.make(Opcodes.ACC_SYNTHETIC, "synthetic"), // FIXME: Do we want this?
      AccessFlag.make(Opcodes.ACC_ENUM, "enum"),
      AccessFlag.ignored(Opcodes.ACC_DEPRECATED)
  );

  public static List<String> convertAccess(int access, List<AccessFlag> flags) {
    List<String> result = new ArrayList<>();
    for (AccessFlag flag : flags) {
      if ((access & flag.bit) != 0 && flag.attribute != null) {
        result.add(flag.attribute);
      }
      access &= ~flag.bit;
    }
    if (access != 0) {
      throw new IllegalArgumentException(String.format("Unexpected access bits: 0x%x", access));
    }
    return result;
  }

  public static class AccessFlag {

    public final int bit;
    public final String attribute;

    private AccessFlag(int bit, String attribute) {
      this.bit = bit;
      this.attribute = attribute;
    }

    private static AccessFlag make(int bit, String attribute) {
      return new AccessFlag(bit, attribute);
    }

    private static AccessFlag ignored(int bit) {
      return new AccessFlag(bit, null);
    }
  }
}
