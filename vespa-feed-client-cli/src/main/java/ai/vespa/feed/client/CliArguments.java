// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Parses command line arguments
 *
 * @author bjorncs
 */
class CliArguments {

    private static final Options optionsDefinition = createOptions();

    private static final String BENCHMARK_OPTION = "benchmark";
    private static final String CA_CERTIFICATES_OPTION = "ca-certificates";
    private static final String CERTIFICATE_OPTION = "certificate";
    private static final String CONNECTIONS_OPTION = "connections";
    private static final String DISABLE_SSL_HOSTNAME_VERIFICATION_OPTION = "disable-ssl-hostname-verification";
    private static final String ENDPOINT_OPTION = "endpoint";
    private static final String FILE_OPTION = "file";
    private static final String HEADER_OPTION = "header";
    private static final String HELP_OPTION = "help";
    private static final String MAX_STREAMS_PER_CONNECTION = "max-streams-per-connection";
    private static final String PRIVATE_KEY_OPTION = "private-key";
    private static final String ROUTE_OPTION = "route";
    private static final String TIMEOUT_OPTION = "timeout";
    private static final String TRACE_OPTION = "trace";
    private static final String VERBOSE_OPTION = "verbose";
    private static final String VERSION_OPTION = "version";
    private static final String STDIN_OPTION = "stdin";

    private final CommandLine arguments;

    private CliArguments(CommandLine arguments) throws CliArgumentsException {
        validateArgumentCombination(arguments);
        this.arguments = arguments;
    }

    static CliArguments fromRawArgs(String[] rawArgs) throws CliArgumentsException {
        CommandLineParser parser = new DefaultParser();
        try {
            return new CliArguments(parser.parse(optionsDefinition, rawArgs));
        } catch (ParseException e) {
            throw new CliArgumentsException(e);
        }
    }

    private static void validateArgumentCombination(CommandLine args) throws CliArgumentsException {
        if (!args.hasOption(HELP_OPTION) && !args.hasOption(VERSION_OPTION)) {
            if (!args.hasOption(ENDPOINT_OPTION)) {
                throw new CliArgumentsException("Endpoint must be specified");
            }
            if (args.hasOption(FILE_OPTION) == args.hasOption(STDIN_OPTION)) {
                throw new CliArgumentsException(String.format("Either option '%s' or '%s' must be specified", FILE_OPTION, STDIN_OPTION));
            }
            if (args.hasOption(CERTIFICATE_OPTION) != args.hasOption(PRIVATE_KEY_OPTION)) {
                throw new CliArgumentsException(
                        String.format("Both '%s' and '%s' must be specified together", CERTIFICATE_OPTION, PRIVATE_KEY_OPTION));
            }
        } else if (args.hasOption(HELP_OPTION) && args.hasOption(VERSION_OPTION)) {
            throw new CliArgumentsException(String.format("Cannot specify both '%s' and '%s'", HELP_OPTION, VERSION_OPTION));
        }
    }

    URI endpoint() throws CliArgumentsException {
        try {
            return ((URL) arguments.getParsedOptionValue(ENDPOINT_OPTION)).toURI();
        } catch (ParseException | URISyntaxException e) {
            throw new CliArgumentsException("Invalid endpoint: " + e.getMessage(), e);
        }
    }

    boolean helpSpecified() { return has(HELP_OPTION); }

    boolean versionSpecified() { return has(VERSION_OPTION); }

    OptionalInt connections() throws CliArgumentsException {  return intValue(CONNECTIONS_OPTION); }

    OptionalInt maxStreamsPerConnection() throws CliArgumentsException { return intValue(MAX_STREAMS_PER_CONNECTION); }

    Optional<CertificateAndKey> certificateAndKey() throws CliArgumentsException {
        Path certificateFile = fileValue(CERTIFICATE_OPTION).orElse(null);
        Path privateKeyFile = fileValue(PRIVATE_KEY_OPTION).orElse(null);
        if (privateKeyFile == null && certificateFile == null) return Optional.empty();
        return Optional.of(new CertificateAndKey(certificateFile, privateKeyFile));
    }

    Optional<Path> caCertificates() throws CliArgumentsException { return fileValue(CA_CERTIFICATES_OPTION); }

    Optional<Path> inputFile() throws CliArgumentsException {
        return fileValue(FILE_OPTION);
    }

    Map<String, String> headers() throws CliArgumentsException {
        String[] rawArguments = arguments.getOptionValues(HEADER_OPTION);
        if (rawArguments == null) return Collections.emptyMap();
        Map<String, String> headers = new HashMap<>();
        for (String rawArgument : rawArguments) {
            if (rawArgument.startsWith("\"") || rawArgument.startsWith("'")) {
                rawArgument = rawArgument.substring(1);
            }
            if (rawArgument.endsWith("\"") || rawArgument.endsWith("'")) {
                rawArgument = rawArgument.substring(0, rawArgument.length() - 1);
            }
            int colonIndex = rawArgument.indexOf(':');
            if (colonIndex == -1) throw new CliArgumentsException("Invalid header: '" + rawArgument + "'");
            headers.put(rawArgument.substring(0, colonIndex), rawArgument.substring(colonIndex + 1).trim());
        }
        return Collections.unmodifiableMap(headers);
    }

