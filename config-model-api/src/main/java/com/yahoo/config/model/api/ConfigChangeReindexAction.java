// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Optional;

/**
 * Represents an action to re-index a document type in order to handle a config change.
 *
 * @author bjorncs
 */
public interface ConfigChangeReindexAction extends ConfigChangeAction {

    @Override default Type getType() { return Type.REINDEX; }

    /** @return name identifying this kind of change, used to identify names which should be allowed */
    String name();

    /** @return name of the document type that must bed re-indexed, or empty if all document types */
    Optional<String> getDocumentType();
}
