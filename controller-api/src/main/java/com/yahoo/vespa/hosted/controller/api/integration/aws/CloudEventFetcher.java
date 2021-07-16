// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import java.util.List;

/**
 * @author freva
 */
public interface CloudEventFetcher {

    List<CloudEvent> getEvents(String regionName);

}
