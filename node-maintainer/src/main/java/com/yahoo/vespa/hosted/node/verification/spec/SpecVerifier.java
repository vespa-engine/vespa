// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec;

import com.google.common.base.Strings;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.verification.Main;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.IPAddressVerifier;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeJsonConverter;
import com.yahoo.vespa.hosted.node.verification.commons.noderepo.NodeSpec;
import com.yahoo.vespa.hosted.node.verification.commons.report.HardwareDivergenceReport;
import com.yahoo.vespa.hosted.node.verification.commons.report.SpecVerificationReport;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfoRetriever;
import io.airlift.command.Command;
import io.airlift.command.Option;

import java.util.Optional;

/**
 * Creates two HardwareInfo objects, one with spec from node repository and one from spec retrieved at the node.
 * Compares the objects and returns the result.
 *
 * @author olaaun
 * @author sgrostad
 */
@Command(name = "specification", description = "Verify that node's actual hardware and configuration matches the expected")
public class SpecVerifier extends Main.VerifierCommand {

    @Option(name = {"-d", "--disk"}, required = true, description = "Expected disk size in GB")
    private double diskAvailableGb;

    @Option(name = {"-m", "--memory"}, required = true, description = "Expected main memory size in GB")
    private double mainMemoryAvailableGb;

    @Option(name = {"-c", "--cpu_cores"}, required = true, description = "Expected number of CPU cores")
    private double cpuCores;

    @Option(name = {"-s", "--is_ssd"}, required = true, description = "Set to true if disk is SSD", allowedValues = {"true", "false"})
    private String fastDisk;

    @Option(name = {"-b", "--bandwidth"}, required = true, description = "Expected network interface speed in Mbit/s")
    private double bandwidth;

    @Option(name = {"-i", "--ips"}, description = "Comma separated list of IP addresses assigned to this node")
    private String ipAddresses;

    @Option(name = {"--skip-lookup"}, required = false, description = "Skip verification of hostname -> IP addresses")
    private boolean skipLookup = false;

    @Option(name = {"--skip-reverse-lookup"}, required = false, description = "Skip verification of IP addresses -> hostname")
    private boolean skipReverseLookup = false;

    @Override
    public void run(HardwareDivergenceReport hardwareDivergenceReport, CommandExecutor commandExecutor) {
        String[] ips = Optional.ofNullable(ipAddresses)
                .filter(s -> !Strings.isNullOrEmpty(s))
                .map(s -> s.split(","))
                .orElse(new String[0]);

        NodeSpec nodeSpec = new NodeSpec(diskAvailableGb, mainMemoryAvailableGb, cpuCores, Boolean.valueOf(fastDisk), bandwidth, ips);
        SpecVerificationReport specVerificationReport = verifySpec(nodeSpec, commandExecutor);

        hardwareDivergenceReport.setSpecVerificationReport(specVerificationReport);
    }

    private SpecVerificationReport verifySpec(NodeSpec nodeSpec, CommandExecutor commandExecutor) {
        VerifierSettings verifierSettings = new VerifierSettings(false);
        HardwareInfo actualHardware = HardwareInfoRetriever.retrieve(commandExecutor, verifierSettings);
        return makeVerificationReport(actualHardware, nodeSpec, skipLookup, skipReverseLookup);
    }

    private static SpecVerificationReport makeVerificationReport(
            HardwareInfo actualHardware,
            NodeSpec nodeSpec,
            boolean skipLookup,
            boolean skipReverseLookup) {
        SpecVerificationReport specVerificationReport = HardwareNodeComparator.compare(NodeJsonConverter.convertJsonModelToHardwareInfo(nodeSpec), actualHardware);
        IPAddressVerifier ipAddressVerifier = new IPAddressVerifier(Defaults.getDefaults().vespaHostname(), skipLookup, skipReverseLookup);
        ipAddressVerifier.reportFaultyIpAddresses(nodeSpec, specVerificationReport);
        return specVerificationReport;
    }
}
