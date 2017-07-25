package com.yahoo.vespa.hosted.node.verification.spec;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by olaa on 14/07/2017.
 */
public class HostURLGenerator {

    private static final String NODE_HOSTNAME_PREFIX= "/nodes/v2/node/";

    protected URL generateNodeInfoUrl(String configServerHostName) throws MalformedURLException {
        String nodeHostName = getEnvironmentVariable("HOSTNAME");
        return new URL(configServerHostName + NODE_HOSTNAME_PREFIX + nodeHostName);
    }

    protected String getEnvironmentVariable(String variableName) {
        return System.getenv(variableName);
    }

}
