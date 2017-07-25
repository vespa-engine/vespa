package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 04/07/2017.
 * Parse JSON from node repository and stores information as a NodeJsonModel object.
 */
public class NodeInfoRetriever {

    private static final Logger logger = Logger.getLogger(NodeInfoRetriever.class.getName());

    public static NodeJsonModel retrieve(URL url) {
        NodeJsonModel nodeJsonModel;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            nodeJsonModel = objectMapper.readValue(url, NodeJsonModel.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse JSON", e);
            return null;
        }
        return nodeJsonModel;
    }

}

