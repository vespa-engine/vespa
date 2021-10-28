// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.mojo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.yahoo.abicheck.Public;
import com.yahoo.abicheck.classtree.ClassFileTree;
import com.yahoo.abicheck.signature.JavaClassSignature;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import root.Root;
import root.sub.Sub;

public class AbiCheckTest {

  private static ClassFileTree.Package buildClassFileTree() throws IOException {
    ClassFileTree.Package rootPkg = mock(ClassFileTree.Package.class);
    ClassFileTree.Package subPkg = mock(ClassFileTree.Package.class);

    ClassFileTree.ClassFile rootPkgInfoClass = mock(ClassFileTree.ClassFile.class);
    ClassFileTree.ClassFile rootPkgClass = mock(ClassFileTree.ClassFile.class);
    ClassFileTree.ClassFile subPkgClass = mock(ClassFileTree.ClassFile.class);

    when(rootPkg.getSubPackages()).thenReturn(Collections.singleton(subPkg));
    when(rootPkg.getClassFiles()).thenReturn(Arrays.asList(rootPkgClass, rootPkgInfoClass));
    when(subPkg.getClassFiles()).thenReturn(Collections.singleton(subPkgClass));

    when(rootPkgInfoClass.getName()).thenReturn("package-info.class");
    when(rootPkgInfoClass.getInputStream())
        .thenAnswer(invocation -> Root.class.getResourceAsStream("package-info.class"));

    when(rootPkgClass.getName()).thenReturn("Root.class");
    when(rootPkgClass.getInputStream())
        .thenAnswer(invocation -> Root.class.getResourceAsStream("Root.class"));

    when(subPkgClass.getName()).thenReturn("Sub.class");
    when(subPkgClass.getInputStream())
        .thenAnswer(invocation -> Sub.class.getResourceAsStream("Sub.class"));

    return rootPkg;
  }

  @Test
  public void testCollectPublicAbiSignatures() throws IOException {
    ClassFileTree.Package rootPkg = buildClassFileTree();

    Map<String, JavaClassSignature> signatures = AbiCheck
        .collectPublicAbiSignatures(rootPkg, Public.class.getCanonicalName());

    assertThat(signatures.size(), equalTo(1));
    JavaClassSignature rootSignature = signatures.get("root.Root");

    // PublicSignatureCollectorTest verifies actual signatures, no need to duplicate here
  }

  @Test
  public void testCompareSignatures() {
    Log log = mock(Log.class);

    JavaClassSignature signatureA = new JavaClassSignature(
        "java.lang.Object",
        Collections.emptySet(),
        Collections.singletonList("public"),
        Collections.singleton("public void foo()"),
        Collections.singleton("public int bar"));
    JavaClassSignature signatureB = new JavaClassSignature(
        "java.lang.Exception",
        Collections.singleton("java.lang.Runnable"),
        Collections.singletonList("protected"),
        Collections.singleton("public void foo(int)"),
        Collections.singleton("public boolean bar"));

    Map<String, JavaClassSignature> expected = ImmutableMap.<String, JavaClassSignature>builder()
        .put("test.Missing", signatureA)
        .put("test.A", signatureA)
        .put("test.B", signatureB)
        .build();

    Map<String, JavaClassSignature> actual = ImmutableMap.<String, JavaClassSignature>builder()
        .put("test.A", signatureA)
        .put("test.Extra", signatureA)
        .put("test.B", signatureA)
        .build();

    assertThat(AbiCheck.compareSignatures(expected, actual, log), equalTo(false));

    verify(log).error("Missing class: test.Missing");
    verify(log).error("Extra class: test.Extra");
    verify(log)
        .error("Class test.B: Expected superclass java.lang.Exception, found java.lang.Object");
    verify(log).error("Class test.B: Missing interface java.lang.Runnable");
    verify(log).error("Class test.B: Missing attribute protected");
    verify(log).error("Class test.B: Extra attribute public");
    verify(log).error("Class test.B: Missing method public void foo(int)");
    verify(log).error("Class test.B: Extra method public void foo()");
    verify(log).error("Class test.B: Missing field public boolean bar");
    verify(log).error("Class test.B: Extra field public int bar");
  }
}
