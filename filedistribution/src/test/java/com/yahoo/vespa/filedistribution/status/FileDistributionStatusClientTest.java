// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution.status;

import org.junit.Test;

import static com.yahoo.vespa.filedistribution.status.FileDistributionStatusClient.CommandLineArguments;
import static org.junit.Assert.assertEquals;

public class FileDistributionStatusClientTest {

    private static final CommandLineArguments arguments = createArguments("--tenant", "foo", "--application", "bar");
    private final FileDistributionStatusClient client = new FileDistributionStatusClient(arguments);

    @Test
    public void finishedForAllHosts() {
        String output = client.parseAndGenerateOutput("{\"status\":\"FINISHED\"}");
        assertEquals("File distribution finished", output);
    }

    @Test
    public void unknownForAllHosts() {
        String output = client.parseAndGenerateOutput("{\"status\":\"UNKNOWN\", \"message\":\"Something went wrong\"}");
        assertEquals("File distribution status unknown: Something went wrong", output);
    }

    @Test
    public void manyHostsVariousStates() {
        String statusForTwoHosts = createStatusForTwoHosts();
        System.out.println(statusForTwoHosts);
        String output = client.parseAndGenerateOutput(statusForTwoHosts);
        assertEquals("File distribution in progress:\nlocalhost1: IN_PROGRESS (1 of 2 finished)\nlocalhost2: UNKNOWN (Connection timed out)", output);
    }

    private static CommandLineArguments createArguments(String... args) {
        return CommandLineArguments.build(args);
    }

    private String createStatusForTwoHosts() {
        return "{\"status\":\"IN_PROGRESS\"," +
                "\"hosts\":[" + createInProgressStatusForHost("localhost1") + "," + createUnknownStatusForHost("localhost2") + "]" +
                "}";
    }

    private String createInProgressStatusForHost(String hostname) {
        return "{\"hostname\":\"" + hostname + "\"," +
                "\"status\":\"IN_PROGRESS\"," +
                "\"message\":\"\"," +
                "\"fileReferences\":[" +
                "{\"1234\":0.2}, {\"abcd\":1.0}]}";
    }

    private String createUnknownStatusForHost(String hostname) {
        return "{\"hostname\":\"" + hostname + "\"," +
                "\"status\":\"UNKNOWN\"," +
                "\"message\":\"Connection timed out\"}";
    }

}
