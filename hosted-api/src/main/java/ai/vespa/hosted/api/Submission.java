// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A submission intended for hosted Vespa, containing an application package with tests, and meta data.
 *
 * @author jonmv
 */
public class Submission {

    private final Optional<String> repository;
    private final Optional<String> branch;
    private final Optional<String> commit;
    private final Optional<String> sourceUrl;
    private final Optional<String> authorEmail;
    private final Path applicationZip;
    private final Path applicationTestZip;
    private final Optional<Long> projectId;
    private final Optional<Integer> risk;
    private final Optional<String> description;

    public Submission(Optional<String> repository, Optional<String> branch, Optional<String> commit,
                      Optional<String> sourceUrl, Optional<String> authorEmail,
                      Path applicationZip, Path applicationTestZip, Optional<Long> projectId,
                      Optional<Integer> risk, Optional<String> description) {
        this.repository = repository;
        this.branch = branch;
        this.commit = commit;
        this.sourceUrl = sourceUrl;
        this.authorEmail = authorEmail;
        this.applicationZip = applicationZip;
        this.applicationTestZip = applicationTestZip;
        this.projectId = projectId;
        this.risk = risk;
        this.description = description;
    }

    public Optional<String> repository() { return repository; }
    public Optional<String> branch() { return branch; }
    public Optional<String> commit() { return commit; }
    public Optional<String> sourceUrl() { return sourceUrl; }
    public Optional<String> authorEmail() { return authorEmail; }
    public Path applicationZip() { return applicationZip; }
    public Path applicationTestZip() { return applicationTestZip; }
    public Optional<Long> projectId() { return projectId; }
    public Optional<Integer> risk() { return risk; }
    public Optional<String> description() { return description; }

}
