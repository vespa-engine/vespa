// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
            try (InputStream in = createFeedInputStream(cliArgs); JsonStreamFeeder feeder = createJsonFeeder(cliArgs)) {
                feeder.feed(in);
            }
            return 0;
        } catch (CliArguments.CliArgumentsException | IOException e) {
            boolean verbose = cliArgs != null && cliArgs.verboseSpecified();
            return handleException(verbose, e);
        }
    }

    private static FeedClient createFeedClient(CliArguments cliArgs) throws CliArguments.CliArgumentsException, IOException {
        FeedClientBuilder builder = FeedClientBuilder.create(cliArgs.endpoint());
        cliArgs.connections().ifPresent(builder::setMaxConnections);
        cliArgs.maxStreamsPerConnection().ifPresent(builder::setMaxConnections);
        if (cliArgs.sslHostnameVerificationDisabled()) {
            builder.setHostnameVerifier(AcceptAllHostnameVerifier.INSTANCE);
        }
        CliArguments.CertificateAndKey certificateAndKey = cliArgs.certificateAndKey().orElse(null);
        Path caCertificates = cliArgs.caCertificates().orElse(null);
        if (certificateAndKey != null || caCertificates != null) {
            SslContextBuilder sslContextBuilder = new SslContextBuilder();
            if (certificateAndKey != null) {
                sslContextBuilder.withCertificateAndKey(certificateAndKey.certificateFile, certificateAndKey.privateKeyFile);
            }
            if (caCertificates != null) {
                sslContextBuilder.withCaCertificates(caCertificates);
            }
            builder.setSslContext(sslContextBuilder.build());
        }
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
        return new BufferedInputStream(
                cliArgs.readFeedFromStandardInput() ? systemIn : Files.newInputStream(cliArgs.inputFile().get()));
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
