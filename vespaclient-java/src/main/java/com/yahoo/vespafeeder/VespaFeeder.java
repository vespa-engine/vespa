// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedhandler.FeedResponse;
import com.yahoo.feedhandler.NullFeedMetric;
import com.yahoo.feedhandler.VespaFeedHandler;
import com.yahoo.log.LogSetup;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.vespaclient.ClusterList;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class VespaFeeder {

    Arguments args;
    DocumentTypeManager manager;
    Executor threadPool = Executors.newCachedThreadPool(ThreadFactoryFactory.getThreadFactory("vespa-feeder"));

    public VespaFeeder(Arguments args, DocumentTypeManager manager) {
        this.args = args;
        this.manager = manager;
    }

    public static class FeedErrorException extends Exception {
        String message;

        public FeedErrorException(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }

    }

    static FeedErrorException renderErrors(List<String> errors) {
        StringBuilder buffer = new StringBuilder();

        if (!errors.isEmpty()) {
            String headline = (errors.size() > 10) ? "First 10 errors (of " + errors.size() + "):" : "Errors:";
            buffer.append(headline).append("\n");
            for (int i = 0; i < headline.length(); ++i) {
                buffer.append("-");
            }
            buffer.append("\n");
            for (int i = 0; i < errors.size() && i < 10; ++i) {
                buffer.append("    ").append(errors.get(i)).append("\n");
            }
        }

        return new FeedErrorException(buffer.toString());
    }

    public RouteMetricSet.ProgressCallback createProgressCallback(PrintStream output) {
        if ("benchmark".equals(args.getMode())) {
            return new BenchmarkProgressPrinter(SystemTimer.INSTANCE, output);
        } else {
            return new ProgressPrinter(SystemTimer.INSTANCE, output);
        }
    }

    void parseFiles(InputStream stdin, PrintStream output) throws Exception {
        FeedContext context = new FeedContext(
                args.getPropertyProcessor(),
                args.getSessionFactory(),
                manager,
                new ClusterList(), new NullFeedMetric());

        final BufferedInputStream input = new BufferedInputStream(stdin);
        VespaFeedHandler handler = VespaFeedHandler.createFromContext(context, threadPool);

        if (args.getFiles().isEmpty()) {
            InputStreamRequest req = new InputStreamRequest(input);
            setProperties(req, input);
            FeedResponse response = (FeedResponse)handler.handle(req.toRequest(), createProgressCallback(output));
            if ( ! response.isSuccess()) {
                throw renderErrors(response.getErrorList());
            }
        } else {
            if (args.isVerbose()) {
                for (String fileName : args.getFiles()) {
                    long thisSize = new File(fileName).length();
                    output.println("Size of file '" + fileName + "' is " + thisSize + " B.");
                }
            }

            for (String fileName : args.getFiles()) {
                File f = new File(fileName);
                FileRequest req = new FileRequest(f);
                final BufferedInputStream inputSnooper = new BufferedInputStream(new FileInputStream(fileName));
                setProperties(req, inputSnooper);
                inputSnooper.close();
                FeedResponse response = (FeedResponse)handler.handle(req.toRequest(), createProgressCallback(output));
                if (!response.isSuccess()) {
                    throw renderErrors(response.getErrorList());
                }
            }
        }
    }

    // use BufferedInputStream to enforce the input.markSupported() == true
    private void setProperties(InputStreamRequest req, BufferedInputStream input) throws IOException {
        setPriority(req);
        setCreateIfNonExistent(req);
        setJsonInput(req, input);
        req.setProperty("totaltimeout", "-1");
    }

    private void setPriority(InputStreamRequest req) {
        if (args.getPriority() != null) {
            req.setProperty("priority", args.getPriority());
        }
    }

    private void setCreateIfNonExistent(InputStreamRequest req) {
        if (args.getFeederConfig().createifnonexistent()) {
            req.setProperty("createifnonexistent", "true");
        }
    }

    // package access for easy testing
    static void setJsonInput(InputStreamRequest req, BufferedInputStream input) throws IOException {
        input.mark(4);
        int b = input.read();
        input.reset();
        // A valid JSON feed will always start with '['
        if (b == '[') {
            req.setProperty(VespaFeedHandler.JSON_INPUT, Boolean.TRUE.toString());
        } else {
            req.setProperty(VespaFeedHandler.JSON_INPUT, Boolean.FALSE.toString());
        }
    }

    public static void main(String[] args) {
        LogSetup.initVespaLogging("vespa-feeder");

        try {
            Arguments arguments = new Arguments(args, null);

            DocumentTypeManager manager = new DocumentTypeManager();
            DocumentTypeManagerConfigurer.configure(manager, "client").close();

            VespaFeeder feeder = new VespaFeeder(arguments, manager);
            feeder.parseFiles(System.in, System.out);
            System.exit(0);
        } catch (Arguments.HelpShownException e) {
            System.exit(0);
        } catch (IllegalArgumentException e) {
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("Could not open file " + e.getMessage());
            System.exit(1);
        } catch (FeedErrorException e) {
            System.err.println("\n" + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Got exception " + e.getMessage() + ", aborting feed.");
            System.exit(1);
        }
    }

}
