// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.maven.plugin.enforcer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Enforces that all expected dependencies are present.
 * Fails by default for rules that do not match any dependencies.
 * Similar to the built-in 'bannedDependencies' rule in maven-enforcer-plugin.
 *
 * @author bjorncs
 */
public class EnforceDependencies implements EnforcerRule {

    private List<String> allowedDependencies = List.of();
    private boolean failOnUnmatched = true;

    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        validateDependencies(getAllDependencies(helper), Set.copyOf(allowedDependencies), failOnUnmatched);
        helper.getLog().info("The 'enforceDependencies' validation completed successfully");
    }

    static void validateDependencies(Set<Artifact> dependencies, Set<String> allowedRules, boolean failOnUnmatched)
            throws EnforcerRuleException {
        SortedSet<Artifact> unmatchedArtifacts = new TreeSet<>();
        Set<String> matchedRules = new HashSet<>();
        for (Artifact dependency : dependencies) {
            boolean matches = false;
            for (String rule : allowedRules) {
                if (matches(dependency, rule)){
                    matchedRules.add(rule);
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                unmatchedArtifacts.add(dependency);
            }
        }
        SortedSet<String> unmatchedRules = new TreeSet<>(allowedRules);
        unmatchedRules.removeAll(matchedRules);
        if (!unmatchedArtifacts.isEmpty() || (failOnUnmatched && !unmatchedRules.isEmpty())) {
            StringBuilder errorMessage = new StringBuilder("Vespa dependency enforcer failed:\n");
            if (!unmatchedArtifacts.isEmpty()) {
                errorMessage.append("Dependencies not matching any rule:\n");
                unmatchedArtifacts.forEach(a -> errorMessage.append(" - ").append(a.toString()).append('\n'));
            }
            if (failOnUnmatched && !unmatchedRules.isEmpty()) {
                errorMessage.append("Rules not matching any dependency:\n");
                unmatchedRules.forEach(p -> errorMessage.append(" - ").append(p).append('\n'));
            }
            throw new EnforcerRuleException(errorMessage.toString());
        }
    }

    private static Set<Artifact> getAllDependencies(EnforcerRuleHelper helper) throws EnforcerRuleException {
        try {
            MavenProject project = (MavenProject) helper.evaluate("${project}");
            MavenSession session = (MavenSession) helper.evaluate("${session}");
            DependencyGraphBuilder graphBuilder = helper.getComponent(DependencyGraphBuilder.class);
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(project);
            DependencyNode root = graphBuilder.buildDependencyGraph(buildingRequest, null);
            return getAllRecursive(root);
        } catch (ExpressionEvaluationException | DependencyGraphBuilderException | ComponentLookupException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    private static Set<Artifact> getAllRecursive(DependencyNode node) {
        Set<Artifact> children = new LinkedHashSet<>();
        if (node.getChildren() != null) {
            for (DependencyNode dep : node.getChildren()) {
                children.add(dep.getArtifact());
                children.addAll(getAllRecursive(dep));
            }
        }
        return children;
    }

    // Similar rule matching to bannedDependencies
    private static boolean matches(Artifact dependency, String rule) throws EnforcerRuleException {
        String[] segments = rule.split(":");
        if (segments.length < 1 || segments.length > 6) throw new EnforcerRuleException("Invalid rule: " + rule);
        if (!segmentMatches(dependency.getGroupId(), segments[0])) return false;
        if (segments.length > 1 && !segmentMatches(dependency.getArtifactId(), segments[1])) return false;
        if (segments.length > 2 && !versionMatches(dependency.getVersion(), segments[2])) return false;
        if (segments.length > 3 && !segmentMatches(dependency.getType(), segments[3])) return false;
        if (segments.length > 4 && !segmentMatches(dependency.getScope(), segments[4])) return false;
        if (segments.length > 5 && dependency.hasClassifier() && !segmentMatches(dependency.getClassifier(), segments[5]))
            return false;
        return true;
    }

    private static boolean segmentMatches(String value, String segmentPattern) {
        String regex = segmentPattern
                .replace(".", "\\.").replace("*", ".*").replace(":", "\\:").replace('?', '.')
                .replace("[", "\\[").replace("]", "\\]").replace("(", "\\(").replace(")", "\\)");
        return Pattern.matches(regex, value);
    }

    private static boolean versionMatches(String rawVersion, String segmentPattern) throws EnforcerRuleException {
        try {
            if (segmentMatches(rawVersion, segmentPattern)) return true;
            VersionRange allowedRange = VersionRange.createFromVersionSpec(segmentPattern);
            ArtifactVersion version = new DefaultArtifactVersion(rawVersion);
            ArtifactVersion recommended = allowedRange.getRecommendedVersion();
            if (recommended == null) return allowedRange.containsVersion(version);
            return recommended.compareTo(version) <= 0;
        } catch (InvalidVersionSpecificationException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        }
    }

    public void setAllowed(List<String> allowed) { this.allowedDependencies = allowed; }
    public List<String> getAllowed() { return allowedDependencies; }
    public void setFailOnUnmatchedRule(boolean enabled) { this.failOnUnmatched = enabled; }
    public boolean isFailOnUnmatchedRule() { return failOnUnmatched; }

    // Mark rule as not cachable
    @Override public boolean isCacheable() { return false; }
    @Override public boolean isResultValid(EnforcerRule enforcerRule) { return false; }
    @Override public String getCacheId() { return ""; }

}
