// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.ControllerHttpClient;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.yolean.Exceptions;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Base class for hosted Vespa plugin mojos.
 *
 * @author jonmv
 */
public abstract class AbstractVespaMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(property = "endpoint", defaultValue = "https://api.vespa-external.aws.oath.cloud:4443")
    protected String endpoint;

    @Parameter(property = "tenant")
    protected String tenant;

    @Parameter(property = "application")
    protected String application;

    @Parameter(property = "instance")
    protected String instance;

    @Parameter(property = "tags")
    protected String tags;

    @Parameter(property = "apiKey")
    protected String apiKey;

    @Parameter(property = "apiKeyFile")
    protected String apiKeyFile;

    @Parameter(property = "apiCertificateFile")
    protected String apiCertificateFile;

    // Fields set up as part of setup().
    protected ApplicationId id;
    protected ControllerHttpClient controller;

    protected boolean requireInstance() { return false; }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            setup();
            doExecute();
        }
        catch (MojoFailureException | MojoExecutionException e) {
            throw e;
        }
        catch (Exception e) {
            String message = "Execution failed for application " + name() + ":\n" + Exceptions.toMessageString(e);
            if (e.getSuppressed().length > 0)
                message += "\nSuppressed:\n" + Stream.of(e.getSuppressed()).map(Exceptions::toMessageString).collect(joining("\n"));

            throw new MojoExecutionException(message, e);
        }
    }

    /** Override this in subclasses, instead of {@link #execute()}. */
    protected abstract void doExecute() throws Exception;

    /** Return the name of the relevant entity, e.g., application with or without instance. */
    protected String name() { return tenant + "." + application; }

    protected void setup() throws MojoExecutionException {
        tenant = firstNonBlank(tenant, project.getProperties().getProperty("tenant"))
                .orElseThrow(() -> new MojoExecutionException("'tenant' must be specified as a parameter or project property"));
        application = firstNonBlank(application, project.getProperties().getProperty("application"))
                .orElseThrow(() -> new MojoExecutionException("'application' must be specified as a parameter or project property"));
        instance = firstNonBlank(instance, project.getProperties().getProperty("instance"), requireInstance() ? null : InstanceName.defaultName().value())
                .orElseThrow(() -> new MojoExecutionException("'instance' must be specified as a parameter or project property"));
        id = ApplicationId.from(tenant, application, instance);

        Optional<Path> apiKeyPath = apiKeyPath(tenant);
        if ( ! isNullOrBlank(apiKey)) {
            controller = ControllerHttpClient.withSignatureKey(URI.create(endpoint), apiKey, id);
        }
        else if (apiKeyPath.isPresent()) {
            controller = isNullOrBlank(apiCertificateFile)
                    ? ControllerHttpClient.withSignatureKey(URI.create(endpoint), apiKeyPath.get(), id)
                    : ControllerHttpClient.withKeyAndCertificate(URI.create(endpoint), apiKeyPath.get(), Paths.get(apiCertificateFile));
        }
        else {
            throw new IllegalArgumentException("One of the properties 'apiKey' or 'apiKeyFile' is required.");
        }
    }

    private Optional<Path> apiKeyPath(String tenant) {
        if (!isNullOrBlank(apiKeyFile)) return Optional.of(Paths.get(apiKeyFile));

        Path cliApiKeyFile = Optional.ofNullable(System.getenv("VESPA_CLI_HOME"))
                                     .map(Paths::get)
                                     .orElseGet(() -> Paths.get(System.getProperty("user.home"), ".vespa"))
                                     .resolve(tenant + ".api-key.pem");
        if (Files.exists(cliApiKeyFile)) return Optional.of(cliApiKeyFile);

        return Optional.empty();
    }

    protected String projectPathOf(String first, String... rest) {
        return project.getBasedir().toPath().resolve(Path.of(first, rest)).toString();
    }

    /** Returns the first of the given strings which is non-null and non-blank, or throws IllegalArgumentException. */
    protected static Optional<String> firstNonBlank(String... values) {
        for (String value : values)
            if (value != null && ! value.isBlank())
                return Optional.of(value);

        return Optional.empty();
    }

    protected static Optional<String> optionalOf(String value) {
        return Optional.ofNullable(value)
                       .filter(data -> ! data.isBlank());
    }

    protected static <T> Optional<T> optionalOf(String value, Function<String, T> mapper) {
        return Optional.ofNullable(value)
                       .filter(data -> ! data.isBlank())
                       .map(mapper);
    }

    protected static boolean isNullOrBlank(String value) {
        return Optional.ofNullable(value)
                .filter(s -> !s.isBlank())
                .isEmpty();
    }

}
