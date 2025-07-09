// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.document.DocumentId;
import com.yahoo.restapi.Path;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Represents the path of a REST-ful document resource.
 *
 * @author Jon Marius Venstad
 */
class DocumentPath {

    private static final Parser<Long> unsignedLongParser = Long::parseUnsignedLong;

    private final Path path;
    private final String rawPath;
    private final Optional<Group> group;

    DocumentPath(Path path, String rawPath) {
        this.path = requireNonNull(path);
        this.rawPath = requireNonNull(rawPath);
        this.group = Optional.ofNullable(path.get("number"))
                .map(unsignedLongParser::parse)
                .map(Group::of)
                .or(() -> Optional.ofNullable(path.get("group")).map(Group::of));
    }

    DocumentId id() {
        return new DocumentId("id:" + requireNonNull(path.get("namespace")) +
                ":" + requireNonNull(path.get("documentType")) +
                ":" + group.map(Group::docIdPart).orElse("") +
                ":" + String.join("/", requireNonNull(path.getRest()).segments())); // :'(
    }

    String rawPath() {
        return rawPath;
    }

    Optional<String> documentType() {
        return Optional.ofNullable(path.get("documentType"));
    }

    Optional<String> namespace() {
        return Optional.ofNullable(path.get("namespace"));
    }

    Optional<Group> group() {
        return group;
    }

}
