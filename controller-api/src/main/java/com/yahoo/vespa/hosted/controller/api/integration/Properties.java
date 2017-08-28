// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import java.util.Optional;

/**
 * @author jvenstad
 */
public interface Properties {

    /**
     * Return the @Issues.Classification listed for the property with id @propertyId.
     */
    Optional<Issues.Classification> classificationFor(long propertyId);

}
