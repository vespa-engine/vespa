// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.JsonFeeder.ResultCallback;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main method for CLI interface
 *
 * @author bjorncs
 */
public class CliClient {

    private static final JsonFactory factory = new JsonFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

    private final PrintStream systemOut;
    private final PrintStream systemError;
    private final InputStream systemIn;
    private final Object printMonitor = new Object();

    private CliClient(PrintStream systemOut, PrintStream systemError, InputStream systemIn) {
        this.systemOut = systemOut;
        this.systemError = systemError;
        this.systemIn = systemIn;
    }

    public static void main(String[] args) {
        CliClient client = new CliClient(System.out, System.err, System.in);
        int exitCode = client.run(args);
        System.exit(exitCode);
    }

    private int run(String[] rawArgs) {
        boolean verbose = false;
        try {
            CliArguments cliArgs = CliArguments.fromRawArgs(rawArgs);
            verbose = cliArgs.verboseSpecified();
            if (cliArgs.helpSpecified()) {
                cliArgs.printHelp(systemOut);
                return 0;
            }
            if (cliArgs.versionSpecified()) {
                systemOut.println(Vespa.VERSION);
                return 0;
            }
            try (InputStream in = createFeedInputStream(cliArgs);
                 FeedClient feedClient = createFeedClient(cliArgs);
                 JsonFeeder feeder = createJsonFeeder(feedClient, cliArgs)) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<FeedException> fatal = new AtomicReference<>();
                AtomicLong successes = new AtomicLong();
                AtomicLong failures = new AtomicLong();
                long startNanos = System.nanoTime();
                if (cliArgs.showProgress()) {
                    Thread progressPrinter = new Thread(() -> {
                        try {
                            while ( ! latch.await(10, TimeUnit.SECONDS)) {
                                synchronized (printMonitor) {
                                    printBenchmarkResult(System.nanoTime() - startNanos, successes.get(), failures.get(), feedClient.stats(), systemError);
                                }
                            }
                        }
                        catch (InterruptedException | IOException ignored) { } // doesn't happen
                    }, "progress-printer");
                    progressPrinter.setDaemon(true);
                    progressPrinter.start();
                }

                feeder.feedMany(in, new ResultCallback() {
                    @Override public void onNextResult(Result result, FeedException error) { handleResult(result, error, successes, failures, cliArgs); }
                    @Override public void onError(FeedException error) { fatal.set(error); latch.countDown(); }
                    @Override public void onComplete() { latch.countDown(); }
                });
                latch.await();

                if (cliArgs.benchmarkModeEnabled()) {
                    printBenchmarkResult(System.nanoTime() - startNanos, successes.get(), failures.get(), feedClient.stats(), systemOut);
                }
                if (fatal.get() != null) throw fatal.get();
            }
            return 0;
        } catch (CliArguments.CliArgumentsException | IOException | FeedException e) {
            return handleException(verbose, e);
        } catch (Exception e) {
            return handleException(verbose, "Unknown failure: " + e.getMessage(), e);
        }
    }

    private void handleResult(Result result, FeedException error, AtomicLong successes, AtomicLong failures, CliArguments args) {
        if (error != null) {
            failures.incrementAndGet();
            if (args.showErrors()) synchronized (printMonitor) {
                systemError.println(error.getMessage());
                if (error instanceof ResultException) ((ResultException) error).getTrace().ifPresent(systemError::println);
                if (args.verboseSpecified()) error.printStackTrace(systemError);
            }
        }
        else {
            successes.incrementAndGet();
            if (args.showSuccesses()) synchronized (printMonitor) {
                systemError.println(result.documentId() + ": " + result.type());
                result.traceMessage().ifPresent(systemError::println);
                result.resultMessage().ifPresent(systemError::println);
            }
        }
    }

    private static FeedClient createFeedClient(CliArguments cliArgs) throws CliArguments.CliArgumentsException {
        FeedClientBuilder builder = FeedClientBuilder.create(cliArgs.endpoint());
        cliArgs.connections().ifPresent(builder::setConnectionsPerEndpoint);
        cliArgs.maxStreamsPerConnection().ifPresent(builder::setMaxStreamPerConnection);
        if (cliArgs.sslHostnameVerificationDisabled()) {
            builder.setHostnameVerifier(AcceptAllHostnameVerifier.INSTANCE);
        }
        cliArgs.certificateAndKey().ifPresent(c -> builder.setCertificate(c.certificateFile, c.privateKeyFile));
        cliArgs.caCertificates().ifPresent(builder::setCaCertificatesFile);
        cliArgs.headers().forEach(builder::addRequestHeader);
        builder.setDryrun(cliArgs.dryrunEnabled());
        return builder.build();
    }

    private static JsonFeeder createJsonFeeder(FeedClient feedClient, CliArguments cliArgs) throws CliArguments.CliArgumentsException, IOException {
        JsonFeeder.Builder builder = JsonFeeder.builder(feedClient);
        cliArgs.timeout().ifPresent(builder::withTimeout);
        cliArgs.route().ifPresent(builder::withRoute);
        cliArgs.traceLevel().ifPresent(builder::withTracelevel);
        return builder.build();
    }

    private InputStream createFeedInputStream(CliArguments cliArgs) throws CliArguments.CliArgumentsException, IOException {
        return cliArgs.readFeedFromStandardInput() ? systemIn : Files.newInputStream(cliArgs.inputFile().get());
    }

    private int handleException(boolean verbose, Exception e) { return handleException(verbose, e.getMessage(), e); }

    private int handleException(boolean verbose, String message, Exception exception) {
        systemError.println(message);
        if (verbose) {
            exception.printStackTrace(systemError);
        }
        return 1;
    }

    private static class AcceptAllHostnameVerifier implements HostnameVerifier {
        static final AcceptAllHostnameVerifier INSTANCE = new AcceptAllHostnameVerifier();
        @Override public boolean verify(String hostname, SSLSession session) { return true; }
    }

    static void printBenchmarkResult(long durationNanos, long successes, long failures,
                                     OperationStats stats, OutputStream systemOut) throws IOException {
        try (JsonGenerator generator = factory.createGenerator(systemOut).useDefaultPrettyPrinter()) {
            generator.writeStartObject();

            writeFloatField(generator, "feeder.seconds", durationNanos * 1e-9, 3);
            generator.writeNumberField("feeder.ok.count", successes);
            writeFloatField(generator, "feeder.ok.rate", successes * 1e9 / Math.max(1, durationNanos), 3);
            generator.writeNumberField("feeder.error.count", failures);
            generator.writeNumberField("feeder.inflight.count", stats.inflight());

            generator.writeNumberField("http.request.count", stats.requests());
            generator.writeNumberField("http.request.bytes", stats.bytesSent());

            generator.writeNumberField("http.exception.count", stats.exceptions());

            generator.writeNumberField("http.response.count", stats.responses());
            generator.writeNumberField("http.response.bytes", stats.bytesReceived());
            generator.writeNumberField("http.response.error.count", stats.responses() - stats.successes());
            writeFloatField(generator, "http.response.latency.millis.min", stats.minLatencyMillis(), 3);
            writeFloatField(generator, "http.response.latency.millis.avg", stats.averageLatencyMillis(), 3);
            writeFloatField(generator, "http.response.latency.millis.max", stats.maxLatencyMillis(), 3);

            generator.writeObjectFieldStart("http.response.code.counts");
            for (Map.Entry<Integer, Long> entry : stats.responsesByCode().entrySet())
                generator.writeNumberField(Integer.toString(entry.getKey()), entry.getValue());
            generator.writeEndObject();

            generator.writeEndObject();
        }
    }

    private static void writeFloatField(JsonGenerator generator, String name, double value, int precision) throws IOException {
        generator.writeFieldName(name);
        generator.writeNumber(String.format("%." + precision + "f", value));
    }

}
