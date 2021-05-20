// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Main method for CLI interface
 *
 * @author bjorncs
 */
class CliClient {

    private final PrintStream systemOut;
    private final PrintStream systemError;
    private final Properties systemProperties;

    CliClient(PrintStream systemOut, PrintStream systemError, Properties systemProperties) {
        this.systemOut = systemOut;
        this.systemError = systemError;
        this.systemProperties = systemProperties;
    }

    public static void main(String[] args) {
        CliClient client = new CliClient(System.out, System.err, System.getProperties());
        int exitCode = client.run(args);
        System.exit(exitCode);
    }

    int run(String[] rawArgs) {
        try {
            CliArguments cliArgs = CliArguments.fromRawArgs(rawArgs);
            if (cliArgs.helpSpecified()) {
                cliArgs.printHelp(systemOut);
                return 0;
            }
            if (cliArgs.versionSpecified()) {
                systemOut.println(Vespa.VERSION);
                return 0;
            }
            FeedClient feedClient = createFeedClient(cliArgs);
            return 0;
        } catch (CliArguments.CliArgumentsException | IOException e) {
            return handleException(e);
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

    private int handleException(Exception e) { return handleException(e.getMessage(), e); }

    private int handleException(String message, Exception exception) {
        systemError.println(message);
        if (debugMode()) {
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
