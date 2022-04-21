// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.Submission;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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

    @Parameter(property = "authorEmail")
    private String authorEmail;

    @Parameter(property = "repository")
    private String repository;

    @Parameter(property = "branch")
    private String branch;

    @Parameter(property = "commit")
    private String commit;

    @Parameter(property = "sourceUrl")
    private String sourceUrl;

    @Parameter(property = "projectId")
    private String projectId;

    @Parameter(property = "risk")
    private String risk;

    @Parameter(property = "description")
    private String description;

    @Override
    public void doExecute() {
        applicationZip = firstNonBlank(applicationZip, projectPathOf("target", "application.zip")).orElseThrow();
        applicationTestZip = firstNonBlank(applicationTestZip, projectPathOf("target", "application-test.zip")).orElseThrow();
        Submission submission = new Submission(optionalOf(repository), optionalOf(branch), optionalOf(commit),
                                               optionalOf(sourceUrl), optionalOf(authorEmail),
                                               Paths.get(applicationZip), Paths.get(applicationTestZip),
                                               optionalOf(projectId, Long::parseLong), optionalOf(risk, Integer::parseInt),
                                               optionalOf(description));

        getLog().info(controller.submit(submission, id.tenant(), id.application()));
    }

}
