package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.Optional;

/**
 * @author olaa
 */
public interface ContactRetriever {
    Contact getContact(Optional<PropertyId> propertyId);
}
