// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec;

import com.yahoo.log.LogSetup;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.HostURLGenerator;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.IPAddressVerifier;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeJsonConverter;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeSpec;
import com.yahoo.vespa.hosted.node.verification.commons.report.Reporter;
import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfoRetriever;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates two HardwareInfo objects, one with spec from node repository and one from spec retrieved at the node.
 * Compares the objects and returns the result.
 *
 * @author olaaun
 * @author sgrostad
 */
public class SpecVerifier {

    private static final Logger logger = Logger.getLogger(SpecVerifier.class.getName());

    public static boolean verifySpec(CommandExecutor commandExecutor, List<URL> nodeInfoUrls) throws IOException {
        NodeSpec nodeSpec = getNodeRepositoryJSON(nodeInfoUrls);
        VerifierSettings verifierSettings = new VerifierSettings(nodeSpec);
        HardwareInfo actualHardware = HardwareInfoRetriever.retrieve(commandExecutor, verifierSettings);
        SpecVerificationReport specVerificationReport = makeVerificationReport(actualHardware, nodeSpec);
        Reporter.reportSpecVerificationResults(specVerificationReport, nodeInfoUrls);
        return specVerificationReport.isValidSpec();
    }

    protected static SpecVerificationReport makeVerificationReport(HardwareInfo actualHardware, NodeSpec nodeSpec) {
        SpecVerificationReport specVerificationReport = HardwareNodeComparator.compare(NodeJsonConverter.convertJsonModelToHardwareInfo(nodeSpec), actualHardware);
        IPAddressVerifier ipAddressVerifier = new IPAddressVerifier();
        ipAddressVerifier.reportFaultyIpAddresses(nodeSpec, specVerificationReport);
        return specVerificationReport;
    }

    protected static NodeSpec getNodeRepositoryJSON(List<URL> nodeInfoUrls) throws IOException {
        NodeSpec nodeSpec = NodeRepoInfoRetriever.retrieve(nodeInfoUrls);
        return nodeSpec;
    }

    public static void main(String[] args) {
        LogSetup.initVespaLogging("spec-verifier");
        CommandExecutor commandExecutor = new CommandExecutor();
        List<URL> nodeInfoUrls;
        if (args.length == 0) {
            throw new IllegalStateException("Expected config server URL as parameter");
        }
        try {
            nodeInfoUrls = HostURLGenerator.generateNodeInfoUrl(commandExecutor, args[0]);
            SpecVerifier.verifySpec(commandExecutor, nodeInfoUrls);
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
    }

}
