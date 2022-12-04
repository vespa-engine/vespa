// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.mojo;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yahoo.abicheck.classtree.ClassFileTree;
import com.yahoo.abicheck.classtree.ClassFileTree.ClassFile;
import com.yahoo.abicheck.classtree.ClassFileTree.Package;
import com.yahoo.abicheck.collector.AnnotationCollector;
import com.yahoo.abicheck.collector.PublicSignatureCollector;
import com.yahoo.abicheck.setmatcher.SetMatcher;
import com.yahoo.abicheck.signature.JavaClassSignature;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Mojo(
    name = "abicheck",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
    threadSafe = true
)
public class AbiCheck extends AbstractMojo {

  public static final String PACKAGE_INFO_CLASS_FILE_NAME = "package-info.class";
  private static final String DEFAULT_SPEC_FILE = "abi-spec.json";
  private static final String WRITE_SPEC_PROPERTY = "abicheck.writeSpec";

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project = null;

  @Parameter(required = true)
  private String publicApiAnnotation = null;

  @Parameter
  private String specFileName = DEFAULT_SPEC_FILE;

  // CLOVER:OFF
  // Testing that Gson can read JSON files is not very useful
  private static Map<String, JavaClassSignature> readSpec(File file) throws IOException {
    try (FileReader reader = new FileReader(file)) {
      ObjectMapper mapper = new ObjectMapper();
      JavaType typeToken = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, JavaClassSignature.class);
      return mapper.readValue(reader, typeToken);
    }
  }
  // CLOVER:ON

  // CLOVER:OFF
  // Testing that Gson can write JSON files is not very useful
  private static void writeSpec(Map<String, JavaClassSignature> signatures, File file)
      throws IOException {
    try (FileWriter writer = new FileWriter(file)) {
      new ObjectMapper().writer(new DefaultPrettyPrinter().withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE))
                        .writeValue(writer, signatures);
    }
  }

  // CLOVER:ON

  private static boolean matchingClasses(String className, JavaClassSignature expected,
      JavaClassSignature actual, Log log) {
    boolean match = true;
    if (!expected.superClass.equals(actual.superClass)) {
      match = false;
      log.error(String
          .format("Class %s: Expected superclass %s, found %s", className, expected.superClass,
              actual.superClass));
    }
    if (!SetMatcher.compare(expected.interfaces, actual.interfaces,
        item -> true,
        item -> log.error(String.format("Class %s: Missing interface %s", className, item)),
        item -> log.error(String.format("Class %s: Extra interface %s", className, item)))) {
      match = false;
    }
    if (!SetMatcher
        .compare(new HashSet<>(expected.attributes), new HashSet<>(actual.attributes),
            item -> true,
            item -> log.error(String.format("Class %s: Missing attribute %s", className, item)),
            item -> log.error(String.format("Class %s: Extra attribute %s", className, item)))) {
      match = false;
    }
    if (!SetMatcher.compare(expected.methods, actual.methods,
        item -> true,
        item -> log.error(String.format("Class %s: Missing method %s", className, item)),
        item -> log.error(String.format("Class %s: Extra method %s", className, item)))) {
      match = false;
    }
    if (!SetMatcher.compare(expected.fields, actual.fields,
        item -> true,
        item -> log.error(String.format("Class %s: Missing field %s", className, item)),
        item -> log.error(String.format("Class %s: Extra field %s", className, item)))) {
      match = false;
    }
    return match;
  }

  private static boolean isPublicAbiPackage(ClassFileTree.Package pkg, String publicApiAnnotation)
      throws IOException {
    Optional<ClassFile> pkgInfo = pkg.getClassFiles().stream()
        .filter(klazz -> klazz.getName().equals(PACKAGE_INFO_CLASS_FILE_NAME)).findFirst();
    if (!pkgInfo.isPresent()) {
      return false;
    }
    try (InputStream is = pkgInfo.get().getInputStream()) {
      AnnotationCollector visitor = new AnnotationCollector();
      new ClassReader(is).accept(visitor,
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return visitor.getAnnotations().contains(publicApiAnnotation);
    }
  }

  static Map<String, JavaClassSignature> collectPublicAbiSignatures(Package pkg,
      String publicApiAnnotation) throws IOException {
    Map<String, JavaClassSignature> signatures = new LinkedHashMap<>();
    if (isPublicAbiPackage(pkg, publicApiAnnotation)) {
      PublicSignatureCollector collector = new PublicSignatureCollector();
      List<ClassFileTree.ClassFile> sortedClassFiles = pkg.getClassFiles().stream()
          .sorted(Comparator.comparing(ClassFile::getName)).collect(Collectors.toList());
      for (ClassFile klazz : sortedClassFiles) {
        try (InputStream is = klazz.getInputStream()) {
          new ClassReader(is).accept(collector, 0);
        }
      }
      signatures.putAll(collector.getClassSignatures());
    }
    List<ClassFileTree.Package> sortedSubPackages = pkg.getSubPackages().stream()
        .sorted(Comparator.comparing(Package::getFullyQualifiedName))
        .collect(Collectors.toList());
    for (ClassFileTree.Package subPkg : sortedSubPackages) {
      signatures.putAll(collectPublicAbiSignatures(subPkg, publicApiAnnotation));
    }
    return signatures;
  }

  static boolean compareSignatures(Map<String, JavaClassSignature> expected,
      Map<String, JavaClassSignature> actual, Log log) {
    return SetMatcher.compare(expected.keySet(), actual.keySet(),
        item -> matchingClasses(item, expected.get(item), actual.get(item), log),
        item -> log.error(String.format("Missing class: %s", item)),
        item -> log.error(String.format("Extra class: %s", item)));
  }

  // CLOVER:OFF
  // The main entry point is tedious to unit test
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Artifact mainArtifact = project.getArtifact();
    File specFile = new File(project.getBasedir(), specFileName);
    if (mainArtifact.getFile() == null) {
      throw new MojoExecutionException("Missing project artifact file");
    } else if (!mainArtifact.getFile().getName().endsWith(".jar")) {
      throw new MojoExecutionException("Project artifact is not a JAR");
    }

    getLog().debug("Analyzing " + mainArtifact.getFile());


    try (JarFile jarFile = new JarFile(mainArtifact.getFile())) {
      ClassFileTree tree = ClassFileTree.fromJar(jarFile);
      Map<String, JavaClassSignature> signatures = new LinkedHashMap<>();
      for (ClassFileTree.Package pkg : tree.getRootPackages()) {
        signatures.putAll(collectPublicAbiSignatures(pkg, publicApiAnnotation));
      }
      if (System.getProperty(WRITE_SPEC_PROPERTY) != null) {
        getLog().info("Writing ABI specs to " + specFile.getPath());
        writeSpec(signatures, specFile);
      } else {
        Map<String, JavaClassSignature> abiSpec = readSpec(specFile);
        if (!compareSignatures(abiSpec, signatures, getLog())) {
          throw new MojoFailureException("ABI spec mismatch.\nTo update run 'mvn package -Dabicheck.writeSpec'");
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Error processing class signatures", e);
    }
  }
  // CLOVER:ON
}
