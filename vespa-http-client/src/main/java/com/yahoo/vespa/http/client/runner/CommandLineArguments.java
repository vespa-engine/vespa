// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import com.google.common.base.Splitter;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.ConnectionParams;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import io.airlift.command.Command;
import io.airlift.command.HelpOption;
import io.airlift.command.Option;
import io.airlift.command.SingleCommand;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * Commandline interface for the binary.
 *
 * @author dybis
 */
@Command(name = "vespa-http-client",
        description = "This is a tool for feeding xml or json data to a Vespa application.")
public class CommandLineArguments {

    /**
     * Creates a CommandLineArguments instance and populates it with data.
     *
     * @param args array of arguments.
     * @return null on failure or if help option is set to true.
     */
    static CommandLineArguments build(String[] args) {
        final CommandLineArguments cmdArgs;
        try {
            cmdArgs =  SingleCommand.singleCommand(CommandLineArguments.class).parse(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("Use --help to show usage.\n");
            return null;
        }
        if (cmdArgs.helpOption.showHelpIfRequested()) {
            return null;
        }

        if (cmdArgs.hostArg == null) {
            System.err.println("'--host' not set.");
            return null;
        }
        if (cmdArgs.priorityArg != null && ! checkPriorityFlag(cmdArgs.priorityArg)) {
            return null;
        }

        return cmdArgs;
    }

    private static boolean checkPriorityFlag(String priorityArg) {
        switch (priorityArg) {
            case "HIGHEST":
            case "VERY_HIGH":
            case "HIGH_1":
            case "HIGH_2":
            case "HIGH_3":
            case "NORMAL_1":
            case "NORMAL_2":
            case "NORMAL_3":
            case "NORMAL_4":
            case "NORMAL_5":
            case "NORMAL_6":
            case "LOW_1":
            case "LOW_2":
            case "LOW_3":
            case "VERY_LOW":
            case "LOWEST":
                return true;
            default:
                System.err.println("Not valid value for priority. Allowed values are HIGHEST, VERY_HIGH, HIGH_[1-3], " +
                        "NORMAL_[1-6], LOW_[1-3], VERY_LOW, and LOWEST.");
                return false;
        }
    }

    @Inject
    private HelpOption helpOption;

    @Option(name = {"--useV3Protocol"}, description = "Not used anymore, see useV2Protocol.")
    private boolean notUsedBoolean = true;

    @Option(name = {"--useV2Protocol"}, description = "Use old V2 protocol to gateway.")
    private boolean enableV2Protocol = false;

    @Option(name = {"--file"},
            description = "The name of the input file to read.")
    private String fileArg = null;

    @Option(name = {"--add-root-element-to-xml"},
            description = "Add <vespafeed> tag to XML document, makes it easier to feed raw data.")
    private boolean addRootElementToXml = false;

    @Option(name = {"--route"},
            description = "(=default)The route to send the data to.")
    private String routeArg = "default";

    @Option(name = {"--host"},
            description = "The host(s) for the gateway. If using several, use comma to sepparate them.")
    private String hostArg;

    @Option(name = {"--port"},
            description = "The port for the host of the gateway.")
    private int portArg = 4080;

    @Option(name = {"--timeout"},
            description = "(=180) The time (in seconds) allowed for sending operations.")
    private long timeoutArg = 180;

    @Option(name = {"--useCompression"},
            description = "Use compression over network.")
    private boolean useCompressionArg = false;

    @Option(name = {"--useDynamicThrottling"},
            description = "Try to maximize throughput by using dynamic throttling.")
    private boolean useDynamicThrottlingArg = false;

    @Option(name = {"--maxpending"},
            description = "The maximum number of operations that are allowed " +
                    "to be pending at any given time.")
    private int maxPendingOperationCountArg = 10000;

    @Option(name = {"--debugport"},
            description = "Deprecated, not used.")
    private int debugportArg = 9988;

    @Option(name = {"-v", "--verbose"},
            description = "Enable verbose output of progress.")
    private boolean verboaseArg = false;

    @Option(name = {"--noretry"},
            description = "Turns off retries of recoverable failures..")
    private boolean noRetryArg = false;

    @Option(name = {"--retrydelay"},
            description = "The time (in seconds) to wait between retries of a failed operation.")
    private int retrydelayArg = 1;

    @Option(name = {"--trace"},
            description = "(=0 (=off)) The trace level of network traffic.")
    private int traceArg = 0;

    @Option(name = {"--printTraceEveryXOperation"},
            description = "(=1) How often to to tracing.")
    private int traceEveryXOperation = 1;

    @Option(name = {"--validate"},
            description = "Run validation tool on input files instead of feeding them.")
    private boolean validateArg = false;

    @Option(name = {"--priority"},
            description = "Specify priority of sent messages, see documentation ")
    private String priorityArg = null;

    @Option(name = {"--numPersistentConnectionsPerEndpoint"},
            description = "How many tcp connections to establish per endoint.)")
    private int numPersistentConnectionsPerEndpoint = 16;

    @Option(name = {"--maxChunkSizeBytes"},
            description = "How much data to send to gateway in each message.")
    private int maxChunkSizeBytes = 20 * 1024;

    @Option(name = {"--whenVerboseEnabledPrintMessageForEveryXDocuments"},
            description = "How often to print verbose message.)")
    private int whenVerboseEnabledPrintMessageForEveryXDocuments = 1000;

    @Option(name = {"--useTls"},
            description = "Use TLS when connecting to endpoint")
    private boolean useTls = false;

    @Option(name = {"--insecure"},
            description = "Skip hostname verification when using TLS")
    private boolean insecure = false;

    int getWhenVerboseEnabledPrintMessageForEveryXDocuments() {
        return whenVerboseEnabledPrintMessageForEveryXDocuments;
    }

    public String getFile() { return fileArg; };

    public boolean getVerbose() { return verboaseArg; }

    public boolean getAddRootElementToXml() { return addRootElementToXml; }

    SessionParams createSessionParams(boolean useJson) {
        final int minThrottleValue = useDynamicThrottlingArg ? 10 : 0;
        SessionParams.Builder builder = new SessionParams.Builder()
                .setFeedParams(
                        new FeedParams.Builder()
                                .setDataFormat(useJson
                                        ? FeedParams.DataFormat.JSON_UTF8
                                        : FeedParams.DataFormat.XML_UTF8)
                                .setRoute(routeArg)
                                .setMaxInFlightRequests(maxPendingOperationCountArg)
                                .setClientTimeout(timeoutArg, TimeUnit.SECONDS)
                                .setServerTimeout(timeoutArg, TimeUnit.SECONDS)
                                .setLocalQueueTimeOut(timeoutArg * 1000)
                                .setPriority(priorityArg)
                                .setMaxChunkSizeBytes(maxChunkSizeBytes)
                                .build()
                )
                .setConnectionParams(
                        new ConnectionParams.Builder()
                                .setHostnameVerifier(insecure ? NoopHostnameVerifier.INSTANCE :
                                        SSLConnectionSocketFactory.getDefaultHostnameVerifier())
                                .setNumPersistentConnectionsPerEndpoint(16)
                                .setEnableV3Protocol(! enableV2Protocol)
                                .setUseCompression(useCompressionArg)
                                .setMaxRetries(noRetryArg ? 0 : 100)
                                .setMinTimeBetweenRetries(retrydelayArg, TimeUnit.SECONDS)
                                .setDryRun(validateArg)
                                .setTraceLevel(traceArg)
                                .setTraceEveryXOperation(traceEveryXOperation)
                                .setPrintTraceToStdErr(traceArg > 0)
                                .setNumPersistentConnectionsPerEndpoint(numPersistentConnectionsPerEndpoint)
                                .build()
                )
                        // Enable dynamic throttling.
                .setThrottlerMinSize(minThrottleValue)
                .setClientQueueSize(maxPendingOperationCountArg);
        Iterable<String> hosts = Splitter.on(',').trimResults().split(hostArg);
        for (String host : hosts) {
            builder.addCluster(new Cluster.Builder()
                    .addEndpoint(Endpoint.create(host, portArg, useTls))
                    .build());
        }
        return builder.build();
    }

}
