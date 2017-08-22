// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.util.Objects;
import java.util.Optional;

/**
 * An identifier of a particular revision (exact content) of an application package,
 * optionally with information about the source of the package revision.
 * 
 * @author bratseth
 */
public class ApplicationRevision {

    private final String applicationPackageHash;
    
    private final Optional<SourceRevision> source;

    private ApplicationRevision(String applicationPackageHash, Optional<SourceRevision> source) {
        Objects.requireNonNull(applicationPackageHash, "applicationPackageHash cannot be null");
        this.applicationPackageHash = applicationPackageHash;
        this.source = source;
    }
    
    /** Create an application package revision where there is no information about its source */
    public static ApplicationRevision from(String applicationPackageHash) {
        return new ApplicationRevision(applicationPackageHash, Optional.empty());
    }

    /** Create an application package revision with a source */
    public static ApplicationRevision from(String applicationPackageHash, SourceRevision source) {
        return new ApplicationRevision(applicationPackageHash, Optional.of(source));
    }

    /** Returns a unique, content-based identifier of an application package (a hash of the content) */
    public String id() { return applicationPackageHash; }

    /** 
     * Returns information about the source of this revision, or empty if the source is not know/defined
     * (which is the case for command-line deployment from developers, but never for deployment jobs)
     */
    public Optional<SourceRevision> source() { return source; }
    
    @Override
    public int hashCode() { return applicationPackageHash.hashCode(); }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if ( ! (other instanceof ApplicationRevision)) return false;
        return this.applicationPackageHash.equals(((ApplicationRevision)other).applicationPackageHash);
    }
    
    @Override
    public String toString() {
        return "Application package revision '" + applicationPackageHash + "'" +
               (source.isPresent() ? " with " + source.get() : "");
    }

}
