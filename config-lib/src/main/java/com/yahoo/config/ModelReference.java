// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.io.File;
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

    // At least one of these are set
    private final Optional<String> modelId;
    private final Optional<UrlReference> url;
    private final Optional<FileReference> path;

    public ModelReference(Optional<String> modelId,
                          Optional<UrlReference> url,
                          Optional<FileReference> path) {
        if (modelId.isEmpty() && url.isEmpty() && path.isEmpty())
            throw new IllegalArgumentException("A model reference must have either a model id, url or path");
        this.modelId = modelId;
        this.url = url;
        this.path = path;
    }

    public Optional<String> modelId() { return modelId; }
    public Optional<UrlReference> url() { return url; }
    public Optional<FileReference> path() { return path; }

    public ModelReference withModelId(Optional<String> modelId) {
        return new ModelReference(modelId, url, path);
    }

    public ModelReference withUrl(Optional<UrlReference> url) {
        return new ModelReference(modelId, url, path);
    }

    public ModelReference withPath(Optional<FileReference> path) {
        return new ModelReference(modelId, url, path);
    }

    /** Returns the path to the file containing this model. */
    public String value() {
        if (url.isPresent() && new File(url.get().value()).exists())
            return new File(url.get().value()).getAbsolutePath();
        if (path.isPresent())
            return path.get().value();
        throw new IllegalStateException("No url or path is available");
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof ModelReference other)) return false;
        if ( ! this.modelId.equals(other.modelId)) return false;
        if ( ! this.url.equals(other.url)) return false;
        if ( ! this.path.equals(other.path)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, url, path);
    }

    /** Returns this on the format accepted by valueOf */
    @Override
    public String toString() {
        return modelId.orElse("") + " " +
               url.map(v -> v.value()).orElse("") + " " +
               path.map(v -> v.value()).orElse("");
    }

    /** Creates a model reference having a model id only. */
    public static ModelReference fromModelId(String modelId) {
        return new ModelReference(Optional.of(modelId), Optional.empty(), Optional.empty());
    }

    /** Creates a model reference having a url only. */
    public static ModelReference fromUrl(String url) {
        return new ModelReference(Optional.empty(), Optional.of(new UrlReference(url)), Optional.empty());
    }

    /** Creates a model reference having a path only. */
    public static ModelReference fromPath(String path) {
        return new ModelReference(Optional.empty(), Optional.empty(), Optional.of(new FileReference(path)));
    }

    /**
     * Creates a model reference from a three-part string on the form
     * <code>modelId url path</code>
     * Each of the elements are either a value not containing space, or empty represented by "".
     */
    public static ModelReference valueOf(String s) {
        String[] parts = s.split(" ");
        if (parts.length != 3)
            throw new IllegalArgumentException("Expected a config with exactly three space-separated parts, but got '" + s + "'");
        return new ModelReference(parts[0].equals("") ? Optional.empty() : Optional.of(parts[0]),
                                  parts[1].equals("") ? Optional.empty() : Optional.of(new UrlReference(parts[1])),
                                  parts[2].equals("") ? Optional.empty() : Optional.of(new FileReference(parts[2])));

    }

}
