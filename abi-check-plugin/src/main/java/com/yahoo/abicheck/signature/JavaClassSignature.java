package com.yahoo.abicheck.signature;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaClassSignature {

  public final List<String> attributes;
  public final Map<String, JavaMethodSignature> methods;

  public JavaClassSignature(List<String> attributes, Map<String, JavaMethodSignature> methods) {
    this.attributes = attributes;
    this.methods = methods;
  }
}
