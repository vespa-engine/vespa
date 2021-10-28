// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.securityenv;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityOptions;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.io.PrintStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.yahoo.vespa.security.tool.securityenv.CliOptions.HELP_OPTION;
import static com.yahoo.vespa.security.tool.securityenv.CliOptions.SHELL_OPTION;

/**
 * Implementation of the 'vespa-security-env' command line utility.
 *
 * @author bjorncs
 */
public class Main {

    private final PrintStream stdOut;
    private final PrintStream stdError;

    Main(PrintStream stdOut, PrintStream stdError) {
        this.stdOut = stdOut;
        this.stdError = stdError;
    }

    public static void main(String[] args) {
        Main program = new Main(System.out, System.err);
        int statusCode = program.execute(args, System.getenv());
        System.exit(statusCode);
    }

    int execute(String[] cliArgs, Map<String, String> envVars) {
        boolean debugMode = envVars.containsKey("VESPA_DEBUG");
        try {
            CommandLine arguments = CliOptions.parseCliArguments(cliArgs);
            if (arguments.hasOption(HELP_OPTION)) {
                CliOptions.printHelp(stdOut);
                return 0;
            }
            UnixShell shell = arguments.hasOption(SHELL_OPTION)
                    ? UnixShell.fromConfigName(arguments.getOptionValue(SHELL_OPTION))
                    : UnixShell.detect(envVars.get("SHELL"));

            Map<OutputVariable, String> outputVariables = new TreeMap<>();
            Optional<TransportSecurityOptions> options = TransportSecurityUtils.getOptions(envVars);
            MixedMode mixedMode = TransportSecurityUtils.getInsecureMixedMode(envVars);
            if (options.isPresent() && mixedMode != MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER) {
                outputVariables.put(OutputVariable.TLS_ENABLED, "1");
                if (options.get().isHostnameValidationDisabled()) {
                    outputVariables.put(OutputVariable.DISABLE_HOSTNAME_VALIDATION, "1");
                }
                options.get().getCaCertificatesFile()
                        .ifPresent(caCertFile -> outputVariables.put(OutputVariable.CA_CERTIFICATE, caCertFile.toString()));
                options.get().getCertificatesFile()
                        .ifPresent(certificateFile -> outputVariables.put(OutputVariable.CERTIFICATE, certificateFile.toString()));
                options.get().getPrivateKeyFile()
                        .ifPresent(privateKeyFile -> outputVariables.put(OutputVariable.PRIVATE_KEY, privateKeyFile.toString()));
            }
            shell.writeOutputVariables(stdOut, outputVariables);
            EnumSet<OutputVariable> unusedVariables = outputVariables.isEmpty()
                    ? EnumSet.allOf(OutputVariable.class)
                    : EnumSet.complementOf(EnumSet.copyOf(outputVariables.keySet()));
            shell.unsetVariables(stdOut, unusedVariables);
            return 0;
        } catch (ParseException e) {
            return handleException("Failed to parse command line arguments: " + e.getMessage(), e, debugMode);
        } catch (IllegalArgumentException e) {
            return handleException("Invalid command line arguments: " + e.getMessage(), e, debugMode);
        } catch (Exception e) {
            return handleException("Failed to generate security environment variables: " + e.getMessage(), e, debugMode);
        }
    }

    private int handleException(String message, Exception exception, boolean debugMode) {
        stdError.println(message);
        if (debugMode) {
            exception.printStackTrace(stdError);
        }
        return 1;
    }
}
