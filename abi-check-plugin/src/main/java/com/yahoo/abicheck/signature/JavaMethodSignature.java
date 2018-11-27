package com.yahoo.abicheck.signature;

import java.util.List;

public class JavaMethodSignature {

  public final List<String> attributes;
  public final String returnType;

  public <T> JavaMethodSignature(List<String> attributes, String returnType) {
    this.attributes = attributes;
    this.returnType = returnType;
  }
}
