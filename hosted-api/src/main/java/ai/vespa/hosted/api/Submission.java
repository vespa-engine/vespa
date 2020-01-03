// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import java.nio.file.Path;
import java.util.OptionalLong;

/**
 * A submission intended for hosted Vespa, containing an application package with tests, and meta data.
 *
 * @author jonmv
 */
public class Submission {

    private final String repository;
    private final String branch;
    private final String commit;
    private final String authorEmail;
    private final Path applicationZip;
    private final Path applicationTestZip;
    private final OptionalLong projectId;

    public Submission(String repository, String branch, String commit, String authorEmail, Path applicationZip, Path applicationTestZip, OptionalLong projectId) {
        this.repository = repository;
        this.branch = branch;
        this.commit = commit;
        this.authorEmail = authorEmail;
        this.applicationZip = applicationZip;
        this.applicationTestZip = applicationTestZip;
        this.projectId = projectId;
    }

    public String repository() { return repository; }
    public String branch() { return branch; }
    public String commit() { return commit; }
    public String authorEmail() { return authorEmail; }
    public Path applicationZip() { return applicationZip; }
    public Path applicationTestZip() { return applicationTestZip; }
    public OptionalLong projectId() { return projectId; }

}
