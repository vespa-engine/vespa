package com.yahoo.abicheck.mojo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yahoo.abicheck.Public;
import com.yahoo.abicheck.classtree.ClassFileTree;
import com.yahoo.abicheck.signature.JavaClassSignature;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
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
}
