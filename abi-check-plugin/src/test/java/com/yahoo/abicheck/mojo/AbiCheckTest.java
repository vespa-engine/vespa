package com.yahoo.abicheck;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yahoo.abicheck.classtree.ClassFileTree;
import com.yahoo.abicheck.classtree.ClassFileTree.Package;
import com.yahoo.abicheck.mojo.AbiCheck;
import com.yahoo.abicheck.signature.JavaClassSignature;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AbiCheckTest {

  @Test
  public void testKukkuu() throws IOException {
    ClassFileTree.Package rootPkg = mock(ClassFileTree.Package.class);
    ClassFileTree.Package subPkg = mock(ClassFileTree.Package.class);

    ClassFileTree.ClassFile rootPkgInfoClass = mock(ClassFileTree.ClassFile.class);
    ClassFileTree.ClassFile rootPkgClass = mock(ClassFileTree.ClassFile.class);
    ClassFileTree.ClassFile subPkgClass = mock(ClassFileTree.ClassFile.class);

    when(rootPkg.getSubPackages()).thenReturn(Collections.singleton(subPkg));
    when(rootPkg.getClassFiles()).thenReturn(Arrays.asList(rootPkgClass, rootPkgInfoClass));
    when(subPkg.getClassFiles()).thenReturn(Collections.singleton(subPkgClass));

    when(rootPkgInfoClass.getName()).thenReturn("package-info.class");
    when(rootPkgClass.getName()).thenReturn("Root.class");
    when(subPkgClass.getName()).thenReturn("Sub.class");

    Map<String, JavaClassSignature> signatures = AbiCheck
        .collectPublicAbiSignatures(rootPkg, "com.yahoo.PublicApi");

//    assertThat(signatures);
  }

  private static Collection<Package> buildClassFileTree() {
    ClassFileTree.Package root = new ClassFileTree.Package(null, "com");
  }
}
