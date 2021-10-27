// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.Optional;

/**
 * @author olaa
 */
public interface ContactRetriever {
    Contact getContact(Optional<PropertyId> propertyId);
}
