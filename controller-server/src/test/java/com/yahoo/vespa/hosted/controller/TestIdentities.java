// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.EnvironmentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.RegionId;

/**
 * @author Tony Vaagenes
 */
public class TestIdentities {

    public static EnvironmentId environment = new EnvironmentId("dev");

    public static RegionId region = new RegionId("us-east-1");

    public static InstanceId instance = new InstanceId("default");

    public static Property property = new Property("property");

    public static NToken userNToken = new NToken("dummy");

}
