// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Submission;
import com.yahoo.config.provision.ApplicationId;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Submits a Vespa application package and corresponding test jars to the hosted Vespa API.
 *
 * @author jonmv
 */
@Mojo(name = "submit")
public class SubmitMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "endpoint", defaultValue = "https://api.vespa.corp.yahoo.com:4443") // TODO jvenstad: Change default
    private String endpointUri;

    @Parameter(property = "tenant")
    private String tenant;

    @Parameter(property = "application")
    private String application;

    @Parameter(property = "instance")
    private String instance;

    @Parameter(property = "applicationZip")
    private String applicationZip;

    @Parameter(property = "applicationTestZip")
    private String applicationTestZip;

    @Parameter(property = "privateKeyFile", required = true)
    private String privateKeyFile;

    @Parameter(property = "authorEmail", required = true)
    private String authorEmail;

    @Parameter(property = "repository", defaultValue = "unknown")
    private String repository;

    @Parameter(property = "branch", defaultValue = "unknown")
    private String branch;

    @Parameter(property = "commit", defaultValue = "unknown")
    private String commit;

    @Override
    public void execute() {
        setup();
        ApplicationId id = ApplicationId.from(tenant, application, instance);
        ControllerHttpClient controller = ControllerHttpClient.withSignatureKey(URI.create(endpointUri),
                                                                                Paths.get(privateKeyFile),
                                                                                id);

        Submission submission = new Submission(repository, branch, commit, authorEmail,
                                               Paths.get(applicationZip), Paths.get(applicationTestZip));

        System.out.println(controller.submit(submission, id.tenant(), id.application()));
    }

    private void setup() {
        tenant = firstNonBlank(tenant, project.getProperties().getProperty("tenant"));
        application = firstNonBlank(application, project.getProperties().getProperty("application"));
        instance = firstNonBlank(instance, project.getProperties().getProperty("instance"));
        applicationZip = firstNonBlank(applicationZip, projectPathOf("target", "application.zip"));
        applicationTestZip = firstNonBlank(applicationTestZip, projectPathOf("target", "application-test.zip"));
    }

    private String projectPathOf(String first, String... rest) {
        return project.getBasedir().toPath().resolve(Path.of(first, rest)).toString();
    }

    /** Returns the first of the given strings which is non-null and non-blank, or throws IllegalArgumentException. */
    private static String firstNonBlank(String... values) {
        for (String value : values)
            if (value != null && ! value.isBlank())
                return value;

        throw new IllegalArgumentException("No valid value given");
    }

}
