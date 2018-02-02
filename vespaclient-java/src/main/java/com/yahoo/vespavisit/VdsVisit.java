// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.log.LogSetup;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.ClusterList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Example client using visiting
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>, based on work by <a href="mailto:humbe@yahoo-inc.com">H&aring;kon Humberset</a>
 */
public class VdsVisit {
    private VdsVisitParameters params;
    private MessageBusParams mbparams = new MessageBusParams(new LoadTypeSet());
    private VisitorSession session;

    private final VisitorSessionAccessorFactory sessionAccessorFactory;
    private VisitorSessionAccessor sessionAccessor;
    private ShutdownHookRegistrar shutdownHookRegistrar;

    public interface ShutdownHookRegistrar {
        public void registerShutdownHook(Thread thread);
    }

    public interface VisitorSessionAccessor {
        public VisitorSession createVisitorSession(VisitorParameters params) throws ParseException;
        public void shutdown();
    }

    public interface VisitorSessionAccessorFactory {
        public VisitorSessionAccessor createVisitorSessionAccessor();
    }

    private static class MessageBusVisitorSessionAccessor implements VisitorSessionAccessor {
        private MessageBusDocumentAccess access;

        private MessageBusVisitorSessionAccessor(MessageBusParams mbparams) {
            access = new MessageBusDocumentAccess(mbparams);
        }
        @Override
        public VisitorSession createVisitorSession(VisitorParameters params) throws ParseException {
            return access.createVisitorSession(params);
        }

        @Override
        public void shutdown() {
            access.shutdown();
        }
    }

    private static class MessageBusVisitorSessionAccessorFactory implements VisitorSessionAccessorFactory {
        MessageBusParams mbparams;

        private MessageBusVisitorSessionAccessorFactory(MessageBusParams mbparams) {
            this.mbparams = mbparams;
        }

        @Override
        public VisitorSessionAccessor createVisitorSessionAccessor() {
            return new MessageBusVisitorSessionAccessor(mbparams);
        }
    }

    private static class JvmRuntimeShutdownHookRegistrar implements ShutdownHookRegistrar {
        @Override
        public void registerShutdownHook(Thread thread) {
            Runtime.getRuntime().addShutdownHook(thread);
        }
    }

    public VdsVisit() {
        this.sessionAccessorFactory = new MessageBusVisitorSessionAccessorFactory(mbparams);
        this.shutdownHookRegistrar = new JvmRuntimeShutdownHookRegistrar();
    }

    public VdsVisit(VisitorSessionAccessorFactory sessionAccessorFactory,
                    ShutdownHookRegistrar shutdownHookRegistrar)
    {
        this.sessionAccessorFactory = sessionAccessorFactory;
        this.shutdownHookRegistrar = shutdownHookRegistrar;
    }

