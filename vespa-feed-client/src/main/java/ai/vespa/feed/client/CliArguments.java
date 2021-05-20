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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Parses command line arguments
 *
 * @author bjorncs
 */
class CliArguments {

    private static final Options optionsDefinition = createOptions();

    private static final String HELP_OPTION = "help";
    private static final String VERSION_OPTION = "version";
    private static final String ENDPOINT_OPTION = "endpoint";
    private static final String FILE_OPTION = "file";
    private static final String CONNECTIONS_OPTION = "connections";
    private static final String MAX_STREAMS_PER_CONNECTION = "max-streams-per-connection";
    private static final String CERTIFICATE_OPTION = "certificate";
    private static final String PRIVATE_KEY_OPTION = "private-key";
    private static final String CA_CERTIFICATES_OPTION = "ca-certificates";
    private static final String DISABLE_SSL_HOSTNAME_VERIFICATION_OPTION = "disable-ssl-hostname-verification";
    private static final String HEADER_OPTION = "header";

    private final CommandLine arguments;

    private CliArguments(CommandLine arguments) {
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

    URI endpoint() throws CliArgumentsException {
        try {
            URL url = (URL) arguments.getParsedOptionValue(ENDPOINT_OPTION);
            if (url == null) throw new CliArgumentsException("Endpoint must be specified");
            return url.toURI();
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
        if ((certificateFile == null) != (privateKeyFile == null)) {
            throw new CliArgumentsException(String.format("Both '%s' and '%s' must be specified together", CERTIFICATE_OPTION, PRIVATE_KEY_OPTION));
        }
        if (privateKeyFile == null && certificateFile == null) return Optional.empty();
        return Optional.of(new CertificateAndKey(certificateFile, privateKeyFile));
    }

    Optional<Path> caCertificates() throws CliArgumentsException { return fileValue(CA_CERTIFICATES_OPTION); }

    Path inputFile() throws CliArgumentsException {
        return fileValue(FILE_OPTION)
            .orElseThrow(() -> new CliArgumentsException("Feed file must be specified"));
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

    private OptionalInt intValue(String option) throws CliArgumentsException {
        try {
            Number number = (Number) arguments.getParsedOptionValue(option);
            return number != null ? OptionalInt.of(number.intValue()) : OptionalInt.empty();
        } catch (ParseException e) {
            throw new CliArgumentsException(String.format("Invalid value for '%s': %s", option, e.getMessage()), e);
        }
    }

    private Optional<Path> fileValue(String option) throws CliArgumentsException {
        try {
            File certificateFile = (File) arguments.getParsedOptionValue(option);
            if (certificateFile == null) return Optional.empty();
            return Optional.of(certificateFile.toPath());
        } catch (ParseException e) {
            throw new CliArgumentsException(String.format("Invalid value for '%s': %s", option, e.getMessage()), e);
        }
    }

    private boolean has(String option) { return arguments.hasOption(option); }

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
                        .hasArg()
                        .type(URL.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(HEADER_OPTION)
                        .hasArgs()
                        .build())
                .addOption(Option.builder()
                        .longOpt(FILE_OPTION)
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(CONNECTIONS_OPTION)
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(MAX_STREAMS_PER_CONNECTION)
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(CONNECTIONS_OPTION)
                        .hasArg()
                        .type(Number.class)
                        .build())
                .addOption(Option.builder()
                        .longOpt(CERTIFICATE_OPTION)
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(PRIVATE_KEY_OPTION)
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(CA_CERTIFICATES_OPTION)
                        .type(File.class)
                        .hasArg()
                        .build())
                .addOption(Option.builder()
                        .longOpt(DISABLE_SSL_HOSTNAME_VERIFICATION_OPTION)
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
