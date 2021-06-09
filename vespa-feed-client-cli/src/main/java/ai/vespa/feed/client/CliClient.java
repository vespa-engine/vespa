// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main method for CLI interface
 *
 * @author bjorncs
 */
public class CliClient {

    private final PrintStream systemOut;
    private final PrintStream systemError;
    private final InputStream systemIn;
    private final Properties systemProperties;
    private final Map<String, String> environmentVariables;

    private CliClient(PrintStream systemOut, PrintStream systemError, InputStream systemIn,
                      Properties systemProperties, Map<String, String> environmentVariables) {
        this.systemOut = systemOut;
        this.systemError = systemError;
        this.systemIn = systemIn;
        this.systemProperties = systemProperties;
        this.environmentVariables = environmentVariables;
    }

    public static void main(String[] args) {
        CliClient client = new CliClient(System.out, System.err, System.in, System.getProperties(), System.getenv());
        int exitCode = client.run(args);
        System.exit(exitCode);
    }

    private int run(String[] rawArgs) {
        CliArguments cliArgs = null;
        try {
            cliArgs = CliArguments.fromRawArgs(rawArgs);
            if (cliArgs.helpSpecified()) {
                cliArgs.printHelp(systemOut);
                return 0;
            }
            if (cliArgs.versionSpecified()) {
                systemOut.println(Vespa.VERSION);
                return 0;
            }
            try (InputStream in = createFeedInputStream(cliArgs);
                 JsonFeeder feeder = createJsonFeeder(cliArgs)) {
                if (cliArgs.benchmarkModeEnabled()) {
                    BenchmarkResultAggregator aggregator = new BenchmarkResultAggregator();
                    feeder.feedMany(in, aggregator).join();
                    aggregator.printBenchmarkResult();
                } else {
                    JsonFeeder.ResultCallback emptyCallback = new JsonFeeder.ResultCallback() {
                        @Override public void onNextResult(Result result, Throwable error) {}
                        @Override public void onError(Throwable error) {}
                        @Override public void onComplete() {}
                    };
                    feeder.feedMany(in, emptyCallback).join();
                }
            }
            return 0;
        } catch (CliArguments.CliArgumentsException | IOException e) {
            boolean verbose = cliArgs != null && cliArgs.verboseSpecified();
            return handleException(verbose, e);
        } catch (Exception e) {
            boolean verbose = cliArgs != null && cliArgs.verboseSpecified();
            return handleException(verbose, "Unknown failure: " + e.getMessage(), e);
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
        cliArgs.caCertificates().ifPresent(builder::setCaCertificates);
        cliArgs.headers().forEach(builder::addRequestHeader);
        return builder.build();
    }

    private static JsonFeeder createJsonFeeder(CliArguments cliArgs) throws CliArguments.CliArgumentsException, IOException {
        FeedClient feedClient = createFeedClient(cliArgs);
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
        if (debugMode() || verbose) {
            exception.printStackTrace(systemError);
        }
        return 1;
    }

    private boolean debugMode() {
        boolean enabledWithSystemProperty = Boolean.parseBoolean(systemProperties.getProperty("VESPA_DEBUG", Boolean.FALSE.toString()));
        boolean enabledWithEnvironmentVariable = Optional.ofNullable(environmentVariables.get("VESPA_DEBUG"))
                .map(Boolean::parseBoolean).orElse(false);
        return enabledWithSystemProperty || enabledWithEnvironmentVariable;
    }

    private static class AcceptAllHostnameVerifier implements HostnameVerifier {
        static final AcceptAllHostnameVerifier INSTANCE = new AcceptAllHostnameVerifier();
        @Override public boolean verify(String hostname, SSLSession session) { return true; }
    }

    private class BenchmarkResultAggregator implements JsonFeeder.ResultCallback {

        private final AtomicInteger okCount = new AtomicInteger();
        private final AtomicInteger errorCount = new AtomicInteger();
        private volatile long endNanoTime;
        private volatile long startNanoTime;

        void start() { this.startNanoTime = System.nanoTime(); }

        void printBenchmarkResult() throws IOException {
            JsonFactory factory = new JsonFactory();
            Duration duration = Duration.ofNanos(endNanoTime - startNanoTime);
            int okCount = this.okCount.get();
            int errorCount = this.errorCount.get();
            double throughput = (double) okCount / duration.toMillis() * 1000D;
            try (JsonGenerator generator = factory.createGenerator(systemOut).useDefaultPrettyPrinter()) {
                generator.writeStartObject();
                generator.writeNumberField("feeder.runtime", duration.toMillis());
                generator.writeNumberField("feeder.okcount", okCount);
                generator.writeNumberField("feeder.errorcount", errorCount);
                generator.writeNumberField("feeder.throughput", throughput);
                generator.writeEndObject();
            }
        }

        @Override
        public void onNextResult(Result result, Throwable error) {
            if (error != null) {
                errorCount.incrementAndGet();
            } else {
                okCount.incrementAndGet();
            }
        }

        @Override public void onError(Throwable error) {}

        @Override public void onComplete() { this.endNanoTime = System.nanoTime(); }
    }
}
