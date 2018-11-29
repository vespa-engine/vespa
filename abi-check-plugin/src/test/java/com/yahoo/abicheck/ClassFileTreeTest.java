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
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

public class ClassFileTreeTest {

  @Test
  public void testJarParsing() throws IOException {
    JarFile jarFile = mock(JarFile.class);
    JarEntry jarEntry = new JarEntry("com/yahoo/Test.class");
    InputStream jarEntryStream = mock(InputStream.class);
    when(jarFile.entries()).thenReturn(Collections
        .enumeration(Collections.singleton(jarEntry)));
    when(jarFile.getInputStream(jarEntry)).thenReturn(jarEntryStream);

    ClassFileTree cft = ClassFileTree.fromJar(jarFile);
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
