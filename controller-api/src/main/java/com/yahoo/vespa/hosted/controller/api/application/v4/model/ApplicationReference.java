// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;

import java.net.URI;

/**
 * @author Stian Kristoffersen
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationReference {
    public ApplicationId application;
    public URI url;
}
