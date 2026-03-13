// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.inject.Inject;

import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Calls the generate-sources phase in the container lifecycle defined in lifecycle.xml.
 *
 * @author Tony Vaagenes
 */
@Mojo(name = "generateSources", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateSourcesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected org.apache.maven.project.MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Inject
    private BuildPluginManager pluginManager;

    @Parameter
    protected String configGenVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String configGenVersion = getConfigGenVersion();
        getLog().debug("configGenVersion = " + configGenVersion);

        executeMojo(
                plugin(
                        groupId("com.yahoo.vespa"),
                        artifactId("config-class-plugin"),
                        version(releaseVersion(configGenVersion))),
                goal("config-gen"),
                configuration(
                        element(name("defFilesDirectories"), "src/main/resources/configdefinitions")),
                createExecutionEnvironment());
        //Compile source roots added in container-lifecycle is not currently
        //propagated automatically to this project.
        project.addCompileSourceRoot(project.getBuild().getDirectory() + "/generated-sources/vespa-configgen-plugin");
    }

    private ExecutionEnvironment createExecutionEnvironment() throws MojoExecutionException {
        return executionEnvironment(
                project,
                session,
                pluginManager);
    }

    private String getConfigGenVersion() throws MojoExecutionException {
        if (configGenVersion != null && !configGenVersion.isEmpty()) {
            return configGenVersion;
        }

        Dependency container = getVespaDependency("container");
        if (container != null)
            return container.getVersion();

        Dependency containerDev = getVespaDependency("container-dev");
        if (containerDev != null)
            return containerDev.getVersion();

        Dependency docproc = getVespaDependency("docproc");
        if (docproc != null)
            return docproc.getVersion();

        MavenProject parent = getVespaParent();
        if (parent != null)
            return parent.getVersion();

        String defaultConfigGenVersion = loadDefaultConfigGenVersion();
        getLog().warn(String.format(Locale.ROOT,
                "Did not find either container or container-dev artifact in project dependencies, "
                + "using default version '%s' of the config class plugin.",
                defaultConfigGenVersion));

        return defaultConfigGenVersion;
    }

    static String loadDefaultConfigGenVersion() throws MojoExecutionException {
        Properties props = new Properties();
        try {
            props.load(GenerateSourcesMojo.class.getResourceAsStream("/build.properties"));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to resolve version of com.yahoo.vespa:config-class-plugin.",
                                             new FileNotFoundException("/build.properties"));
        }
        return props.getProperty("projectVersion");
    }

    private MavenProject getVespaParent() {
        MavenProject parent = project.getParent();
        if (parent != null &&
                "com.yahoo.vespa".equals(parent.getGroupId()) &&
                "parent".equals(parent.getArtifactId())) {

            return parent;
        }

        return null;
    }

    private Dependency getVespaDependency(String artifactId) {
        for (Object element : project.getDependencies()) {
            Dependency dependency = (Dependency) element;

            if ("com.yahoo.vespa".equals(dependency.getGroupId()) &&
                    artifactId.equals(dependency.getArtifactId())) {
                return dependency;
            }
        }

        return null;
    }

    static String releaseVersion(String mavenVersion) {
        if (mavenVersion.endsWith("-SNAPSHOT")) {
            return mavenVersion;
        } else {
            String[] parts = mavenVersion.split(Pattern.quote("."));
            if (parts.length <= 3) {
                return mavenVersion;
            } else {
                return stringJoin(List.of(parts).subList(0, 3), ".");
            }
        }
    }

    static String stringJoin(Collection<String> elements, String sep) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> i = elements.iterator();

        if (i.hasNext())
            builder.append(i.next());

        while(i.hasNext()) {
            builder.append(sep).append(i.next());
        }

        return builder.toString();
    }
}
