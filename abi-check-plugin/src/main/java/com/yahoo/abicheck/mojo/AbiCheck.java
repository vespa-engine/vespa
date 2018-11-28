package com.yahoo.abicheck.mojo;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.yahoo.abicheck.classtree.ClassFileTree;
import com.yahoo.abicheck.classtree.ClassFileTree.ClassFile;
import com.yahoo.abicheck.classtree.ClassFileTree.Package;
import com.yahoo.abicheck.collector.AnnotationCollector;
import com.yahoo.abicheck.collector.PublicSignatureCollector;
import com.yahoo.abicheck.signature.JavaClassSignature;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;

@Mojo(
    name = "abicheck",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.RUNTIME
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

  private static String capitalizeFirst(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private static <T> boolean matchingItemSets(Set<T> expected, Set<T> actual,
      Predicate<T> itemsMatch, BiConsumer<T, String> onError) {
    boolean mismatch = false;
    Set<T> missing = Sets.difference(expected, actual);
    for (T name : missing) {
      mismatch = true;
      onError.accept(name, "missing");
    }
    Set<T> extra = Sets.difference(actual, expected);
    for (T name : extra) {
      mismatch = true;
      onError.accept(name, "extra");
    }
    Set<T> both = Sets.intersection(actual, expected);
    for (T name : both) {
      if (!itemsMatch.test(name)) {
        mismatch = true;
      }
    }
    return !mismatch;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Artifact mainArtifact = project.getArtifact();
    if (mainArtifact.getFile() == null) {
      throw new MojoExecutionException("Missing project artifact file");
    } else if (!mainArtifact.getType().equals("jar")) {
      throw new MojoExecutionException("Project artifact is not a JAR");
    }

    getLog().debug("Analyzing " + mainArtifact.getFile());

    try {
      ClassFileTree tree = ClassFileTree.fromJar(mainArtifact.getFile());
      Map<String, JavaClassSignature> signatures = new LinkedHashMap<>();
      for (ClassFileTree.Package pkg : tree.getRootPackages()) {
        signatures.putAll(collectPublicAbiSignatures(pkg));
      }
      if (System.getProperty(WRITE_SPEC_PROPERTY) != null) {
        getLog().info("Writing ABI specs to " + specFileName);
        writeSpec(signatures);
      } else {
        Gson gson = new GsonBuilder().create();
        try (FileReader reader = new FileReader(specFileName)) {
          TypeToken<Map<String, JavaClassSignature>> typeToken =
              new TypeToken<Map<String, JavaClassSignature>>() {
              };
          Map<String, JavaClassSignature> abiSpec = gson
              .fromJson(reader, typeToken.getType());
          if (!matchingItemSets(abiSpec.keySet(), signatures.keySet(),
              item -> matchingClasses(item, abiSpec.get(item), signatures.get(item)),
              (item, error) -> getLog()
                  .error(String.format("%s class: %s", capitalizeFirst(error), item)))) {
            throw new MojoFailureException("ABI spec mismatch");
          }
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Error processing class signatures", e);
    }
  }

  private void writeSpec(Map<String, JavaClassSignature> publicAbiSignatures) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try (FileWriter writer = new FileWriter(specFileName)) {
      gson.toJson(publicAbiSignatures, writer);
    }
  }

  private boolean matchingClasses(String className, JavaClassSignature expected,
      JavaClassSignature actual) {
    boolean match = true;
    if (!expected.superClass.equals(actual.superClass)) {
      match = false;
      getLog().error(String
          .format("Class %s: Expected superclass %s, found %s", className, expected.superClass,
              actual.superClass));
    }
    if (!matchingItemSets(expected.interfaces, actual.interfaces, item -> true,
        (item, error) -> getLog().error(
            String.format("Class %s: %s interface %s", className, capitalizeFirst(error), item)))) {
      if (!matchingItemSets(new HashSet<>(expected.attributes), new HashSet<>(actual.attributes),
          item -> true, (item, error) -> getLog().error(String
              .format("Class %s: %s attribute %s", className, capitalizeFirst(error), item)))) {
        match = false;
      }
    }
    if (!matchingItemSets(expected.methods, actual.methods, item -> true, (item, error) -> getLog()
        .error(String.format("Class %s: %s method %s", className, capitalizeFirst(error), item)))) {
      match = false;
    }
    if (!matchingItemSets(expected.fields, actual.fields, item -> true, (item, error) -> getLog()
        .error(String.format("Class %s: %s field %s", className, capitalizeFirst(error), item)))) {
      match = false;
    }
    return match;
  }

  private boolean isPublicAbiPackage(ClassFileTree.Package pkg) throws IOException {
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

  private Map<String, JavaClassSignature> collectPublicAbiSignatures(Package pkg)
      throws IOException {
    Map<String, JavaClassSignature> signatures = new LinkedHashMap<>();
    if (isPublicAbiPackage(pkg)) {
      PublicSignatureCollector collector = new PublicSignatureCollector();
      for (ClassFile klazz : pkg.getClassFiles()) {
        try (InputStream is = klazz.getInputStream()) {
          new ClassReader(is).accept(collector, 0);
        }
      }
      signatures.putAll(collector.getClassSignatures());
    }
    for (ClassFileTree.Package subPkg : pkg.getSubPackages()) {
      signatures.putAll(collectPublicAbiSignatures(subPkg));
    }
    return signatures;
  }
}
