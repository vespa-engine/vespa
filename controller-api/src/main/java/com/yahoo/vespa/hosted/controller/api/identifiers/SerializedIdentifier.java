// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

/**
 * TODO: Class description
 *
 * @author smorgrav
 */

public abstract class SerializedIdentifier extends Identifier {

    public SerializedIdentifier(String id) {
        super(id);
    }

    @Override
    public void validate() {
        super.validate();
        validateSerializedIdentifier();
    }

}
