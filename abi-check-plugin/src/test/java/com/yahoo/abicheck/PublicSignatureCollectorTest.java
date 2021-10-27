// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import com.yahoo.abicheck.collector.PublicSignatureCollector;
import com.yahoo.abicheck.signature.JavaClassSignature;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

public class PublicSignatureCollectorTest {

  @Test
  public void testCollection() throws IOException {
    String testClassName = "com.yahoo.abicheck.PublicSignatureCollectorTest$TestClass";
    ClassReader r = new ClassReader(testClassName);
    PublicSignatureCollector collector = new PublicSignatureCollector();
    r.accept(collector, 0);

    Map<String, JavaClassSignature> signatures = collector.getClassSignatures();
    assertThat(signatures.size(), equalTo(1));
    JavaClassSignature s = signatures.get(testClassName);
    assertThat(s.superClass, equalTo("java.lang.Object"));
    assertThat(s.interfaces,
        containsInAnyOrder("java.lang.Runnable", "java.util.function.Function"));
    assertThat(s.attributes, contains("public"));
    assertThat(s.fields, containsInAnyOrder(
        "public static int staticPublicField",
        "protected static int staticProtectedField",
        "public boolean publicField",
        "protected boolean protectedField"));
    assertThat(s.methods, containsInAnyOrder(
        "public void <init>()",
        "public static java.lang.String staticPublicMethod()",
        "protected static java.lang.String staticProtectedMethod()",
        "public java.lang.Object publicMethod()",
        "protected java.lang.Object protectedMethod()",
        "public void run()",
        "public java.lang.Void apply(java.lang.Void)",
        "public bridge synthetic java.lang.Object apply(java.lang.Object)"
    ));
  }

  public static class TestClass implements Runnable, Function<Void, Void> {

    public static int staticPublicField = 1;
    protected static int staticProtectedField = 2;
    private static int staticPrivateField = 3;
    public boolean publicField = true;
    protected boolean protectedField = true;
    private boolean privateField = true;

    public static String staticPublicMethod() {
      return "";
    }

    protected static String staticProtectedMethod() {
      return "";
    }

    private static String staticPrivateMethod() {
      return "";
    }

    public Object publicMethod() {
      return null;
    }

    protected Object protectedMethod() {
      return null;
    }

    private Object privateMethod() {
      return null;
    }

    @Override
    public void run() {

    }

    @Override
    public Void apply(Void aVoid) {
      return null;
    }
  }
}
