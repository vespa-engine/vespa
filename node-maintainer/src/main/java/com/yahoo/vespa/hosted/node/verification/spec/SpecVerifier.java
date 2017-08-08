package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.HostURLGenerator;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.IPAddressVerifier;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeJsonConverter;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.report.VerificationReport;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by olaa on 14/07/2017.
 * Creates two HardwareInfo objects, one with spec from node repository and one from spec retrieved at the node.
 * Compares the objects and returns the result.
 */
public class SpecVerifier {

    private static final Logger logger = Logger.getLogger(SpecVerifier.class.getName());
    private static final String VIRTUAL_ENVIRONMENT = "VIRTUAL_MACHINE";

    public static boolean verifySpec(CommandExecutor commandExecutor, ArrayList<URL> nodeInfoUrls) throws IOException {
        NodeRepoJsonModel nodeRepoJsonModel = getNodeRepositoryJSON(nodeInfoUrls);
        if (nodeRepoJsonModel.getEnvironment().equals(VIRTUAL_ENVIRONMENT)) {
            logger.log(Level.INFO, "Node is virtual machine - No need for verification");
            return true;
        }
        VerifierSettings verifierSettings = new VerifierSettings(nodeRepoJsonModel);
        HardwareInfo actualHardware = HardwareInfoRetriever.retrieve(commandExecutor, verifierSettings);
        VerificationReport verificationReport = makeVerificationReport(actualHardware, nodeRepoJsonModel);
        printResults(verificationReport);
        return isValidSpec(verificationReport);
    }

    private static boolean isValidSpec(VerificationReport verificationReport) throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        return om.writeValueAsString(verificationReport).length() == 2;
    }

    protected static VerificationReport makeVerificationReport(HardwareInfo actualHardware, NodeRepoJsonModel nodeRepoJsonModel) {
        VerificationReport verificationReport = HardwareNodeComparator.compare(NodeJsonConverter.convertJsonModelToHardwareInfo(nodeRepoJsonModel), actualHardware);
        IPAddressVerifier ipAddressVerifier = new IPAddressVerifier();
        ipAddressVerifier.reportFaultyIpAddresses(nodeRepoJsonModel, verificationReport);
        return verificationReport;
    }

    protected static NodeRepoJsonModel getNodeRepositoryJSON(ArrayList<URL> nodeInfoUrls) throws IOException {
        NodeRepoJsonModel nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(nodeInfoUrls);
        return nodeRepoJsonModel;
    }

    private static void printResults(VerificationReport verificationReport) {
        //TODO: Instead of println, report JSON to node repo
        ObjectMapper om = new ObjectMapper();
        try {
            System.out.println(om.writeValueAsString(verificationReport));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        CommandExecutor commandExecutor = new CommandExecutor();
        ArrayList<URL> nodeInfoUrls;
        if (args.length == 0) {
            nodeInfoUrls = HostURLGenerator.generateNodeInfoUrl(commandExecutor);
        } else {
            nodeInfoUrls = HostURLGenerator.generateNodeInfoUrl(commandExecutor, args[0]);
        }

        if (!SpecVerifier.verifySpec(commandExecutor, nodeInfoUrls)) {
            System.exit(2);
        }

    }

}
