package com.yahoo.vespa.hosted.node.verification.commons;

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
    private static final String PORT_NUMBER = ":4080";
    private static final String HTTP = "http://";
    private static final String PARSE_ALL_HOSTNAMES_REGEX = ",";
    private static final String PROTOCOL_REGEX = "^(https?|file)://.*$";

    public static ArrayList<URL> generateNodeInfoUrl(CommandExecutor commandExecutor, String commaSeparatedUrls) throws IOException {
        ArrayList<URL> nodeInfoUrls = new ArrayList<>();
        String[] configServerHostNames = commaSeparatedUrls.split(PARSE_ALL_HOSTNAMES_REGEX);
        String nodeHostName = generateNodeHostName(commandExecutor);
        for (String configServerHostName : configServerHostNames) {
            nodeInfoUrls.add(buildNodeInfoURL(configServerHostName, nodeHostName));
        }
        return nodeInfoUrls;
    }

    protected static String generateNodeHostName(CommandExecutor commandExecutor) throws IOException {
        String nodeHostName = getEnvironmentVariable(commandExecutor);
        return nodeHostName;
    }

    protected static String getEnvironmentVariable(CommandExecutor commandExecutor) throws IOException {
        ArrayList<String> output = commandExecutor.executeCommand("hostname");
        if (output.size() == 1) {
            return output.get(0);
        }
        throw new IOException("Unexpected output from \"hostname\" command.");
    }

    protected static URL buildNodeInfoURL(String configServerHostName, String nodeHostName) throws MalformedURLException {
        if (configServerHostName.matches(PROTOCOL_REGEX)) {
            return new URL(configServerHostName + NODE_HOSTNAME_PREFIX + nodeHostName);
        }
        return new URL(HTTP + configServerHostName + PORT_NUMBER + NODE_HOSTNAME_PREFIX + nodeHostName);
    }

}
