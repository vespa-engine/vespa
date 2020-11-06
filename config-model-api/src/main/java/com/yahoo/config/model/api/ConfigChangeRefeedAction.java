// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

/**
 * Represents an action to re-feed a document type in order to handle a config change.
 *
 * @author geirst
 */
public interface ConfigChangeRefeedAction extends ConfigChangeAction {

    @Override
    default Type getType() { return Type.REFEED; }

    /** Returns the name identifying this kind of change, used to identify names which should be allowed */
    String name();

    /** Returns the name of the document type that one must re-feed to handle this config change */
    String getDocumentType();

}
