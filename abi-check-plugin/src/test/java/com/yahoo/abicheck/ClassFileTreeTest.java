// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;
import com.yahoo.abicheck.classtree.ClassFileTree;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

public class ClassFileTreeTest {

  @Test
  public void testJarParsing() throws IOException {
    JarFile jarFile = mock(JarFile.class);
    JarEntry classJarEntry = new JarEntry("com/yahoo/Test.class");
    JarEntry dirJarEntry = new JarEntry("com/yahoo/");
    InputStream jarEntryStream = mock(InputStream.class);
    when(jarFile.entries())
        .thenReturn(Collections.enumeration(Arrays.asList(dirJarEntry, classJarEntry)));
    when(jarFile.getInputStream(classJarEntry)).thenReturn(jarEntryStream);

    try (ClassFileTree cft = ClassFileTree.fromJar(jarFile)) {
      ClassFileTree.Package com = Iterables.getOnlyElement(cft.getRootPackages());
      assertThat(com.getFullyQualifiedName(), equalTo("com"));
      assertThat(com.getClassFiles(), empty());
      ClassFileTree.Package yahoo = Iterables.getOnlyElement(com.getSubPackages());
      assertThat(yahoo.getFullyQualifiedName(), equalTo("com.yahoo"));
      assertThat(yahoo.getSubPackages(), empty());
      ClassFileTree.ClassFile testClassFile = Iterables.getOnlyElement(yahoo.getClassFiles());
      assertThat(testClassFile.getName(), equalTo("Test.class"));
      assertThat(testClassFile.getInputStream(), sameInstance(jarEntryStream));
    }
  }
}
