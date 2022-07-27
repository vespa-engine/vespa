// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.securityenv;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TransportSecurityOptions;
import com.yahoo.security.tls.TransportSecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author bjorncs
 */
public class MainTest {

    private final ByteArrayOutputStream stdOutBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stdErrBytes = new ByteArrayOutputStream();
    private final PrintStream stdOut = new PrintStream(stdOutBytes);
    private final PrintStream stdError = new PrintStream(stdErrBytes);

    @TempDir
    public File tmpFolder;

    @Test
    void prints_help_page_on_help_option() throws IOException {
        int exitCode = runMain(List.of("--help"), Map.of());
        assertThat(exitCode).isEqualTo(0);
        assertThat(stdOut()).isEqualTo(readTestResource("expected-help-output.txt"));
    }

    @Test
    void unsets_all_variables_when_no_security_config() throws IOException {
        int exitCode = runMain(List.of(), Map.of());
        assertThat(exitCode).isEqualTo(0);
        assertThat(stdErr()).isEmpty();
        assertThat(stdOut()).isEqualTo(readTestResource("no-security-output.txt"));
    }

    @Test
    void prints_security_variables_with_specified_shell() throws IOException {
        Path configFile = generateConfigFile();
        Map<String, String> env = Map.of(TransportSecurityUtils.CONFIG_FILE_ENVIRONMENT_VARIABLE, configFile.toString());
        int exitCode = runMain(List.of(), env);
        assertThat(exitCode).isEqualTo(0);
        assertThat(stdOut()).isEqualTo(readTestResource("bash-output.txt"));
    }

    @Test
    void prints_security_variables_with_auto_detected_shell() throws IOException {
        Path configFile = generateConfigFile();
        Map<String, String> env = Map.of(
                TransportSecurityUtils.CONFIG_FILE_ENVIRONMENT_VARIABLE, configFile.toString(),
                TransportSecurityUtils.INSECURE_MIXED_MODE_ENVIRONMENT_VARIABLE, MixedMode.TLS_CLIENT_MIXED_SERVER.configValue(),
                "SHELL", "/usr/local/bin/fish");
        int exitCode = runMain(List.of(), env);
        assertThat(exitCode).isEqualTo(0);
        assertThat(stdOut()).isEqualTo(readTestResource("csh-output.txt"));
    }


    @Test
    void prints_error_message_on_unknown_shell_name() {
        int exitCode = runMain(List.of("--shell", "invalid-shell-name"), Map.of());
        assertThat(exitCode).isEqualTo(1);
        assertThat(stdErr()).isEqualTo("Invalid command line arguments: Unknown shell: invalid-shell-name\n");
    }

    @Test
    void prints_error_message_on_unknown_command_line_parameter() {
        int exitCode = runMain(List.of("--unknown-parameter"), Map.of());
        assertThat(exitCode).isEqualTo(1);
        assertThat(stdErr()).isEqualTo("Failed to parse command line arguments: Unrecognized option: --unknown-parameter\n");
    }

    private int runMain(List<String> args, Map<String, String> env) {
        return new Main(stdOut, stdError).execute(args.toArray(new String[0]), env);
    }

    private String stdOut() {
        stdOut.flush();
        return stdOutBytes.toString();
    }

    private String stdErr() {
        stdError.flush();
        return stdErrBytes.toString();
    }

    private static String readTestResource(String fileName) throws IOException {
        return Files.readString(Paths.get(MainTest.class.getResource('/' + fileName).getFile()));
    }

    private Path generateConfigFile() throws IOException {
        TransportSecurityOptions options = new TransportSecurityOptions.Builder()
                .withCertificates(Paths.get("/path/to/certificate"), Paths.get("/path/to/key"))
                .withCaCertificates(Paths.get("/path/to/cacerts"))
                .withHostnameValidationDisabled(true)
                .build();
        Path configFile = File.createTempFile("junit", null, tmpFolder).toPath();
        options.toJsonFile(configFile);
        return configFile;
    }

}