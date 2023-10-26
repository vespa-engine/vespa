// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.maven.plugin.enforcer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author bjorncs
 */
@Named("allowedDependencies")
@SuppressWarnings("deprecation")
public class AllowedDependencies extends AbstractEnforcerRule implements EnforcerRule {

    private static final String WRITE_SPEC_PROP = "dependencyEnforcer.writeSpec";
    private static final String GUESS_VERSION = "dependencyEnforcer.guessProperty";

    @Inject private MavenProject project;
    @Inject private MavenSession session;
    @Inject private DependencyGraphBuilder graphBuilder;

    // Injected parameters
    public List<String> ignored;
    public String rootProjectId;
    public String specFile;

    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        try {
            project = (MavenProject) helper.evaluate("${project}");
            session = (MavenSession) helper.evaluate("${session}");
            graphBuilder = helper.getComponent(DependencyGraphBuilder.class);
        } catch (ExpressionEvaluationException | ComponentLookupException e) {
            throw new RuntimeException(e);
        }
        execute();
    }

    public void execute() throws EnforcerRuleException {
        var dependencies = getDependenciesOfAllProjects();
        getLog().info("Found %d unique dependencies ".formatted(dependencies.size()));
        var specFile = Paths.get(project.getBasedir() + File.separator + this.specFile).normalize();
        var spec = loadDependencySpec(specFile);
        var resolved = resolve(spec, dependencies);
        if (System.getProperties().containsKey(WRITE_SPEC_PROP)) {
            // Guess property for version by default, can be disabled with <prop>=false
            var guessProperty = Optional.ofNullable(System.getProperty(GUESS_VERSION))
                    .map(p -> p.isEmpty() || Boolean.parseBoolean(p))
                    .orElse(true);
            writeDependencySpec(specFile, resolved, guessProperty);
            getLog().info("Updated spec file '%s'".formatted(specFile.toString()));
        } else {
            warnOnDuplicateVersions(resolved);
            validateDependencies(resolved, session.getRequest().getPom().toPath(), project.getArtifactId());
        }
        getLog().info("The dependency enforcer completed successfully");
    }

    private static void validateDependencies(Resolved resolved, Path aggregatorPomRoot, String moduleName)
            throws EnforcerRuleException {
        if (!resolved.unmatchedRules().isEmpty() || !resolved.unmatchedDeps().isEmpty()) {
            var errorMsg = new StringBuilder("The dependency enforcer failed:\n");
            if (!resolved.unmatchedRules().isEmpty()) {
                errorMsg.append("Rules not matching any dependency:\n");
                resolved.unmatchedRules().forEach(r -> errorMsg.append(" - ").append(r.asString()).append('\n'));
            }
            if (!resolved.unmatchedDeps().isEmpty()) {
                errorMsg.append("Dependencies not matching any rule:\n");
                resolved.unmatchedDeps().forEach(d -> errorMsg.append(" - ").append(d.asString(null)).append('\n'));
            }
            throw new EnforcerRuleException(
                    errorMsg.append("Maven dependency validation failed. ")
                            .append("If this change was intentional, update the dependency spec by running:\n")
                            .append("$ mvn validate -D").append(WRITE_SPEC_PROP).append(" -pl :").append(moduleName)
                            .append(" -f ").append(aggregatorPomRoot).append("\n").toString());
        }
    }

    private Set<Dependency> getDependenciesOfAllProjects() throws EnforcerRuleException {
        try {
            Pattern depIgnorePattern = Pattern.compile(
                    ignored.stream()
                            .map(s -> s.replace(".", "\\.").replace("*", ".*").replace(":", "\\:").replace('?', '.'))
                            .collect(Collectors.joining(")|(", "^(", ")$")));
            List<MavenProject> projects = getAllProjects(session, rootProjectId);
            Set<Dependency> dependencies = new HashSet<>();
            for (MavenProject project : projects) {
                var req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                req.setProject(project);
                var root = graphBuilder.buildDependencyGraph(req, null);
                addDependenciesRecursive(root, dependencies, depIgnorePattern);
            }
            return Set.copyOf(dependencies);
        } catch (DependencyGraphBuilderException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static void addDependenciesRecursive(DependencyNode node, Set<Dependency> dependencies, Pattern ignored) {
        if (node.getChildren() != null) {
            for (DependencyNode dep : node.getChildren()) {
                Artifact a = dep.getArtifact();
                Dependency dependency = Dependency.fromArtifact(a);
                if (!ignored.matcher(dependency.asString(null)).matches()) {
                    dependencies.add(dependency);
                }
                addDependenciesRecursive(dep, dependencies, ignored);
            }
        }
    }

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

        projects.sort(Comparator.comparing(AllowedDependencies::projectIdOf));
        return projects;
    }

    private List<Rule> loadDependencySpec(Path specFile) {
        try (Stream<String> s = Files.lines(specFile)) {
            return s.map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(Rule::fromString)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Resolved resolve(List<Rule> spec, Set<Dependency> dependencies) {
        var resolvedDeps = new HashSet<Dependency>();
        var resolveRules = new HashSet<Rule>();
        var unmatchedDeps = new HashSet<Dependency>();
        var unmatchedRules = new HashSet<Rule>();
        for (var rule : spec) {
            var requiredDependency = rule.resolveToDependency(project.getProperties());
            if (dependencies.contains(requiredDependency)) {
                resolvedDeps.add(requiredDependency);
                resolveRules.add(rule);
            } else {
                unmatchedRules.add(rule);
            }
        }
        for (var dependency : dependencies) {
            if (!resolvedDeps.contains(dependency)) {
                unmatchedDeps.add(dependency);
            }
        }
        return new Resolved(resolvedDeps, resolveRules, unmatchedDeps, unmatchedRules);
    }

    void writeDependencySpec(Path specFile, Resolved resolved, boolean guessVersion) {
        var content = new TreeSet<String>();
        resolved.matchedRules().forEach(r -> content.add(r.asString()));
        resolved.unmatchedDeps().forEach(d -> content.add(d.asString(guessVersion ? project.getProperties() : null)));
        try (var out = Files.newBufferedWriter(specFile)) {
            out.write("# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.\n\n");
            for (var line : content) {
                out.write(line); out.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void warnOnDuplicateVersions(Resolved resolved) {
        Map<String, Set<String>> versionsForDependency = new TreeMap<>();
        Set<Dependency> allDeps = new HashSet<>(resolved.matchedDeps());
        allDeps.addAll(resolved.unmatchedDeps());
        for (Dependency d : allDeps) {
            String id = "%s:%s".formatted(d.groupId(), d.artifactId());
            versionsForDependency.computeIfAbsent(id, __ -> new TreeSet<>()).add(d.version());
        }
        versionsForDependency.forEach((dependency, versions) -> {
            if (versions.size() > 1) {
                getLog().warn("'%s' has multiple versions %s".formatted(dependency, versions));
            }
        });
    }

    private static String projectIdOf(MavenProject project) { return "%s:%s".formatted(project.getGroupId(), project.getArtifactId()); }

    private record Rule(String groupId, String artifactId, String version, Optional<String> classifier){
        static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.+?)}");

        static Rule fromString(String s) {
            String[] splits = s.split(":");
            return splits.length == 3
                    ? new Rule(splits[0], splits[1], splits[2], Optional.empty())
                    : new Rule(splits[0], splits[1], splits[2], Optional.of(splits[3]));
        }

        Dependency resolveToDependency(Properties props) {
            // Replace expressions on form ${property} in 'version' field with value from properties
            var matcher = PROPERTY_PATTERN.matcher(version);
            var resolvedVersion = version;
            while (matcher.find()) {
                String property = matcher.group(1);
                String value = props.getProperty(property);
                if (value == null) throw new IllegalArgumentException("Missing property: " + property);
                resolvedVersion = version.replace(matcher.group(), value);
            }
            return new Dependency(groupId, artifactId, resolvedVersion, classifier);
        }

        String asString() {
            var b = new StringBuilder(groupId).append(':').append(artifactId).append(':').append(version);
            classifier.ifPresent(c -> b.append(':').append(c));
            return b.toString();
        }
    }

    record Dependency(String groupId, String artifactId, String version, Optional<String> classifier) {
        static Dependency fromArtifact(Artifact a) {
            return new Dependency(
                    a.getGroupId(), a.getArtifactId(), a.getVersion(), Optional.ofNullable(a.getClassifier()));
        }

        String asString(Properties props) {
            String versionStr = version;
            if (props != null) {
                // Guess property name if properties are provided
                var matchingProps = props.entrySet().stream()
                        .filter(e -> e.getValue().equals(version))
                        .map(v -> "${%s}".formatted(v.getKey()))
                        .collect(Collectors.joining("|"));
                if (!matchingProps.isEmpty()) versionStr = matchingProps;
            }
            var b = new StringBuilder(groupId).append(':').append(artifactId).append(':').append(versionStr);
            classifier.ifPresent(c -> b.append(':').append(c));
            return b.toString();
        }
    }

    record Resolved(Set<Dependency> matchedDeps, Set<Rule> matchedRules,
                    Set<Dependency> unmatchedDeps, Set<Rule> unmatchedRules) {}

    // Mark rule as not cachable
    @Override public boolean isCacheable() { return false; }
    @Override public boolean isResultValid(EnforcerRule r) { return false; }
    @Override public String getCacheId() { return ""; }
}
