// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.signature;

import java.util.List;
import java.util.Set;

public class JavaClassSignature {

  public final String superClass;
  public final Set<String> interfaces;
  public final List<String> attributes;
  public final Set<String> methods;
  public final Set<String> fields;

  public JavaClassSignature(String superClass, Set<String> interfaces, List<String> attributes,
      Set<String> methods, Set<String> fields) {
    this.superClass = superClass;
    this.interfaces = interfaces;
    this.attributes = attributes;
    this.methods = methods;
    this.fields = fields;
  }
}
