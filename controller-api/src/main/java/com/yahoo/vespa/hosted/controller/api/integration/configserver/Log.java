// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author Tony Vaagenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Log {
    public long time;
    public String level;
    public String message;
    public boolean applicationPackage;

}
