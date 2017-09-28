// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parse JSON from node repository and stores information as a NodeRepoJsonModel object.
 *
 * @author olaaun
 * @author sgrostad
 */
public class NodeRepoInfoRetriever {

    private static final Logger logger = Logger.getLogger(NodeRepoInfoRetriever.class.getName());

    public static NodeRepoJsonModel retrieve(List<URL> nodeInfoUrls) throws IOException {
        NodeRepoJsonModel nodeRepoJsonModel;
        ObjectMapper objectMapper = new ObjectMapper();
        for (URL nodeInfoURL : nodeInfoUrls) {
            try {
                nodeRepoJsonModel = objectMapper.readValue(nodeInfoURL, NodeRepoJsonModel.class);
                return nodeRepoJsonModel;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to parse JSON from config server: " + nodeInfoURL.toString(), e);
            }
        }
        throw new IOException("Failed to parse JSON from all possible config servers.");
    }

}

