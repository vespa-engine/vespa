package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by olaa on 14/07/2017.
 * Makes the URL used to retrieve the JSON from the node repository with information about the node's spec.
 */
public class HostURLGenerator {

    private static final String NODE_HOSTNAME_PREFIX = "/nodes/v2/node/";

    protected URL generateNodeInfoUrl(String configServerHostName, CommandExecutor commandExecutor) throws IOException {
        String nodeHostName = getEnvironmentVariable(commandExecutor);
        return new URL(configServerHostName + NODE_HOSTNAME_PREFIX + nodeHostName);
    }

    protected String getEnvironmentVariable(CommandExecutor commandExecutor) throws IOException {
        ArrayList<String> output = commandExecutor.executeCommand("hostname");
        if (output.size() == 1){
            return output.get(0);
        }
        throw new IOException("Unexpected output from \"hostname\" command.");
    }

}
