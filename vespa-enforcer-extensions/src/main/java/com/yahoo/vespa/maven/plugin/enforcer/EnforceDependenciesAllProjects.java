// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.maven.plugin.enforcer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author bjorncs
 */
public class EnforceDependenciesAllProjects implements EnforcerRule {

    private static final String WRITE_SPEC_PROP = "dependencyEnforcer.writeSpec";
    private static final String NON_TEST_HEADER = "#[non-test]";
    private static final String TEST_ONLY_HEADER = "#[test-only]";

    private String rootProjectId;
    private String specFile;
    private List<String> ignored = List.of();
    private List<String> testUtilProjects = List.of();

    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        Log log = helper.getLog();
        Dependencies deps = getDependenciesOfAllProjects(helper, ignored, testUtilProjects, rootProjectId);
        log.info("Found %d unique dependencies (%d non-test, %d test only)".formatted(
                deps.nonTest().size() + deps.testOnly().size(), deps.nonTest().size(), deps.testOnly().size()));
        Path specFile = resolveSpecFile(helper, this.specFile);
        if (System.getProperties().containsKey(WRITE_SPEC_PROP)) {
            writeDependencySpec(specFile, deps);
            log.info("Updated spec file '%s'".formatted(specFile.toString()));
        } else {
            validateDependencies(deps, specFile, aggregatorPomRoot(helper), projectName(helper));
        }
        log.info("The dependency enforcer completed successfully");
    }

    // Config injection for rule configuration. Method names must match config XML elements.
    @SuppressWarnings("unused") public void setRootProjectId(String l) { this.rootProjectId = l; }
    @SuppressWarnings("unused") public String getRootProjectId() { return rootProjectId; }
    @SuppressWarnings("unused") public void setSpecFile(String f) { this.specFile = f; }
    @SuppressWarnings("unused") public String getSpecFile() { return specFile; }
    @SuppressWarnings("unused") public void setIgnored(List<String> l) { this.ignored = l; }
    @SuppressWarnings("unused") public List<String> getIgnored() { return ignored; }
    @SuppressWarnings("unused") public void setTestUtilProjects(List<String> l) { this.testUtilProjects = l; }
    @SuppressWarnings("unused") public List<String> getTestUtilProjects() { return testUtilProjects; }

    record Dependency(String groupId, String artifactId, String version, Optional<String> classifier)
            implements Comparable<Dependency> {
        static Dependency fromArtifact(Artifact a) {
            return new Dependency(
                    a.getGroupId(), a.getArtifactId(), a.getVersion(), Optional.ofNullable(a.getClassifier()));
        }

        static Dependency fromString(String s) {
            String[] splits = s.split(":");
            return splits.length == 3
                    ? new Dependency(splits[0], splits[1], splits[2], Optional.empty())
                    : new Dependency(splits[0], splits[1], splits[2], Optional.of(splits[3]));
        }

        String asString() {
            var b = new StringBuilder(groupId).append(':').append(artifactId).append(':').append(version);
            classifier.ifPresent(c -> b.append(':').append(c));
            return b.toString();
        }

        static final Comparator<Dependency> COMPARATOR = Comparator.comparing(Dependency::groupId)
                .thenComparing(Dependency::artifactId).thenComparing(Dependency::version)
                .thenComparing(d -> d.classifier().orElse(""));
        @Override public int compareTo(Dependency o) { return COMPARATOR.compare(this, o); }
    }

    record Dependencies(SortedSet<Dependency> nonTest, SortedSet<Dependency> testOnly) {}

    static void validateDependencies(Dependencies dependencies, Path specFile, Path aggregatorPomRoot,
                                     String moduleName)
            throws EnforcerRuleException {
        Dependencies allowedDependencies = loadDependencySpec(specFile);
        if (!allowedDependencies.equals(dependencies)) {
            StringBuilder errorMsg = new StringBuilder("The dependency enforcer failed:\n");
            generateDiff(errorMsg, "non-test", dependencies.nonTest(), allowedDependencies.nonTest());
            generateDiff(errorMsg, "test-only", dependencies.testOnly(), allowedDependencies.testOnly());
            throw new EnforcerRuleException(
                    errorMsg.append("Maven dependency validation failed. ")
                            .append("If this change was intentional, update the dependency spec by running:\n")
                            .append("$ mvn validate -D").append(WRITE_SPEC_PROP).append(" -pl :").append(moduleName)
                            .append(" -f ").append(aggregatorPomRoot).append("\n").toString());
        }
    }

    static void generateDiff(
            StringBuilder errorMsg, String label, SortedSet<Dependency> actual, SortedSet<Dependency> expected) {
        SortedSet<Dependency> forbidden = new TreeSet<>(actual);
        forbidden.removeAll(expected);
        SortedSet<Dependency> removed = new TreeSet<>(expected);
        removed.removeAll(actual);
        if (!forbidden.isEmpty()) {
            errorMsg.append("Forbidden ").append(label).append(" dependencies:\n");
            forbidden.forEach(d -> errorMsg.append(" - ").append(d.asString()).append('\n'));
        }
        if (!removed.isEmpty()) {
            errorMsg.append("Removed ").append(label).append(" dependencies:\n");
            removed.forEach(d -> errorMsg.append(" - ").append(d.asString()).append('\n'));
        }
    }

    private static Dependencies getDependenciesOfAllProjects(EnforcerRuleHelper helper, List<String> ignored,
                                                             List<String> testUtilProjects, String rootProjectId)
            throws EnforcerRuleException {
        try {
            Pattern depIgnorePattern = Pattern.compile(
                    ignored.stream()
                            .map(s -> s.replace(".", "\\.").replace("*", ".*").replace(":", "\\:").replace('?', '.'))
                            .collect(Collectors.joining(")|(", "^(", ")$")));
            Pattern projectIgnorePattern = Pattern.compile(
                    testUtilProjects.stream()
                            .map(s -> s.replace(".", "\\.").replace("*", ".*").replace(":", "\\:").replace('?', '.'))
                            .collect(Collectors.joining(")|(", "^(", ")$")));
            SortedSet<Dependency> nonTestDeps = new TreeSet<>();
            SortedSet<Dependency> testDeps = new TreeSet<>();
            MavenSession session = mavenSession(helper);
            var graphBuilder = helper.getComponent(DependencyGraphBuilder.class);
            List<MavenProject> projects = getAllProjects(session, rootProjectId);
            for (MavenProject project : projects) {
                var req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                req.setProject(project);
                DependencyNode root = graphBuilder.buildDependencyGraph(req, null);
                String projectId = projectIdOf(project);
                boolean overrideToTest = projectIgnorePattern.matcher(projectId).matches();
                if (overrideToTest) helper.getLog().info("Treating dependencies of '%s' as 'test'".formatted(projectId));
                addDependenciesRecursive(root, nonTestDeps, testDeps, depIgnorePattern, overrideToTest);
            }
            testDeps.removeAll(nonTestDeps);
            return new Dependencies(nonTestDeps, testDeps);
        } catch (DependencyGraphBuilderException | ComponentLookupException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static String projectIdOf(MavenProject project) { return "%s:%s".formatted(project.getGroupId(), project.getArtifactId()); }

    /** Only return the projects we'd like to enforce dependencies for: the root project, its modules, their modules, etc. */
    private static List<MavenProject> getAllProjects(MavenSession session, String rootProjectId) throws EnforcerRuleException {
        if (rootProjectId == null) throw new EnforcerRuleException("Missing required <rootProjectId> in <enforceDependencies> in pom.xml");

        List<MavenProject> allProjects = session.getAllProjects();
        if (allProjects.size() == 1) {
            throw new EnforcerRuleException(
                    "Only a single Maven module detected. Enforcer must be executed from root of aggregator pom.");
        }
        MavenProject rootProject = allProjects
                .stream()
                .filter(project -> rootProjectId.equals(projectIdOf(project)))
                .findAny()
                .orElseThrow(() -> new EnforcerRuleException("Root project not found: " + rootProjectId));

        Map<Path, MavenProject> projectsByBaseDir = allProjects
                .stream()
                .collect(Collectors.toMap(project -> project.getBasedir().toPath().normalize(), project -> project));

        var projects = new ArrayList<MavenProject>();

        var pendingProjects = new ArrayDeque<MavenProject>();
        pendingProjects.add(rootProject);

        while (!pendingProjects.isEmpty()) {
            MavenProject project = pendingProjects.pop();
            projects.add(project);

            for (var module : project.getModules()) {
                // Assumption: The module is a relative path to a project base directory.
                Path moduleBaseDir = project.getBasedir().toPath().resolve(module).normalize();
                MavenProject moduleProject = projectsByBaseDir.get(moduleBaseDir);
                if (moduleProject == null)
                    throw new EnforcerRuleException("Failed to find module '" + module + "' in project " + project.getBasedir());
                pendingProjects.add(moduleProject);
            }
        }

        projects.sort(Comparator.comparing(EnforceDependenciesAllProjects::projectIdOf));
        return projects;
    }

    private static void addDependenciesRecursive(
            DependencyNode node, Set<Dependency> nonTestDeps, Set<Dependency> testDeps, Pattern ignored,
            boolean overrideToTest) {
        if (node.getChildren() != null) {
            for (DependencyNode dep : node.getChildren()) {
                Artifact a = dep.getArtifact();
                Dependency dependency = Dependency.fromArtifact(a);
                if (!ignored.matcher(dependency.asString()).matches()) {
                    if (a.getScope().equals("test") || overrideToTest) {
                        testDeps.add(dependency);
                    } else {
                        nonTestDeps.add(dependency);
                    }
                }
                addDependenciesRecursive(dep, nonTestDeps, testDeps, ignored, overrideToTest);
            }
        }
    }

    private static Path resolveSpecFile(EnforcerRuleHelper helper, String specFile) {
        return Paths.get(mavenProject(helper).getBasedir() + File.separator + specFile).normalize();
    }

    private static String projectName(EnforcerRuleHelper helper) { return mavenProject(helper).getArtifactId(); }

    private static Path aggregatorPomRoot(EnforcerRuleHelper helper) {
        return mavenSession(helper).getRequest().getPom().toPath();
    }

    private static MavenProject mavenProject(EnforcerRuleHelper helper) {
        try {
            return (MavenProject) helper.evaluate("${project}");
        } catch (ExpressionEvaluationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static MavenSession mavenSession(EnforcerRuleHelper helper) {
        try {
            return (MavenSession) helper.evaluate("${session}");
        } catch (ExpressionEvaluationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static void writeDependencySpec(Path specFile, Dependencies dependencies) {
        try (var out = Files.newBufferedWriter(specFile)) {
            out.write("# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.\n\n");
            out.write(NON_TEST_HEADER); out.write('\n');
            out.write("# Contains dependencies that are not used exclusively in 'test' scope\n");
            for (Dependency d : dependencies.nonTest()) {
                out.write(d.asString()); out.write('\n');
            }
            out.write("\n"); out.write(TEST_ONLY_HEADER); out.write('\n');
            out.write("# Contains dependencies that are used exclusively in 'test' scope\n");
            for (Dependency d : dependencies.testOnly()) {
                out.write(d.asString()); out.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Dependencies loadDependencySpec(Path specFile) {
        try {
            List<String> lines;
            try (Stream<String> s = Files.lines(specFile)) {
                lines = s.map(String::trim).filter(l -> !l.isEmpty()).toList();
            }
            SortedSet<Dependency> nonTest = parseDependencies(lines.stream().takeWhile(l -> !l.equals(TEST_ONLY_HEADER)));
            SortedSet<Dependency> testOnly = parseDependencies(lines.stream().dropWhile(l -> !l.equals(TEST_ONLY_HEADER)));
            return new Dependencies(nonTest, testOnly);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SortedSet<Dependency> parseDependencies(Stream<String> lines) {
        return lines.filter(l -> !l.startsWith("#")).map(Dependency::fromString)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    // Mark rule as not cachable
    @Override public boolean isCacheable() { return false; }
    @Override public boolean isResultValid(EnforcerRule r) { return false; }
    @Override public String getCacheId() { return ""; }

}
