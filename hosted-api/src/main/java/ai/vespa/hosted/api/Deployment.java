// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A deployment intended for hosted Vespa, containing an application package and some meta data.
 */
public class Deployment {

    private final Optional<String> version;
    private final Path applicationZip;

    private Deployment(Optional<String> version, Path applicationZip) {
        this.version = version;
        this.applicationZip = applicationZip;
    }

    /** Returns a deployment which will use the provided application package. */
    public static Deployment ofPackage(Path applicationZipFile) {
        return new Deployment(Optional.empty(), applicationZipFile);
    }

    /** Returns a copy of this which will have the specified Vespa version on its nodes. */
    public Deployment atVersion(String vespaVersion) {
        return new Deployment(Optional.of(vespaVersion), applicationZip);
    }

    public Optional<String> version() { return version; }
    public Path applicationZip() { return applicationZip; }

}
