// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Properties;

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

    private CliClient(PrintStream systemOut, PrintStream systemError, InputStream systemIn, Properties systemProperties) {
        this.systemOut = systemOut;
        this.systemError = systemError;
        this.systemIn = systemIn;
        this.systemProperties = systemProperties;
    }

    public static void main(String[] args) {
        CliClient client = new CliClient(System.out, System.err, System.in, System.getProperties());
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
                 JsonStreamFeeder feeder = createJsonFeeder(cliArgs)) {
                if (cliArgs.benchmarkModeEnabled()) {
                    printBenchmarkResult(feeder.benchmark(in));
                } else {
                    feeder.feed(in);
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
        cliArgs.connections().ifPresent(builder::setMaxConnections);
        cliArgs.maxStreamsPerConnection().ifPresent(builder::setMaxConnections);
        if (cliArgs.sslHostnameVerificationDisabled()) {
            builder.setHostnameVerifier(AcceptAllHostnameVerifier.INSTANCE);
        }
        cliArgs.certificateAndKey().ifPresent(c -> builder.setCertificate(c.certificateFile, c.privateKeyFile));
        cliArgs.caCertificates().ifPresent(builder::setCaCertificates);
        cliArgs.headers().forEach(builder::addRequestHeader);
        return builder.build();
    }

    private static JsonStreamFeeder createJsonFeeder(CliArguments cliArgs) throws CliArguments.CliArgumentsException, IOException {
        FeedClient feedClient = createFeedClient(cliArgs);
        JsonStreamFeeder.Builder builder = JsonStreamFeeder.builder(feedClient);
        cliArgs.timeout().ifPresent(builder::withTimeout);
        cliArgs.route().ifPresent(builder::withRoute);
        cliArgs.traceLevel().ifPresent(builder::withTracelevel);
        return builder.build();
    }

    private InputStream createFeedInputStream(CliArguments cliArgs) throws CliArguments.CliArgumentsException, IOException {
        return cliArgs.readFeedFromStandardInput() ? systemIn : Files.newInputStream(cliArgs.inputFile().get());
    }

    private void printBenchmarkResult(JsonStreamFeeder.BenchmarkResult result) throws IOException {
        JsonFactory factory = new JsonFactory();
        try (JsonGenerator generator = factory.createGenerator(systemOut).useDefaultPrettyPrinter()) {
            generator.writeStartObject();
            generator.writeNumberField("feeder.runtime", result.duration.toMillis());
            generator.writeNumberField("feeder.okcount", result.okCount);
            generator.writeNumberField("feeder.errorcount", result.errorCount);
            generator.writeNumberField("feeder.throughput", result.throughput);
            generator.writeEndObject();
        }
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
        return Boolean.parseBoolean(systemProperties.getProperty("VESPA_DEBUG", Boolean.FALSE.toString()));
    }

    private static class AcceptAllHostnameVerifier implements HostnameVerifier {
        static final AcceptAllHostnameVerifier INSTANCE = new AcceptAllHostnameVerifier();
        @Override public boolean verify(String hostname, SSLSession session) { return true; }
    }
}