    boolean sslHostnameVerificationDisabled() { return has(DISABLE_SSL_HOSTNAME_VERIFICATION_OPTION); }

    boolean benchmarkModeEnabled() { return has(BENCHMARK_OPTION); }

    Optional<String> route() { return stringValue(ROUTE_OPTION); }

    OptionalInt traceLevel() throws CliArgumentsException { return intValue(TRACE_OPTION); }

    Optional<Duration> timeout() throws CliArgumentsException {
        OptionalDouble timeout = doubleValue(TIMEOUT_OPTION);
        return timeout.isPresent()
                ? Optional.of(Duration.ofMillis((long)(timeout.getAsDouble()*1000)))
                : Optional.empty();
    }

    boolean verboseSpecified() { return has(VERBOSE_OPTION); }

    boolean readFeedFromStandardInput() { return has(STDIN_OPTION); }

    private OptionalInt intValue(String option) throws CliArgumentsException {
        try {
            Number number = (Number) arguments.getParsedOptionValue(option);
            return number != null ? OptionalInt.of(number.intValue()) : OptionalInt.empty();
        } catch (ParseException e) {
            throw newInvalidValueException(option, e);
        }
    }

    private Optional<Path> fileValue(String option) throws CliArgumentsException {
        try {
            File certificateFile = (File) arguments.getParsedOptionValue(option);
            if (certificateFile == null) return Optional.empty();
            return Optional.of(certificateFile.toPath());
        } catch (ParseException e) {
            throw newInvalidValueException(option, e);
        }
    }

    private Optional<String> stringValue(String option) { return Optional.ofNullable(arguments.getOptionValue(option)); }

    private OptionalDouble doubleValue(String option) throws CliArgumentsException {
        try {
            Number number = (Number) arguments.getParsedOptionValue(option);
            return number != null ? OptionalDouble.of(number.doubleValue()) : OptionalDouble.empty();
        } catch (ParseException e) {
            throw newInvalidValueException(option, e);
        }
    }

    private boolean has(String option) { return arguments.hasOption(option); }

    private static CliArgumentsException newInvalidValueException(String option, ParseException cause) {
        return new CliArgumentsException(String.format("Invalid value for '%s': %s", option, cause.getMessage()), cause);
    }

    private static Options createOptions() {
        return new Options()
                .addOption(Option.builder()
                        .longOpt(HELP_OPTION)
                        .build())
                .addOption(Option.builder()
                        .longOpt(VERSION_OPTION)
                        .build())
                .addOption(Option.builder()
                        .longOpt(ENDPOINT_OPTION)
                        .desc("URI to feed endpoint")
                        .hasArg()
                        .type(URL.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(HEADER_OPTION)
                        .desc("HTTP header on the form 'Name: value'")
                        .hasArgs()
                        .build())
                .addOption(Option.builder()
                        .longOpt(FILE_OPTION)
                        .type(File.class)
                        .desc("Path to feed file in JSON format")
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(CONNECTIONS_OPTION)
                        .desc("Number of concurrent HTTP/2 connections")
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(MAX_STREAMS_PER_CONNECTION)
                        .desc("Number of concurrent streams per HTTP/2 connection")
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(CERTIFICATE_OPTION)
                        .desc("Path to PEM encoded X.509 certificate file")
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(PRIVATE_KEY_OPTION)
                        .desc("Path to PEM/PKCS#8 encoded private key file")
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(CA_CERTIFICATES_OPTION)
                        .desc("Path to file containing CA X.509 certificates encoded as PEM")
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(DISABLE_SSL_HOSTNAME_VERIFICATION_OPTION)
                        .desc("Disable SSL hostname verification")
                        .build())
                .addOption(Option.builder()
                        .longOpt(BENCHMARK_OPTION)
                        .desc("Enable benchmark mode")
                        .build())
                .addOption(Option.builder()
                        .longOpt(ROUTE_OPTION)
                        .desc("Target Vespa route for feed operations")
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(TIMEOUT_OPTION)
                        .desc("Feed operation timeout (in seconds)")
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(TRACE_OPTION)
                        .desc("The trace level of network traffic. Disabled by default (=0)")
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(STDIN_OPTION)
                        .desc("Read JSON input from standard input")
                        .build())
                .addOption(Option.builder()
                        .longOpt(VERBOSE_OPTION)
                        .build());
    }

    void printHelp(OutputStream out) {
        HelpFormatter formatter = new HelpFormatter();
        PrintWriter writer = new PrintWriter(out);
        formatter.printHelp(
                writer,
                formatter.getWidth(),
                "vespa-feed-client <options>",
                "Vespa feed client",
                optionsDefinition,
                formatter.getLeftPadding(),
                formatter.getDescPadding(),
                "");
        writer.flush();
    }

    static class CliArgumentsException extends Exception {
        CliArgumentsException(String message, Throwable cause) { super(message, cause); }
        CliArgumentsException(Throwable cause) { super(cause.getMessage(), cause); }
        CliArgumentsException(String message) { super(message); }
    }

    static class CertificateAndKey {
        final Path certificateFile;
        final Path privateKeyFile;

        CertificateAndKey(Path certificateFile, Path privateKeyFile) {
            this.certificateFile = certificateFile;
            this.privateKeyFile = privateKeyFile;
        }
    }

}
