// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.collector;

import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class AnnotationCollector extends ClassVisitor {

  private final Set<String> annotations = new HashSet<>();

  public AnnotationCollector() {
    super(Opcodes.ASM9);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    annotations.add(Type.getType(descriptor).getClassName());
    return null;
  }

  public Set<String> getAnnotations() {
    return annotations;
  }
}
