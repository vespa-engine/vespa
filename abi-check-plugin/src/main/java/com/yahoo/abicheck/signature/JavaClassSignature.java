package com.yahoo.abicheck.signature;

import java.util.List;
import java.util.Set;

public class JavaClassSignature {

  public final List<String> attributes;
  public final Set<String> methods;

  public JavaClassSignature(List<String> attributes, Set<String> methods) {
    this.attributes = attributes;
    this.methods = methods;
  }
}
