// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.classtree;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class ClassFileTree implements AutoCloseable {

  public static ClassFileTree fromJar(JarFile jarFile) {
    Map<String, Package> rootPackages = new HashMap<>();

    Enumeration<JarEntry> jarEntries = jarFile.entries();
    while (jarEntries.hasMoreElements()) {
      JarEntry entry = jarEntries.nextElement();
      if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
        Deque<String> parts = new ArrayDeque<>(Arrays.asList(entry.getName().split("/")));
        String className = parts.removeLast();
        Package pkg = rootPackages
            .computeIfAbsent(parts.removeFirst(), name -> new Package(null, name));
        for (String part : parts) {
          pkg = pkg.getOrCreateSubPackage(part);
        }
        pkg.addClass(new ClassFile(pkg, className) {

          @Override
          public InputStream getInputStream() throws IOException {
            return jarFile.getInputStream(entry);
          }
        });
      }
    }

    return new ClassFileTree() {
      @Override
      public Collection<Package> getRootPackages() {
        return rootPackages.values();
      }

      @Override
      public void close() throws IOException {
        jarFile.close();
      }
    };
  }

  public abstract void close() throws IOException;

  public abstract Collection<Package> getRootPackages();

  public static abstract class ClassFile {

    private final Package parent;
    private final String name;

    private ClassFile(Package parent, String name) {
      this.parent = parent;
      this.name = name;
    }

    public abstract InputStream getInputStream() throws IOException;

    public String getName() {
      return name;
    }

    // CLOVER:OFF
    // Testing debug methods is not necessary
    @Override
    public String toString() {
      return "ClassFile(" + parent.getFullyQualifiedName() + "." + name + ")";
    }
    // CLOVER:ON
  }

  public static class Package {

    private final Package parent;
    private final String name;
    private final Map<String, Package> subPackages = new HashMap<>();
    private final Set<ClassFile> classFiles = new HashSet<>();

    private Package(Package parent, String name) {
      this.parent = parent;
      this.name = name;
    }

    private Package getOrCreateSubPackage(String name) {
      return subPackages.computeIfAbsent(name, n -> new Package(this, n));
    }

    private void addClass(ClassFile klazz) {
      classFiles.add(klazz);
    }

    public String getFullyQualifiedName() {
      if (parent == null) {
        return name;
      } else {
        return parent.getFullyQualifiedName() + "." + name;
      }
    }

    public Collection<Package> getSubPackages() {
      return subPackages.values();
    }

    public Collection<ClassFile> getClassFiles() {
      return classFiles;
    }

    // CLOVER:OFF
    // Testing debug methods is not necessary
    @Override
    public String toString() {
      return "Package(" + getFullyQualifiedName() + ")";
    }
    // CLOVER:ON
  }
}
