package com.yahoo.vespa.hosted.node.verification.spec.noderepo; /**
 * Created by olaa on 04/07/2017.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.spec.hardware.HardwareInfo;
import java.io.IOException;
import java.net.URL;

public class NodeInfoRetriever {

    public static HardwareInfo retrieve(URL url) {
        NodeJsonModel nodeJsonModel;
        HardwareInfo nodeRepoInfo = new HardwareInfo();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            nodeJsonModel = objectMapper.readValue(url, NodeJsonModel.class);
        } catch (IOException e) {
            e.printStackTrace();
            return nodeRepoInfo;
        }
        NodeGenerator nodeGenerator = new NodeGenerator(nodeJsonModel);
        nodeRepoInfo = nodeGenerator.convertJsonModel();
        return nodeRepoInfo;
    }

}

