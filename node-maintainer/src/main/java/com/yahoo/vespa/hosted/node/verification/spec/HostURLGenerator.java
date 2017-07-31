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
    private static final String PORT_NUMBER = ":4080";
    private static final String CONFIG_SERVER_HOST_NAME_COMMAND = "yinst set | grep cfg";
    private static final String HTTP = "http://";
    private static final String PARSE_OUT_HOSTNAMES_REGEX = "\\s+";
    private static final String PARSE_ALL_HOSTNAMES_REGEX = ",";
    private static final String PROTOCOL_REGEX = "^(https?|file)://.*$";

    public static ArrayList<URL> generateNodeInfoUrl(CommandExecutor commandExecutor) throws IOException {
        String[] configServerHostNames = getConfigServerHostNames(commandExecutor);
        String nodeHostName = generateNodeHostName(commandExecutor);
        ArrayList<URL> nodeInfoUrls = new ArrayList<>();
        for (String configServerHostName : configServerHostNames) {
            nodeInfoUrls.add(buildNodeInfoURL(configServerHostName, nodeHostName));
        }
        return nodeInfoUrls;
    }

    protected static URL buildNodeInfoURL(String configServerHostName, String nodeHostName) throws MalformedURLException {
        if (configServerHostName.matches(PROTOCOL_REGEX)) {
            return new URL(configServerHostName + NODE_HOSTNAME_PREFIX + nodeHostName);
        }
        return new URL(HTTP + configServerHostName + PORT_NUMBER + NODE_HOSTNAME_PREFIX + nodeHostName);
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

    protected static String[] getConfigServerHostNames(CommandExecutor commandExecutor) throws IOException {
        ArrayList<String> output = commandExecutor.executeCommand(CONFIG_SERVER_HOST_NAME_COMMAND);
        if (output.size() != 1)
            throw new IOException("Expected one line return from the command: " + CONFIG_SERVER_HOST_NAME_COMMAND);
        String[] configServerHostNames = parseOutHostNames(output.get(0));
        return configServerHostNames;
    }

    private static String[] parseOutHostNames(String output) throws IOException {
        String[] outputSplit = output.trim().split(PARSE_OUT_HOSTNAMES_REGEX);
        if (outputSplit.length != 2) throw new IOException("Expected config server hsot names to have index 1");
        String[] configServerHostNames = outputSplit[1].split(PARSE_ALL_HOSTNAMES_REGEX);
        return configServerHostNames;
    }

}
