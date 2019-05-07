// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Submission;
import com.yahoo.config.provision.ApplicationId;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Submits a Vespa application package and corresponding test jars to the hosted Vespa API.
 *
 * @author jonmv
 */
@Mojo(name = "submit")
public class SubmitMojo extends AbstractVespaMojo {

    @Parameter(property = "applicationZip")
    private String applicationZip;

    @Parameter(property = "applicationTestZip")
    private String applicationTestZip;

    @Parameter(property = "authorEmail", required = true)
    private String authorEmail;

    @Parameter(property = "repository", defaultValue = "unknown")
    private String repository;

    @Parameter(property = "branch", defaultValue = "unknown")
    private String branch;

    @Parameter(property = "commit", defaultValue = "unknown")
    private String commit;

    @Override
    public void doExecute() {
        applicationZip = firstNonBlank(applicationZip, projectPathOf("target", "application.zip"));
        applicationTestZip = firstNonBlank(applicationTestZip, projectPathOf("target", "application-test.zip"));
        Submission submission = new Submission(repository, branch, commit, authorEmail,
                                               Paths.get(applicationZip),
                                               Paths.get(applicationTestZip));

        System.out.println(controller.submit(submission, id.tenant(), id.application()));
    }

}
