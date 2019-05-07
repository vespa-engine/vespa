package ai.vespa.hosted.api;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A deployment intended for hosted Vespa, containing an application package and some meta data.
 */
public class Deployment {

    // Deployment options
    private final Optional<String> version;
    private final boolean ignoreValidationErrors;

    // Provide an application package ...
    private final Optional<Path> applicationZip;

    // ... or reference a previously submitted one.
    private final Optional<String> repository;
    private final Optional<String> branch;
    private final Optional<String> commit;
    private final OptionalLong build;

    private Deployment(Optional<String> version, boolean ignoreValidationErrors, Optional<Path> applicationZip,
                      Optional<String> repository, Optional<String> branch, Optional<String> commit, OptionalLong build) {
        this.version = version;
        this.ignoreValidationErrors = ignoreValidationErrors;
        this.applicationZip = applicationZip;
        this.repository = repository;
        this.branch = branch;
        this.commit = commit;
        this.build = build;
    }


    /** Returns a deployment which will use the provided application package. */
    public static Deployment ofPackage(Path applicationZipFile) {
        return new Deployment(Optional.empty(), false, Optional.of(applicationZipFile),
                              Optional.empty(), Optional.empty(), Optional.empty(), OptionalLong.empty());
    }

    /** Returns a deployment which will use the previously submitted package with the given reference. */
    public static Deployment ofReference(String repository, String branch, String commit, long build) {
        return new Deployment(Optional.empty(), false, Optional.empty(),
                              Optional.of(repository), Optional.of(branch), Optional.of(commit), OptionalLong.of(build));
    }

    /** Returns a copy of this which will have the specified Vespa version on its nodes. */
    public Deployment atVersion(String vespaVersion) {
        return new Deployment(Optional.of(vespaVersion), ignoreValidationErrors, applicationZip, repository, branch, commit, build);
    }

    /** Returns a copy of this which will additionally ignore validation errors upon deployment. */
    public Deployment ignoringValidationErrors() {
        return new Deployment(version, true, applicationZip, repository, branch, commit, build);
    }

    public Optional<String> version() { return version; }
    public boolean ignoreValidationErrors() { return ignoreValidationErrors; }
    public Optional<Path> applicationZip() { return applicationZip; }
    public Optional<String> repository() { return repository; }
    public Optional<String> branch() { return branch; }
    public Optional<String> commit() { return commit; }
    public OptionalLong build() { return build; }

}
