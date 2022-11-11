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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
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

    private String specFile;
    private List<String> ignored;

    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        Log log = helper.getLog();
        SortedSet<Dependency> dependencies = getDependenciesOfAllProjects(helper, ignored);
        log.info("Found %d unique dependencies".formatted(dependencies.size()));
        Path specFile = resolveSpecFile(helper, this.specFile);
        if (System.getProperties().containsKey(WRITE_SPEC_PROP)) {
            writeDependencySpec(specFile, dependencies);
            log.info("Updated spec file '%s'".formatted(specFile.toString()));
        } else {
            validateDependencies(dependencies, specFile);
        }
        log.info("The dependency enforcer completed successfully");
    }

    // Config injection for rule configuration. Method names must match config XML elements.
    @SuppressWarnings("unused") public void setSpecFile(String f) { this.specFile = f; }
    @SuppressWarnings("unused") public String getSpecFile() { return specFile; }
    @SuppressWarnings("unused") public void setIgnored(List<String> l) { this.ignored = l; }
    @SuppressWarnings("unused") public List<String> getIgnored() { return ignored; }

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

    static void validateDependencies(SortedSet<Dependency> dependencies, Path specFile)
            throws EnforcerRuleException {
        SortedSet<Dependency> allowedDependencies = loadDependencySpec(specFile);
        SortedSet<Dependency> forbiddenDependencies = new TreeSet<>(dependencies);
        forbiddenDependencies.removeAll(allowedDependencies);
        SortedSet<Dependency> removeDependencies = new TreeSet<>(allowedDependencies);
        removeDependencies.removeAll(dependencies);
        if (!forbiddenDependencies.isEmpty() || !removeDependencies.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("The dependency enforcer failed:\n");
            if (!forbiddenDependencies.isEmpty()) {
                errorMsg.append("Forbidden dependencies:\n");
                forbiddenDependencies.forEach(d -> errorMsg.append(" - ").append(d.asString()).append('\n'));
            }
            if (!removeDependencies.isEmpty()) {
                errorMsg.append("Removed dependencies:\n");
                removeDependencies.forEach(d -> errorMsg.append(" - ").append(d.asString()).append('\n'));
            }
            throw new EnforcerRuleException(
                    errorMsg.append("Maven dependency validation failed. To update dependency spec run " +
                                            "'mvn enforcer:enforce -D")
                            .append(WRITE_SPEC_PROP).append("'")
                            .toString());
        }
    }

    private static SortedSet<Dependency> getDependenciesOfAllProjects(EnforcerRuleHelper helper, List<String> ignored)
            throws EnforcerRuleException {
        try {
            Pattern ignorePattern = Pattern.compile(
                    ignored.stream()
                            .map(s -> s.replace(".", "\\.").replace("*", ".*").replace(":", "\\:").replace('?', '.'))
                            .collect(Collectors.joining(")|(", "^(", ")$")));
            SortedSet<Dependency> dependencies = new TreeSet<>();
            MavenSession session = (MavenSession) helper.evaluate("${session}");
            var graphBuilder = helper.getComponent(DependencyGraphBuilder.class);
            for (MavenProject project : session.getAllProjects()) {
                var req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                req.setProject(project);
                DependencyNode root = graphBuilder.buildDependencyGraph(req, null);
                addDependenciesRecursive(root, dependencies, ignorePattern);
            }
            return dependencies;
        } catch (ExpressionEvaluationException | DependencyGraphBuilderException | ComponentLookupException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    private static void addDependenciesRecursive(DependencyNode node, Set<Dependency> dependencies, Pattern ignored) {
        if (node.getChildren() != null) {
            for (DependencyNode dep : node.getChildren()) {
                Dependency dependency = Dependency.fromArtifact(dep.getArtifact());
                if (!ignored.matcher(dependency.asString()).matches()) {
                    dependencies.add(dependency);
                }
                addDependenciesRecursive(dep, dependencies, ignored);
            }
        }
    }

    private static Path resolveSpecFile(EnforcerRuleHelper helper, String specFile) throws EnforcerRuleException {
        try {
            MavenProject project = (MavenProject) helper.evaluate("${project}");
            return Paths.get(project.getBasedir() + File.separator + specFile).normalize();
        } catch (ExpressionEvaluationException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    static void writeDependencySpec(Path specFile, SortedSet<Dependency> dependencies)
            throws EnforcerRuleException {
        try (var out = Files.newBufferedWriter(specFile)) {
            out.write("# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.\n");
            for (Dependency d : dependencies) {
                out.write(d.asString());
                out.write('\n');
            }
        } catch (IOException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    private static SortedSet<Dependency> loadDependencySpec(Path specFile) throws EnforcerRuleException {
        try {
            try (Stream<String> s = Files.lines(specFile)) {
                return s.map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).map(Dependency::fromString)
                        .collect(Collectors.toCollection(TreeSet::new));
            }
        } catch (IOException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    // Mark rule as not cachable
    @Override public boolean isCacheable() { return false; }
    @Override public boolean isResultValid(EnforcerRule r) { return false; }
    @Override public String getCacheId() { return ""; }

}
