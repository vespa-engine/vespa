package com.yahoo.vespa.hosted.node.verification.spec;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by olaa on 14/07/2017.
 */
public class HostURLGenerator {

    private static final String API_PATH_PREFIX= "/nodes/v2/node/";

    protected URL generateNodeInfoUrl(String zoneHostName) throws MalformedURLException {
        String nodeHostName = getEnvironmentVariable("HOSTNAME");
        return new URL(zoneHostName + API_PATH_PREFIX + nodeHostName);
    }

    protected String getEnvironmentVariable(String variableName) {
        return System.getenv(variableName);
    }

}
