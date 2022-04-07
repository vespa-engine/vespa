// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import java.util.logging.Level;
import com.yahoo.log.LogSetup;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.net.HostName;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.lang.reflect.Constructor;
import java.util.logging.Logger;

/**
 * Example client using visiting
 *
 * @author Einar M R Rosenvinge
 */
public class VdsVisitTarget {

    private static final Logger log = Logger.getLogger(VdsVisitTarget.class.getName());

    private boolean printIds = false;
    DocumentAccess access;
    VisitorDestinationSession session;
    String slobrokAddress = null;
    int port = -1;
    private boolean verbose = false;
    private int processTime = 0;
    private String handlerClassName = StdOutVisitorHandler.class.getName();
    private String[] handlerArgs = null;

    public boolean isPrintIds() {
        return printIds;
    }

    public String getSlobrokAddress() {
        return slobrokAddress;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public int getPort() {
        return port;
    }

    public int getProcessTime() {
        return processTime;
    }

    public String getHandlerClassName() {
        return handlerClassName;
    }

    public String[] getHandlerArgs() {
        return handlerArgs;
    }

    public static void main(String args[]) {
        LogSetup.initVespaLogging("vespa-visit-target");
        VdsVisitTarget visitTarget = new VdsVisitTarget();


        try {
            visitTarget.parseArguments(args);
            visitTarget.initShutdownHook();
            visitTarget.run();
            System.exit(0);
        } catch (HelpShownException e) {
            System.exit(0);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println("Failed to parse arguments. Try --help for syntax. " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption("h", "help", false, "Show this syntax page.");

        options.addOption(Option.builder("s")
                .longOpt("bindtoslobrok")
                .hasArg(true)
                .argName("address")
                .desc("Bind to the given slobrok address.")
                .build());

        options.addOption(Option.builder("t")
                .longOpt("bindtosocket")
                .hasArg(true)
                .argName("port")
                .desc("Bind to the given TCP port")
                .type(Number.class)
                .build());

        options.addOption(Option.builder("p")
                .longOpt("processtime")
                .hasArg(true)
                .argName("msecs")
                .desc("Sleep this amount of millisecs before processing message. (Debug option for pretending to be slow client).")
                .type(Number.class)
                .build());

        options.addOption(Option.builder("c")
                .longOpt("visithandler")
                .hasArg(true)
                .argName("classname")
                .desc("Use the given class as a visit handler (defaults to StdOutVisitorHandler)")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("visitoptions")
                .hasArg(true)
                .argName("args")
                .desc("Option arguments to pass through to the visitor handler instance")
                .build());

        options.addOption("i", "printids", false, "Display only document identifiers.");
        options.addOption("v", "verbose", false, "Indent XML, show progress and info on STDERR.");

        return options;
    }

    private void printSyntax(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("vespa-visit-target <options>", "Retrieve results from a visitor", options ,
                            "One, and only one, of the binding options must be present.\n" +
                            "\n" +
                            "For more detailed information, such as defaults and format of\n" +
                            "arguments, refer to 'man vespa-visit-target'.\n");
    }

    class HelpShownException extends Exception {}

    void parseArguments(String args[]) throws ParseException, HelpShownException {
        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);

        if (line.hasOption("h")) {
            printSyntax(options);
            throw new HelpShownException();
        }
        if (line.hasOption("s")) {
            slobrokAddress = line.getOptionValue("s");
        }
        if (line.hasOption("t")) {
            port = ((Number) line.getParsedOptionValue("t")).intValue();
        }
        if (line.hasOption("i")) {
            printIds = true;
        }
        if (line.hasOption("p")) {
            processTime = ((Number) line.getParsedOptionValue("p")).intValue();
        }
        if (line.hasOption("v")) {
            verbose = true;
        }
        if (line.hasOption("c")) {
            handlerClassName = line.getOptionValue("c");
        }
        if (line.hasOption("o")) {
            handlerArgs = line.getOptionValue("o").split(" ");
        }

        if (!(slobrokAddress == null ^ port == -1)) {
            throw new IllegalArgumentException("You must specify one, and only one, binding option");
        }
        if (port != -1 && port < 0 || port > 65535) {
            throw new IllegalArgumentException("The port must be in the range 0-65535");
        }
        if (verbose) {
            if (port != -1) {
                System.err.println("Binding to socket " + getTcpAddress());
            } else {
                System.err.println("Binding to slobrok address: " + slobrokAddress + "/visit-destination");
            }
        }
    }

    private String getTcpAddress() {
        String hostname = HostName.getLocalhost();
        return "tcp/" + hostname + ":" + port + "/visit-destination";
    }

    @SuppressWarnings("unchecked")
    public void run() throws Exception {
        initShutdownHook();
        log.log(Level.FINE, "Starting VdsVisitTarget");
        MessageBusParams mbusParams = new MessageBusParams();
        mbusParams.getRPCNetworkParams().setIdentity(new Identity(slobrokAddress));

        if (port > 0) {
            mbusParams.getRPCNetworkParams().setListenPort(port);
        }

        access = new MessageBusDocumentAccess(mbusParams);

        VdsVisitHandler handler;

        Class<?> cls = Thread.currentThread().getContextClassLoader()
                .loadClass(handlerClassName);
        try {
            // Any custom data handlers may have a constructor that takes in args,
            // so that the user can pass cmd line options to them
            Class<?>[] consTypes = new Class<?>[] { boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class,
                    boolean.class, int.class, String[].class };
            Constructor<?> cons = cls.getConstructor(consTypes);
            handler = (VdsVisitHandler)cons.newInstance(
                    printIds, verbose, verbose, verbose, false, false,
                    processTime, handlerArgs);
        } catch (NoSuchMethodException e) {
            // Retry, this time matching the StdOutVisitorHandler constructor
            // arg list
            Class<?>[] consTypes = new Class<?>[] { boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class,
                    boolean.class, int.class, boolean.class };
            Constructor<?> cons = cls.getConstructor(consTypes);
            handler = (VdsVisitHandler)cons.newInstance(
                    printIds, verbose, verbose, verbose, false, false, processTime, false);
        }

        VisitorDataHandler dataHandler = handler.getDataHandler();
        VisitorControlHandler controlHandler = handler.getControlHandler();

        VisitorDestinationParameters params = new VisitorDestinationParameters(
                "visit-destination", dataHandler);
        session = access.createVisitorDestinationSession(params);
        while (!controlHandler.isDone()) {
            Thread.sleep(1000);
        }
    }

    private void initShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new CleanUpThread());
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
                if (access != null) {
                    access.shutdown();
                }
            } catch (IllegalStateException ise) {
                //ignore this too
            }
        }
    }

}