    public static void main(String args[]) {
        LogSetup.initVespaLogging("vespa-visit");
        VdsVisit vdsVisit = new VdsVisit();

        Options options = createOptions();

        try {
            ArgumentParser parser = new ArgumentParser(options);
            vdsVisit.params = parser.parse(args);
            if (vdsVisit.params == null) {
                vdsVisit.printSyntax(options);
                System.exit(0);
            }
            ClusterList clusterList = new ClusterList("client");
            vdsVisit.params.getVisitorParameters().setRoute(
                    resolveClusterRoute(clusterList, vdsVisit.params.getCluster()));
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println("Failed to parse arguments. Try --help for syntax. " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        if (vdsVisit.params.isVerbose()) {
            verbosePrintParameters(vdsVisit.params, System.err);
        }

        try {
            vdsVisit.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void printSyntax(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("vespa-visit <options>", "Visit documents from VDS", options , "");
    }

    @SuppressWarnings("AccessStaticViaInstance")
    protected static Options createOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "Show this syntax page.");

        options.addOption(Option.builder("d")
                .longOpt("datahandler")
                .hasArg(true)
                .argName("target")
                .desc("Send results to the given target.")
                .build());

        options.addOption(Option.builder("s")
                .longOpt("selection")
                .hasArg(true)
                .argName("selection")
                .desc("What documents to visit.")
                .build());

        options.addOption(Option.builder("f")
                .longOpt("from")
                .hasArg(true)
                .argName("timestamp")
                .desc("Only visit from the given timestamp (microseconds).")
                .type(Number.class)
                .build());

        options.addOption(Option.builder("t")
                .longOpt("to")
                .hasArg(true)
                .argName("timestamp")
                .desc("Only visit up to the given timestamp (microseconds).")
                .type(Number.class).build());

        options.addOption("e", "headersonly", false, "Only visit headers of documents.[Deprecated]");

        options.addOption(Option.builder("l")
                .longOpt("fieldset")
                .hasArg(true)
                .argName("fieldset")
                .desc("Retrieve the specified fields only (see http://docs.vespa.ai/documentation/reference/fieldsets.html). Default is [all].")
                .build());

        options.addOption(Option.builder()
                .longOpt("visitinconsistentbuckets")
                .hasArg(false)
                .desc("Don't wait for inconsistent buckets to become consistent.")
                .build());

        options.addOption(Option.builder("m")
                .longOpt("maxpending")
                .hasArg(true)
                .argName("num")
                .desc("Maximum pending messages to data handlers per storage visitor.")
                .type(Number.class)
                .build());

        options.addOption(Option.builder()
                .longOpt("maxpendingsuperbuckets")
                .hasArg(true)
                .argName("num")
                .desc("Maximum pending visitor messages from the vespa-visit client. If set, dynamic throttling of visitors will be disabled!")
                .type(Number.class)
                .build());

        options.addOption(Option.builder("b")
                .longOpt("maxbuckets")
                .hasArg(true)
                .argName("num")
                .desc("Maximum buckets per visitor.")
                .type(Number.class)
                .build());

        options.addOption("i", "printids", false, "Display only document identifiers.");

        options.addOption(Option.builder("p")
                .longOpt("progress")
                .hasArg(true)
                .argName("file")
                .desc("Use given file to track progress.")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("timeout")
                .hasArg(true)
                .argName("milliseconds")
                .desc("Time out visitor after given time.")
                .type(Number.class)
                .build());

        options.addOption(Option.builder("u")
                .longOpt("buckettimeout")
                .hasArg(true)
                .argName("milliseconds")
                .desc("Fail visitor if visiting a single bucket takes longer than this (default same as timeout)")
                .type(Number.class)
                .build());

        options.addOption(Option.builder()
                .longOpt("visitlibrary")
                .hasArg(true)
                .argName("string")
                .desc("Use the given visitor library.")
                .build());

        options.addOption(Option.builder()
                .longOpt("libraryparam")
                .numberOfArgs(2)
                .argName("key> <val")
                .desc("Give the following parameter to the visitor.")
                .build());

        options.addOption("r", "visitremoves", false, "Include information of removed documents.");

        options.addOption(Option.builder("c")
                .longOpt("cluster")
                .hasArg(true)
                .argName("cluster")
                .desc("Visit the given cluster.")
                .build());

        options.addOption("v", "verbose", false, "Indent XML, show progress and info on STDERR.");

        options.addOption(Option.builder()
                .longOpt("statistics")
                .hasArg(true)
                .argName("args")
                .desc("Use CountVisitor for document statistics. Use comma-separated arguments.")
                .build());

        options.addOption(Option.builder()
                .longOpt("abortonclusterdown")
                .hasArg(false)
                .desc("Abort if cluster is down.")
                .build());

        options.addOption(Option.builder()
                .longOpt("maxhits")
                .hasArg(true)
                .argName("num")
                .desc("Abort visiting when we have received this many \"first pass\" documents. Only appropriate for visiting involving id.order. This is only an approximate number, all pending work will be completed and those documents will also be returned.")
                .type(Number.class)
                .build());

        options.addOption(Option.builder()
                .longOpt("maxtotalhits")
                .hasArg(true)
                .argName("num")
                .desc("Abort visiting when we have received this many total documents. This is only an approximate number, all pending work will be completed and those documents will also be returned.")
                .type(Number.class)
                .build());

        options.addOption(Option.builder()
                .longOpt("processtime")
                .hasArg(true)
                .argName("num")
                .desc("Sleep this amount of millisecs before processing message. (Debug option for pretending to be slow client)")
                .type(Number.class)
                .build());
        options.addOption(Option.builder()
                .longOpt("priority")
                .hasArg(true)
                .argName("name")
                .desc("Priority used for each visitor. Defaults to NORMAL_3. " +
                        "Use with care to avoid starving lower prioritized traffic in the cluster")
                .build());

        options.addOption(Option.builder()
                .longOpt("ordering")
                .hasArg(true)
                .argName("order")
                .desc("Order to visit documents in. Only makes sense in conjunction with a document selection involving id.order. Legal values are \"ascending\" and \"descending\"")
                .build());

        options.addOption(Option.builder()
                .longOpt("tracelevel")
                .hasArg(true)
                .argName("level")
                .desc("Tracelevel ([0-9]) to use for debugging purposes")
                .type(Number.class)
                .build());

        options.addOption(Option.builder()
                .longOpt("skipbucketsonfatalerrors")
                .hasArg(false)
                .desc("Skip visiting super buckets with fatal error codes.")
                .build());

        options.addOption(Option.builder()
                .longOpt("jsonoutput")
                .desc("Output documents as JSON")
                .hasArg(false)
                .build());

        options.addOption(Option.builder()
                .longOpt("bucketspace")
                .hasArg(true)
                .argName("space")
                .desc("Bucket space to visit ('" + FixedBucketSpaces.defaultSpace() + "' or '" + FixedBucketSpaces.globalSpace() + "'). " +
                      "If not specified, '" + FixedBucketSpaces.defaultSpace() + "' is used.")
                .build());

        return options;
    }

    public static class VdsVisitParameters {
        private VisitorParameters visitorParameters;
        /** If not specified in options, will get form cluster list */
        private String cluster = null;
        private boolean verbose = false;
        private boolean printIdsOnly = false;
        private String statisticsParts = null;
        private boolean abortOnClusterDown = false;
        private int processTime = 0;
        private int fullTimeout = 7 * 24 * 60 * 60 * 1000;
        private boolean jsonOutput = false;

        public VisitorParameters getVisitorParameters() {
            return visitorParameters;
        }

        public void setVisitorParameters(VisitorParameters visitorParameters) {
            this.visitorParameters = visitorParameters;
        }

        public String getCluster() {
            return cluster;
        }

        public void setCluster(String cluster) {
            this.cluster = cluster;
        }

        public boolean isVerbose() {
            return verbose;
        }

        public void setVerbose(boolean verbose) {
            this.verbose = verbose;
        }

        public boolean isPrintIdsOnly() {
            return printIdsOnly;
        }

        public void setPrintIdsOnly(boolean printIdsOnly) {
            this.printIdsOnly = printIdsOnly;
        }

        public String getStatisticsParts() {
            return statisticsParts;
        }

        public void setStatisticsParts(String statisticsParts) {
            this.statisticsParts = statisticsParts;
        }

        public boolean getAbortOnClusterDown() {
            return abortOnClusterDown;
        }

        public void setAbortOnClusterDown(boolean abortOnClusterDown) {
            this.abortOnClusterDown = abortOnClusterDown;
        }

        public int getFullTimeout() {
            return fullTimeout;
        }

        public void setFullTimeout(int fullTimeout) {
            this.fullTimeout = fullTimeout;
        }

        public int getProcessTime() {
            return processTime;
        }

        public void setProcessTime(int processTime) {
            this.processTime = processTime;
        }

        public void setJsonOutput(boolean jsonOutput) {
            this.jsonOutput = jsonOutput;
        }
    }

    protected static class ArgumentParser {
        private Options options;

        public ArgumentParser(Options options) {
            this.options = options;
        }

        public VdsVisitParameters parse(String args[]) throws org.apache.commons.cli.ParseException {
            VdsVisitParameters allParams = new VdsVisitParameters();
            VisitorParameters params = new VisitorParameters("");
            CommandLineParser parser = new DefaultParser();
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("h")) {
                return null;
            }
            if (line.hasOption("d")) {
                params.setRemoteDataHandler(line.getOptionValue("d"));
            }
            if (line.hasOption("s")) {
                params.setDocumentSelection(line.getOptionValue("s"));
            }
            if (line.hasOption("bucketspace")) {
                params.setBucketSpace(line.getOptionValue("bucketspace"));
            }
            if (line.hasOption("f")) {
                params.setFromTimestamp(((Number) line.getParsedOptionValue("f")).longValue());
            }
            if (line.hasOption("t")) {
                params.setToTimestamp(((Number) line.getParsedOptionValue("t")).longValue());
            }
            if (line.hasOption("e")) {
                params.fieldSet("[header]");
            }
            if (line.hasOption("l")) {
                params.fieldSet(line.getOptionValue("l"));
            }
            if (line.hasOption("visitinconsistentbuckets")) {
                params.visitInconsistentBuckets(true);
            }
            if (line.hasOption("m")) {
                params.setMaxPending(((Number) line.getParsedOptionValue("m")).intValue());
            }
            if (line.hasOption("b")) {
                params.setMaxBucketsPerVisitor(((Number) line.getParsedOptionValue("b")).intValue());
            }
            if (line.hasOption("i")) {
                allParams.setPrintIdsOnly(true);
                params.fieldSet("[id]");
            }
            if (line.hasOption("p")) {
                params.setResumeFileName(line.getOptionValue("p"));
            }
            if (line.hasOption("o")) {
                allParams.setFullTimeout(((Number) line.getParsedOptionValue("o")).intValue());
                params.setTimeoutMs(allParams.getFullTimeout());
            }
            if (line.hasOption("u")) {
                params.setTimeoutMs(((Number) line.getParsedOptionValue("u")).intValue());
            }
            if (line.hasOption("visitlibrary")) {
                params.setVisitorLibrary(line.getOptionValue("visitlibrary"));
            }
            if (line.hasOption("libraryparam")) {
                String key = line.getOptionValues("libraryparam")[0];
                String value = line.getOptionValues("libraryparam")[1];
                params.setLibraryParameter(key, value);
            }
            if (line.hasOption("r")) {
                params.visitRemoves(true);
            }
            if (line.hasOption("c")) {
                allParams.setCluster(line.getOptionValue("c"));
            }

            if (line.hasOption("v")) {
                allParams.setVerbose(true);
            }

            if (line.hasOption("statistics")) {
                allParams.setStatisticsParts(line.getOptionValue("statistics"));
                params.fieldSet("[id]");
                params.setVisitorLibrary("CountVisitor");
            }

            if (line.hasOption("abortonclusterdown")) {
                allParams.setAbortOnClusterDown(true);
            }
            if (line.hasOption("processtime")) {
                allParams.setProcessTime(((Number) line.getParsedOptionValue("processtime")).intValue());
            }
            if (line.hasOption("maxhits")) {
                params.setMaxFirstPassHits(((Number)line.getParsedOptionValue("maxhits")).intValue());
            }
            if (line.hasOption("maxtotalhits")) {
                params.setMaxTotalHits(((Number)line.getParsedOptionValue("maxtotalhits")).intValue());
            }
            if (line.hasOption("tracelevel")) {
                params.setTraceLevel(((Number)line.getParsedOptionValue("tracelevel")).intValue());
            }
            if (line.hasOption("priority")) {
                try {
                    DocumentProtocol.Priority priority = DocumentProtocol.getPriorityByName(
                            line.getOptionValue("priority"));
                    params.setPriority(priority);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown priority name");
                }
            } else {
                // Let bulk visitor jobs have a low priority by default to avoid stalling concurrent
                // (real time) write and read operations.
                params.setPriority(DocumentProtocol.Priority.LOW_1);
            }
            if (line.hasOption("ordering")) {
                String opt = line.getOptionValue("ordering");
                if (opt.equalsIgnoreCase("ascending")) {
                    params.setVisitorOrdering(OrderingSpecification.ASCENDING);
                } else if (opt.equalsIgnoreCase("descending")) {
                    params.setVisitorOrdering(OrderingSpecification.DESCENDING);
                } else {
                    throw new IllegalArgumentException("Unknown ordering. Legal values are \"ascending\", \"descending\"");
                }
            }
            if (line.hasOption("skipbucketsonfatalerrors")) {
                params.skipBucketsOnFatalErrors(true);
            }
            if (line.hasOption("maxpendingsuperbuckets")) {
                StaticThrottlePolicy throttlePolicy = new StaticThrottlePolicy();
                throttlePolicy.setMaxPendingCount(((Number)line.getParsedOptionValue("maxpendingsuperbuckets")).intValue());
                params.setThrottlePolicy(throttlePolicy);
            }
            if (line.hasOption("jsonoutput")) {
                allParams.setJsonOutput(true);
            }

            allParams.setVisitorParameters(params);
            return allParams;
        }
    }

    // For unit testing only
    protected void setVdsVisitParameters(VdsVisitParameters vdsVisitParameters) {
        this.params = vdsVisitParameters;
    }

    protected static String resolveClusterRoute(ClusterList clusters, String wantedCluster) {
        if (clusters.getStorageClusters().size() == 0) {
            throw new IllegalArgumentException("Your Vespa cluster does not have any content clusters " +
                    "declared. Visiting feature is not available.");
        }

        ClusterDef found = null;

        String names = "";
        for (ClusterDef c : clusters.getStorageClusters()) {
            if (!names.isEmpty()) {
                names += ", ";
            }
            names += c.getName();
        }
        if (wantedCluster != null) {
            for (ClusterDef c : clusters.getStorageClusters()) {
                if (c.getName().equals(wantedCluster)) {
                    found = c;
                }
            }
            if (found == null) {
                throw new IllegalArgumentException("Your vespa cluster contains the content clusters " +
                        names + ", not " + wantedCluster + ". Please select a valid vespa cluster.");
            }
        } else if (clusters.getStorageClusters().size() == 1) {
            found = clusters.getStorageClusters().get(0);
        } else {
            throw new IllegalArgumentException("Your vespa cluster contains the content clusters " +
                    names + ". Please use the -c option to select one of them as a target for visiting.");
        }

        return "[Storage:cluster=" + found.getName() + ";clusterconfigid=" + found.getConfigId() + "]";
    }

    protected static void verbosePrintParameters(VdsVisitParameters vdsParams, PrintStream out) {
        VisitorParameters params = vdsParams.getVisitorParameters();
        if (params.getTimeoutMs() != -1) {
            out.println("Time out visitor after " + params.getTimeoutMs() + " ms.");
        }
        if (params.getDocumentSelection() == null || params.getDocumentSelection().equals("")) {
            out.println("Visiting all documents");
        } else {
            out.println("Visiting documents matching: " + params.getDocumentSelection());
        }
        out.println(String.format("Visiting bucket space: %s", params.getBucketSpace()));
        if (params.getFromTimestamp() != 0 && params.getToTimestamp() != 0) {
            out.println("Visiting in the inclusive timestamp range "
                               + params.getFromTimestamp() + " - " + params.getToTimestamp() + ".");
        } else if (params.getFromTimestamp() != 0) {
            out.println("Visiting from and including timestamp " + params.getFromTimestamp() + ".");
        } else if (params.getToTimestamp() != 0) {
            out.println("Visiting to and including timestamp " + params.getToTimestamp() + ".");
        }
        out.println("Visiting field set " + params.fieldSet() + ".");
        if (params.visitInconsistentBuckets()) {
            out.println("Visiting inconsistent buckets.");
        }
        if (params.visitRemoves()) {
            out.println("Including remove entries.");
        }
        if (params.getResumeFileName() != null && !"".equals(params.getResumeFileName())) {
            out.println("Tracking progress in file: " + params.getResumeFileName());
        }
        if (vdsParams.isPrintIdsOnly()) {
            out.println("Only showing document identifiers.");
        }
        out.println("Let visitor have maximum " + params.getMaxPending() + " replies pending on data handlers per storage node visitor.");
        out.println("Visit maximum " + params.getMaxBucketsPerVisitor() + " buckets per visitor.");
        if (params.getRemoteDataHandler() != null) {
            out.println("Sending data to data handler at: " + params.getRemoteDataHandler());
        }
        if (params.getRoute() != null) {
            out.println("Visiting cluster '" + params.getRoute() + "'.");
        }
        if (params.getVisitorLibrary() != null) {
            out.println("Using visitor library '" + params.getVisitorLibrary() + "'.");
        }
        if (params.getLibraryParameters().size() > 0) {
            out.println("Adding the following library specific parameters:");
            for (Map.Entry<String, byte[]> entry : params.getLibraryParameters().entrySet()) {
                out.println("  " + entry.getKey() + " = " +
                        new String(entry.getValue(), Charset.forName("utf-8")));
            }
        }
        if (params.getPriority() != DocumentProtocol.Priority.NORMAL_3) {
            out.println("Visitor priority " + params.getPriority().name());
        }
        if (params.skipBucketsOnFatalErrors()) {
            out.println("Skip visiting super buckets with fatal errors.");
        }
    }

    private void onDocumentSelectionException(Exception e) {
        System.err.println("Illegal document selection string '" +
                params.getVisitorParameters().getDocumentSelection() + "'.\n");
        System.exit(1);
    }

    private void onIllegalArgumentException(Exception e) {
        System.err.println("Illegal arguments : \n");
        System.err.println(e.getMessage());
        System.exit(1);
    }

    public void run() {
        System.exit(doRun());
    }

    protected int doRun() {
        VisitorParameters visitorParameters = params.getVisitorParameters();
        // If progress file already exists, create resume token from it
        if (visitorParameters.getResumeFileName() != null &&
            !"".equals(visitorParameters.getResumeFileName()))
        {
            try {
                File file = new File(visitorParameters.getResumeFileName());
                FileInputStream fos = new FileInputStream(file);

                StringBuilder builder = new StringBuilder();
                byte[] b = new byte[100000];
                int length;

                while ((length = fos.read(b)) > 0) {
                    builder.append(new String(b, 0, length));
                }
                fos.close();
                visitorParameters.setResumeToken(new ProgressToken(builder.toString()));

                if (params.isVerbose()) {
                    System.err.format("Resuming visitor already %.1f %% finished.\n",
                            visitorParameters.getResumeToken().percentFinished());
                }
            } catch (FileNotFoundException e) {
                // Ignore; file has not been created yet but will be shortly.
            } catch (IOException e) {
                System.err.println("Could not open progress file: " + visitorParameters.getResumeFileName());
                e.printStackTrace(System.err);
                return 1;
            }
        }

        initShutdownHook();
        sessionAccessor = sessionAccessorFactory.createVisitorSessionAccessor();

        VdsVisitHandler handler;

        handler = new StdOutVisitorHandler(
                params.isPrintIdsOnly(),
                params.isVerbose(),
                params.isVerbose(),
                params.isVerbose(),
                params.getStatisticsParts() != null,
                params.getAbortOnClusterDown(),
                params.getProcessTime(),
                params.jsonOutput);

        if (visitorParameters.getResumeFileName() != null) {
            handler.setProgressFileName(visitorParameters.getResumeFileName());
        }

        visitorParameters.setControlHandler(handler.getControlHandler());
        if (visitorParameters.getRemoteDataHandler() == null) {
            visitorParameters.setLocalDataHandler(handler.getDataHandler());
        }

        if (params.getStatisticsParts() != null) {
            String[] parts = params.getStatisticsParts().split(",");
            for (String s : parts) {
                visitorParameters.setLibraryParameter(s, "true");
            }
        }

        try {
            session = sessionAccessor.createVisitorSession(visitorParameters);
            while (true) {
                try {
                    if (session.waitUntilDone(params.getFullTimeout())) break;
                } catch (InterruptedException e) {}
            }

            if (visitorParameters.getTraceLevel() > 0) {
                System.out.println(session.getTrace().toString());
            }
        } catch (ParseException e) {
            onDocumentSelectionException(e);
        } catch (IllegalArgumentException e) {
            onIllegalArgumentException(e);
        } catch (Exception e) {
            System.err.println("Document selection string was: " + visitorParameters.getDocumentSelection());
            System.err.println("Caught unexpected exception: ");
            e.printStackTrace(System.err);
            return 1;
        }
        if (visitorParameters.getControlHandler().getResult().code
                == VisitorControlHandler.CompletionCode.SUCCESS)
        {
            return 0;
        } else {
            return 1;
        }
    }

    private void initShutdownHook() {
        shutdownHookRegistrar.registerShutdownHook(new CleanUpThread());
    }

    class CleanUpThread extends Thread {
        public void run() {
            try {
                if (session != null) {
                    session.destroy();
                }
            } catch (IllegalStateException ise) {
                //ignore this
            }
            try {
                if (sessionAccessor != null) {
                    sessionAccessor.shutdown();
                }
            } catch (IllegalStateException ise) {
                //ignore this too
            }
        }
    }
}
