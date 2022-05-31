// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.fieldset.DocumentOnly;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

/**
 * This class is responsible for parsing the command line arguments and print the help page.
 *
 * @author bjorncs
 */
public class CommandLineOptions {

    public static final String HELP_OPTION = "help";
    public static final String PRINTIDS_OPTION = "printids";
    public static final String FIELDSET_OPTION = "fieldset";
    public static final String CLUSTER_OPTION = "cluster";
    public static final String ROUTE_OPTION = "route";
    public static final String CONFIGID_OPTION = "configid";
    public static final String SHOWDOCSIZE_OPTION = "showdocsize";
    public static final String TIMEOUT_OPTION = "timeout";
    public static final String NORETRY_OPTION = "noretry";
    public static final String TRACE_OPTION = "trace";
    public static final String PRIORITY_OPTION = "priority";
    public static final String JSONOUTPUT_OPTION = "jsonoutput";
    public static final String XMLOUTPUT_OPTION = "xmloutput";

    private final Options options = createOptions();
    private final InputStream stdIn;

    public CommandLineOptions(InputStream stdIn) {
        this.stdIn = stdIn;
    }

    public CommandLineOptions() {
        this(System.in);
    }

    @SuppressWarnings("AccessStaticViaInstance")
    private static Options createOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .hasArg(false)
                .desc("Show this syntax page.")
                .longOpt(HELP_OPTION)
                .build());

        options.addOption(Option.builder("i")
                .hasArg(false)
                .desc("Show only identifiers of retrieved documents.")
                .longOpt(PRINTIDS_OPTION)
                .build());

        options.addOption(Option.builder("f")
                .hasArg(true)
                .desc("Retrieve the specified fields only (see https://docs.vespa.ai/en/documents.html#fieldsets) (default '" + DocumentOnly.NAME + "')")
                .longOpt(FIELDSET_OPTION)
                .argName("fieldset").build());

        options.addOption(Option.builder("u")
                .hasArg(true)
                .desc("Send request to the given content cluster.")
                .longOpt(CLUSTER_OPTION)
                .argName("cluster").build());

        options.addOption(Option.builder("r")
                .hasArg(true)
                .desc("Send request to the given messagebus route.")
                .longOpt(ROUTE_OPTION)
                .argName("route").build());

        options.addOption(Option.builder("c")
                .hasArg(true)
                .desc("Use the specified config id for messagebus configuration.")
                .longOpt(CONFIGID_OPTION)
                .argName("configid").build());

        options.addOption(Option.builder("s")
                .hasArg(false)
                .desc("Show binary size of document.")
                .longOpt(SHOWDOCSIZE_OPTION).build());

        options.addOption(Option.builder("t")
                .hasArg(true)
                .desc("Set timeout for the request in seconds (default 0).")
                .longOpt(TIMEOUT_OPTION)
                .argName("timeout")
                .type(Number.class).build());

        options.addOption(Option.builder("n")
                .hasArg(false)
                .desc("Do not retry operation on transient errors, as is default.")
                .longOpt(NORETRY_OPTION).build());

        options.addOption(Option.builder("a")
                .hasArg(true)
                .desc("Trace level to use (default 0).")
                .longOpt(TRACE_OPTION)
                .argName("trace")
                .type(Number.class).build());

        options.addOption(Option.builder("p")
                .hasArg(true)
                .desc("Priority (default 6).")
                .longOpt(PRIORITY_OPTION)
                .argName("priority").build());

        options.addOption(Option.builder("j")
                .hasArg(false)
                .desc("JSON output (default format)")
                .longOpt(JSONOUTPUT_OPTION).build());

        options.addOption(Option.builder("x")
                .hasArg(false)
                .desc("XML output")
                .longOpt(XMLOUTPUT_OPTION).build());

        return options;
    }

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();

        formatter.printHelp(
                "vespa-get <options> [documentid...]", "Fetch a document from a Vespa Content cluster.", options,
                "If one or more document identifier are specified, these documents will be " +
                        "retrieved. Otherwise, document identifiers (separated with line break) will be read from standard in.\n",
                false);
    }

    public ClientParameters parseCommandLineArguments(String[] args) throws IllegalArgumentException {
        try {
            CommandLineParser clp = new DefaultParser();
            CommandLine cl = clp.parse(options, args);

            boolean printIdsOnly = cl.hasOption(PRINTIDS_OPTION);
            String fieldSet = cl.getOptionValue(FIELDSET_OPTION, "");
            String cluster = cl.getOptionValue(CLUSTER_OPTION, "");
            String route = cl.getOptionValue(ROUTE_OPTION, "");
            String configId = cl.getOptionValue(CONFIGID_OPTION, "");
            boolean help = cl.hasOption(HELP_OPTION);
            boolean noRetry = cl.hasOption(NORETRY_OPTION);
            boolean showDocSize = cl.hasOption(SHOWDOCSIZE_OPTION);
            boolean jsonOutput = cl.hasOption(JSONOUTPUT_OPTION);
            boolean xmlOutput = cl.hasOption(XMLOUTPUT_OPTION);
            int trace = getTrace(cl);
            DocumentProtocol.Priority priority = getPriority(cl);
            double timeout = getTimeout(cl);
            Iterator<String> documentIds = getDocumentIds(cl);

            if (jsonOutput && xmlOutput) {
                throw new IllegalArgumentException("Cannot combine both xml and json output");
            }

            if (printIdsOnly && !fieldSet.isEmpty()) {
                throw new IllegalArgumentException("Field set option can not be used in combination with print ids option.");
            }

            if (printIdsOnly) {
                fieldSet = DocIdOnly.NAME;
            } else if (fieldSet.isEmpty()) { 
                fieldSet = DocumentOnly.NAME;
            }

            if (!cluster.isEmpty() && !route.isEmpty()) {
                throw new IllegalArgumentException("Cluster and route options are mutually exclusive.");
            }

            if (route.isEmpty() && cluster.isEmpty()) {
                route = "default-get";
            }

            if (trace < 0 || trace > 9) {
                throw new IllegalArgumentException("Invalid tracelevel: " + trace);
            }

            if (configId.isEmpty()) {
                configId = "client";
            }

            ClientParameters.Builder paramsBuilder = new ClientParameters.Builder();
            return paramsBuilder
                    .setDocumentIds(documentIds)
                    .setConfigId(configId)
                    .setFieldSet(fieldSet)
                    .setHelp(help)
                    .setPrintIdsOnly(printIdsOnly)
                    .setNoRetry(noRetry)
                    .setCluster(cluster)
                    .setRoute(route)
                    .setShowDocSize(showDocSize)
                    .setTraceLevel(trace)
                    .setPriority(priority)
                    .setTimeout(timeout)
                    .setJsonOutput(!xmlOutput)
                    .build();
        } catch (ParseException pe) {
            throw new IllegalArgumentException(pe.getMessage());
        }
    }

    private Iterator<String> getDocumentIds(CommandLine cl) {
        // Fetch document ids from stdin if no ids are passed in as command line arguments
        List<String> documentIds = Arrays.asList(cl.getArgs());
        // WARNING: CommandLine.getArgs may return a single empty string as the only element
        if (documentIds.isEmpty() ||
                documentIds.size() == 1 && documentIds.get(0).isEmpty()) {
            return new Scanner(stdIn);
        } else {
            return documentIds.iterator();
        }
    }

    private static double getTimeout(CommandLine cl) throws ParseException {
        Number timeoutObj = (Number) cl.getParsedOptionValue(TIMEOUT_OPTION);
        return timeoutObj != null ? timeoutObj.doubleValue() : 0;
    }

    private static int getTrace(CommandLine cl) throws ParseException {
        Number traceObj = (Number) cl.getParsedOptionValue(TRACE_OPTION);
        return traceObj != null ? traceObj.intValue() : 0;
    }

    private static DocumentProtocol.Priority getPriority(CommandLine cl) throws ParseException {
        String priority = cl.getOptionValue(PRIORITY_OPTION, "NORMAL_2");
        return parsePriority(priority);
    }

    private static DocumentProtocol.Priority parsePriority(String name) {
        try {
            return DocumentProtocol.Priority.valueOf(name);
        } catch (IllegalArgumentException e) {
        }
        try {
            int n = Integer.parseInt(name);
            for (DocumentProtocol.Priority priority : DocumentProtocol.Priority.values()) {
                if (priority.getValue() == n) {
                    return priority;
                }
            }
        } catch (NumberFormatException e) {
        }
        throw new IllegalArgumentException("Invalid priority: " + name);
    }

}
