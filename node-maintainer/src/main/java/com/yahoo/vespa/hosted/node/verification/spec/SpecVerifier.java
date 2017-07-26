package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.IPAddressVerifier;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeJsonConverter;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.YamasSpecReport;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Created by olaa on 14/07/2017.
 * Creates two HardwareInfo objects, one with spec from node repository and one from spec retrieved at the node.
 * Compares the objects and returns the result.
 */
public class SpecVerifier {

    private static final Logger logger = Logger.getLogger(SpecVerifier.class.getName());

    public void verifySpec(String configServerHostName, CommandExecutor commandExecutor) throws IOException {
        NodeRepoJsonModel nodeRepoJsonModel = getNodeRepositoryJSON(configServerHostName, commandExecutor);
        HardwareInfo actualHardware = HardwareInfoRetriever.retrieve(commandExecutor);
        YamasSpecReport yamasSpecReport = makeYamasSpecReport(actualHardware, nodeRepoJsonModel);
        printResults(yamasSpecReport);
    }

    protected YamasSpecReport makeYamasSpecReport(HardwareInfo actualHardware, NodeRepoJsonModel nodeRepoJsonModel){
        YamasSpecReport yamasSpecReport = HardwareNodeComparator.compare(NodeJsonConverter.convertJsonModelToHardwareInfo(nodeRepoJsonModel), actualHardware);
        IPAddressVerifier ipAddressVerifier = new IPAddressVerifier();
        ipAddressVerifier.reportFaultyIpAddresses(nodeRepoJsonModel, yamasSpecReport);
        return yamasSpecReport;
    }

    protected NodeRepoJsonModel getNodeRepositoryJSON(String configServerHostName, CommandExecutor commandExecutor) throws IOException {
        URL nodeRepoUrl;
        HostURLGenerator hostURLGenerator = new HostURLGenerator();
        nodeRepoUrl = hostURLGenerator.generateNodeInfoUrl(configServerHostName, commandExecutor);
        NodeRepoJsonModel nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(nodeRepoUrl);
        return nodeRepoJsonModel;
    }

    private void printResults(YamasSpecReport yamasSpecReport) {
        //TODO: Instead of println, report JSON to YAMAS
        ObjectMapper om = new ObjectMapper();
        try {
            System.out.println(om.writeValueAsString(yamasSpecReport));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new RuntimeException("Expected only 1 argument - config server zone url");
        }
        String configServerHostName = args[0];
        CommandExecutor commandExecutor = new CommandExecutor();
        SpecVerifier specVerifier = new SpecVerifier();
        specVerifier.verifySpec(configServerHostName, commandExecutor);
    }

}
