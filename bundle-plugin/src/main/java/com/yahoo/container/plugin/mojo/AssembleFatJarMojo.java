package com.yahoo.container.plugin.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.shade.DefaultShader;
import org.apache.maven.plugins.shade.ShadeRequest;
import org.apache.maven.plugins.shade.filter.SimpleFilter;
import org.apache.maven.plugins.shade.mojo.ArchiveFilter;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Wrapper around maven-shade-plugin's {@link DefaultShader} for packaging Vespa fat jars for `$VESPA_HOME/lib/jars`.
 * The produced fat jar will add dependencies which are already installed in lib/jars to manifest's "Class-Path" instead of embedding.
 *
 * @author bjorncs
 */
@Mojo(name = "assemble-fat-jar", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class AssembleFatJarMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}")
    public MavenSession session;

    @Parameter(defaultValue = "${project}")
    public MavenProject project;

    @Component
    public DependencyGraphBuilder dependencyGraphBuilder;

    @Parameter(defaultValue = "${project.artifactId}-jar-with-dependencies")
    public String finalName;

    @Parameter(defaultValue = "com.yahoo.vespa:vespa-3party-jars")
    public String projectDefiningInstalledDependencies;

    @Parameter(defaultValue = "${project.build.directory}")
    public File outputDirectory;

    @Parameter
    public String mainClass;

    @Parameter
    public String[] excludes = new String[0];

    private final Set<String> defaultExcludes = Set.of(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/MANIFEST.MF",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "about.html",
            "module-info.class",
            "license/*",
            "**/package-info.class",
            "**/module-info.class");

    @Override
    public void execute() throws MojoExecutionException {
        var installedDependencies = resolveThirdPartyArtifactsInstalledInVespaHomeLibJars();
        var projectDependencies = new TreeSet<>(project.getArtifacts());
        File outputFile = outputFile();
        var archiveFilter = new ArchiveFilter() {
            @Override public String getArtifact() { return null; }
            @Override public Set<String> getIncludes() { return Set.of(); }
            @Override public Set<String> getExcludes() {
                var values = new TreeSet<>(defaultExcludes);
                values.addAll(List.of(excludes));
                return values;
            }
            @Override public boolean getExcludeDefaults() { return true; }
        };
        var jarsToShade = projectDependencies.stream()
                .filter(d -> !installedDependencies.contains(d) && !d.getType().equals("pom") && d.getScope().equals("compile"))
                .map(Artifact::getFile)
                .collect(Collectors.toCollection(TreeSet::new));
        jarsToShade.add(project.getArtifact().getFile());
        try {
            var classpath = generateClasspath(installedDependencies, projectDependencies);
            var req = new ShadeRequest();
            req.setJars(jarsToShade);
            req.setUberJar(outputFile);
            req.setFilters(List.of(new SimpleFilter(jarsToShade, archiveFilter)));
            req.setRelocators(List.of());
            req.setResourceTransformers(List.of(new ManifestWriter(classpath, mainClass)));
            req.setShadeSourcesContent(false);
            new DefaultShader().shade(req);
            Files.copy(outputFile.toPath(), finalFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private String generateClasspath(Set<Artifact> installedDependencies, SortedSet<Artifact> projectDependencies) {
        return projectDependencies.stream()
                .filter(installedDependencies::contains)
                .map(AssembleFatJarMojo::filename)
                .collect(Collectors.joining(" "));
    }

    private static String filename(Artifact a) {
        return a.getGroupId().equals("com.yahoo.vespa") ? "%s-jar-with-dependencies.jar".formatted(a.getArtifactId()) : a.getFile().getName();
    }

    private File outputFile() {
        var a = project.getArtifact();
        var name = project.getArtifactId() + "-" + a.getVersion() + "-shaded." + a.getArtifactHandler().getExtension();
        return new File(outputDirectory, name);
    }

    private File finalFile() {
        var name = finalName + "." + project.getArtifact().getArtifactHandler().getExtension();
        return new File(outputDirectory, name);
    }

    private SortedSet<Artifact> resolveThirdPartyArtifactsInstalledInVespaHomeLibJars() throws MojoExecutionException {
        try {
            var installedDepsProject = projectDefiningInstalledDependencies.split(":");
            var project = session.getAllProjects().stream()
                    .filter(p -> p.getGroupId().equals(installedDepsProject[0]) && p.getArtifactId().equals(installedDepsProject[1]))
                    .findAny().orElseThrow(() -> new IllegalStateException(
                            "Cannot find %s. Build from project root with 'mvn install -pl :%s'".formatted(projectDefiningInstalledDependencies, this.project.getArtifactId())));
            var req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            req.setProject(project);
            var root = dependencyGraphBuilder.buildDependencyGraph(req, null);
            return getAllRecursive(root);
        } catch (DependencyGraphBuilderException e) {
            throw new MojoExecutionException(e);
        }
    }

    private static SortedSet<Artifact> getAllRecursive(DependencyNode node) {
        SortedSet<Artifact> children = new TreeSet<>();
        if (node.getChildren() != null) {
            for (DependencyNode dep : node.getChildren()) {
                var a = dep.getArtifact();
                if (!a.getType().equals("pom")) children.add(a);
                children.addAll(getAllRecursive(dep));
            }
        }
        return children;
    }

    private static class ManifestWriter implements ResourceTransformer {

        private final String classpath;
        private final String mainclass;

        ManifestWriter(String classpath, String mainclass) {
            this.classpath = classpath;
            this.mainclass = mainclass;
        }

        @Override public boolean canTransformResource(String resource) { return false; }
        @SuppressWarnings("deprecation") @Override public void processResource(String resource, InputStream is, List<Relocator> relocators) {}
        @Override public boolean hasTransformedResource() { return true; }

        @Override
        public void modifyOutputStream(JarOutputStream os) throws IOException {
            var manifest = new Manifest();
            var attributes = manifest.getMainAttributes();
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.putValue("Class-Path", classpath);
            attributes.putValue("Created-By", "vespa container maven plugin");
            if (mainclass != null) attributes.putValue("Main-Class", mainclass);
            var entry = new JarEntry(JarFile.MANIFEST_NAME);
            entry.setTime(System.currentTimeMillis());
            os.putNextEntry(entry);
            var baos = new ByteArrayOutputStream();
            manifest.write(baos);
            os.write(baos.toByteArray());
            os.flush();
        }
    }

}
