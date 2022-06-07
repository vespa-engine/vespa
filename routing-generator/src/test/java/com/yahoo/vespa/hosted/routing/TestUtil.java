// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.yolean.Exceptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author mpolden
 */
public class TestUtil {

    private static final Path testData = Paths.get("src/test/resources/");

    public static RoutingTable readRoutingTable(String filename) {
        List<String> lines = Exceptions.uncheck(() -> Files.readAllLines(testFile(filename),
                                                                         StandardCharsets.UTF_8));
        LbServicesConfig lbServicesConfig = new CfgConfigPayloadBuilder().deserialize(lines)
                                                                         .toInstance(LbServicesConfig.class, "*");
        return RoutingTable.from(lbServicesConfig, 42);
    }

    public static Path testFile(String filename) {
        return testData.resolve(filename);
    }

}
