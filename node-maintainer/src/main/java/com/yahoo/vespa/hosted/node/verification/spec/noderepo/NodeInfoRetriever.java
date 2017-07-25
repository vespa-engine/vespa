package com.yahoo.vespa.hosted.node.verification.spec.noderepo; /**
 * Created by olaa on 04/07/2017.
 */

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;

public class NodeInfoRetriever {

    public static NodeJsonModel retrieve(URL url) {
        NodeJsonModel nodeJsonModel;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            nodeJsonModel = objectMapper.readValue(url, NodeJsonModel.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return nodeJsonModel;
    }

}

