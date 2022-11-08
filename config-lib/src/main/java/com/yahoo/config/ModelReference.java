// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable reference to a model.
 * This is a file path when read by a client but is set in a config instance either as a
 * path, url or id resolved to an url during deployment.
 *
 * @author bratseth
 */
public class ModelReference {

    // Either: If unresolved, at least one of these are set
    private final Optional<String> modelId;
    private final Optional<UrlReference> url;
    private final Optional<FileReference> path;

    // Or: If resolved, this is set
    private final Path resolved;

    private ModelReference(Optional<String> modelId,
                           Optional<UrlReference> url,
                           Optional<FileReference> path,
                           Path resolved) {
        this.modelId = modelId;
        this.url = url;
        this.path = path;
        this.resolved = resolved;
    }

    /** Returns whether this is already resolved. */
    public boolean isResolved() { return resolved != null; }

    /** Returns the id specified for this model, oor null if it is resolved. */
    public Optional<String> modelId() { return modelId; }

    /** Returns the url specified for this model, or null if it is resolved. */
    public Optional<UrlReference> url() { return url; }

    /** Returns the path specified for this model, or null if it is resolved. */
    public Optional<FileReference> path() { return path; }

    /** Returns the path to the file containing this model, or null if this is unresolved. */
    public Path value() { return resolved; }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof ModelReference other)) return false;
        if ( ! Objects.equals(this.modelId, other.modelId)) return false;
        if ( ! Objects.equals(this.url, other.url)) return false;
        if ( ! Objects.equals(this.path, other.path)) return false;
        if ( ! Objects.equals(this.resolved, other.resolved)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, url, path, resolved);
    }

    /** Returns this on the format accepted by valueOf */
    @Override
    public String toString() {
        if (resolved != null) return resolved.toString();
        return modelId.orElse("\"\"") + " " +
               url.map(v -> v.value()).orElse("\"\"") + " " +
               path.map(v -> v.value()).orElse("\"\"");
    }

    /**
     * Creates a model reference which is either a single string with no spaces if resolved, or if unresolved
     * a three-part string on the form <code>modelId url path</code>, where
     * each of the elements is either a value not containing space, or empty represented by "".
     */
    public static ModelReference valueOf(String s) {
        String[] parts = s.split(" ");
        if (parts.length == 1)
            return resolved(Path.of(s));
        else if (parts.length == 3)
            return unresolved(parts[0].equals("\"\"") ? Optional.empty() : Optional.of(parts[0]),
                              parts[1].equals("\"\"") ? Optional.empty() : Optional.of(new UrlReference(parts[1])),
                              parts[2].equals("\"\"") ? Optional.empty() : Optional.of(new FileReference(parts[2])));
        else
            throw new IllegalArgumentException("Unexpected model reference string '" + s + "'");
    }

    /** Creates an unresolved reference from a model id only. */
    public static ModelReference unresolved(String modelId) {
        return new ModelReference(Optional.of(modelId), Optional.empty(), Optional.empty(), null);
    }

    /** Creates an unresolved reference from an url only. */
    public static ModelReference unresolved(UrlReference url) {
        return new ModelReference(Optional.empty(), Optional.of(url), Optional.empty(), null);
    }

    /** Creates an unresolved reference from a path only. */
    public static ModelReference unresolved(FileReference path) {
        return new ModelReference(Optional.empty(), Optional.empty(), Optional.of(path), null);
    }

    /** Creates an unresolved reference. */
    public static ModelReference unresolved(Optional<String> modelId,
                                            Optional<UrlReference> url,
                                            Optional<FileReference> path) {
        if (modelId.isEmpty() && url.isEmpty() && path.isEmpty())
            throw new IllegalArgumentException("A model reference must have either a model id, url or path");
        return new ModelReference(modelId, url, path, null);
    }

    /** Creates a resolved reference. */
    public static ModelReference resolved(Path path) {
        return new ModelReference(null, null, null, Objects.requireNonNull(path));
    }

}
