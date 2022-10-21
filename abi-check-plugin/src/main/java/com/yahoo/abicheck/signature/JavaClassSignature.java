// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.signature;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaClassSignature {

  public final String superClass;
  public final Set<String> interfaces;
  public final List<String> attributes;
  public final Set<String> methods;
  public final Set<String> fields;

  public JavaClassSignature(@JsonProperty("superClass") String superClass,
                            @JsonProperty("interfaces") Set<String> interfaces,
                            @JsonProperty("attributes") List<String> attributes,
                            @JsonProperty("methods") Set<String> methods,
                            @JsonProperty("fields") Set<String> fields) {
    this.superClass = superClass;
    this.interfaces = interfaces;
    this.attributes = attributes;
    this.methods = methods;
    this.fields = fields;
  }

}
