// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A submission intended for hosted Vespa, containing an application package with tests, and metadata.
 *
 * @author Jon Marius Venstad
 */
public record Submission(Optional<String> repository, Optional<String> branch, Optional<String> commit,
                         Optional<String> sourceUrl, Optional<String> authorEmail, Path applicationZip,
                         Path applicationTestZip, Optional<Long> projectId, Optional<Integer> risk,
                         Optional<String> description, boolean skipTests) {

    public Submission(Optional<String> repository, Optional<String> branch, Optional<String> commit,
                      Optional<String> sourceUrl, Optional<String> authorEmail, Path applicationZip,
                      Path applicationTestZip, Optional<Long> projectId, Optional<Integer> risk,
                      Optional<String> description) {
        this(repository, branch, commit, sourceUrl, authorEmail, applicationZip, applicationTestZip, projectId, risk, description, false);
    }

    public Submission(Path applicationZip, Path applicationTestZip, Optional<Long> projectId) {
        this(applicationZip, applicationTestZip, projectId, false);
    }

    public Submission(Path applicationZip, Path applicationTestZip, Optional<Long> projectId, boolean skipTests) {
        this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
             applicationZip, applicationTestZip, projectId, Optional.empty(), Optional.empty(), skipTests);
    }
}
