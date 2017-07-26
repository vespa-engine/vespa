package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 04/07/2017.
 * Parse JSON from node repository and stores information as a NodeRepoJsonModel object.
 */
public class NodeRepoInfoRetriever {

    private static final Logger logger = Logger.getLogger(NodeRepoInfoRetriever.class.getName());

    public static NodeRepoJsonModel retrieve(URL url) {
        NodeRepoJsonModel nodeRepoJsonModel;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            nodeRepoJsonModel = objectMapper.readValue(url, NodeRepoJsonModel.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse JSON", e);
            return null;
        }
        return nodeRepoJsonModel;
    }

}

