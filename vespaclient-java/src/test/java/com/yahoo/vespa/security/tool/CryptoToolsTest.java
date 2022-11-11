// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.security.SharedKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.toUtf8Bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author vekterli
 */
public class CryptoToolsTest {

    private record ProcessOutput(int exitCode, String stdOut, String stdErr) {}

    private static final byte[] EMPTY_BYTES = new byte[0];

    @TempDir
    public File tmpFolder;

    private void verifyStdoutMatchesFile(List<String> args, String expectedFile) throws IOException {
        var procOut = runMain(args, EMPTY_BYTES, Map.of());
        assertEquals(0, procOut.exitCode());
        assertEquals(readTestResource(expectedFile), procOut.stdOut());
    }

    private void verifyStdoutEquals(List<String> args, String stdIn, String expectedMessage) throws IOException {
        var procOut = runMain(args, toUtf8Bytes(stdIn), Map.of());
        assertEquals(0, procOut.exitCode());
        assertEquals(expectedMessage, procOut.stdOut());
    }

    private void verifyStderrEquals(List<String> args, String expectedMessage) throws IOException {
        var procOut = runMain(args, EMPTY_BYTES, Map.of());
        assertEquals(1, procOut.exitCode()); // Assume checking stderr is because of a failure.
        assertEquals(expectedMessage, procOut.stdErr());
    }

    @Test
    void top_level_help_page_printed_if_help_option_given() throws IOException {
        verifyStdoutMatchesFile(List.of("--help"), "expected-help-output.txt");
    }

    @Test
    void top_level_help_page_printed_if_no_option_given() throws IOException {
        verifyStdoutMatchesFile(List.of(), "expected-help-output.txt");
    }

    @Test
    void keygen_help_printed_if_help_option_given_to_subtool() throws IOException {
        verifyStdoutMatchesFile(List.of("keygen", "--help"), "expected-keygen-help-output.txt");
    }

    @Test
    void encrypt_help_printed_if_help_option_given_to_subtool() throws IOException {
        verifyStdoutMatchesFile(List.of("encrypt", "--help"), "expected-encrypt-help-output.txt");
    }

    @Test
    void decrypt_help_printed_if_help_option_given_to_subtool() throws IOException {
        verifyStdoutMatchesFile(List.of("decrypt", "--help"), "expected-decrypt-help-output.txt");
    }

    @Test
    void token_info_help_printed_if_help_option_given_to_subtool() throws IOException {
        verifyStdoutMatchesFile(List.of("token-info", "--help"), "expected-token-info-help-output.txt");
    }

    @Test
    void convert_base_help_printed_if_help_option_given_to_subtool() throws IOException {
        verifyStdoutMatchesFile(List.of("convert-base", "--help"), "expected-convert-base-help-output.txt");
    }

    @Test
    void reseal_help_printed_if_help_option_given_to_subtool() throws IOException {
        verifyStdoutMatchesFile(List.of("reseal", "--help"), "expected-reseal-help-output.txt");
    }

    @Test
    void missing_required_parameter_prints_error_message() throws IOException {
        // We don't test all possible input arguments to all tools, since it'd be too closely
        // bound to the order in which the implementation checks for argument presence.
        // This primarily verifies that IllegalArgumentExceptions thrown by a tool will be caught
        // and printed to stderr as expected.
        verifyStderrEquals(List.of("keygen"),
                "Invalid command line arguments: Required argument '--private-out-file' must be provided\n");
        verifyStderrEquals(List.of("keygen", "--private-out-file", "foo.txt"),
                "Invalid command line arguments: Required argument '--public-out-file' must be provided\n");
    }

    // We don't want to casually overwrite key material if someone runs a command twice by accident.
    @Test
    void keygen_fails_by_default_if_output_file_exists() throws IOException {
        Path privKeyFile = pathInTemp("priv.txt");
        Path pubKeyFile  = pathInTemp("pub.txt");
        Files.writeString(privKeyFile, TEST_PRIV_KEY);

        verifyStderrEquals(List.of("keygen",
                                   "--private-out-file", absPathOf(privKeyFile),
                                   "--public-out-file",  absPathOf(pubKeyFile)),
                ("Invalid command line arguments: Output file '%s' already exists. No keys written. " +
                 "If you want to overwrite existing files, specify --overwrite-existing.\n")
                .formatted(absPathOf(privKeyFile)));

        Files.delete(privKeyFile);
        Files.writeString(pubKeyFile, TEST_PUB_KEY);

        verifyStderrEquals(List.of("keygen",
                                   "--private-out-file", absPathOf(privKeyFile),
                                   "--public-out-file",  absPathOf(pubKeyFile)),
                ("Invalid command line arguments: Output file '%s' already exists. No keys written. " +
                 "If you want to overwrite existing files, specify --overwrite-existing.\n")
                 .formatted(absPathOf(pubKeyFile)));
    }

    @Test
    void keygen_fails_if_priv_and_pub_paths_equal() throws IOException {
        Path keyFile = pathInTemp("foo.txt");

        verifyStderrEquals(List.of("keygen",
                        "--private-out-file", absPathOf(keyFile),
                        "--public-out-file",  absPathOf(keyFile)),
                "Invalid command line arguments: Private and public key output files must be different\n");
    }

    // ... but we'll allow it if someone enables the foot-gun option.
    @Test
    void keygen_allowed_if_output_file_exists_and_explicit_overwrite_option_specified() throws IOException {
        Path privKeyFile = pathInTemp("priv.txt");
        Path pubKeyFile  = pathInTemp("pub.txt");
        Files.writeString(privKeyFile, TEST_PRIV_KEY);
        Files.writeString(pubKeyFile,  TEST_PUB_KEY);

        var procOut = runMain(List.of("keygen",
                                     "--private-out-file", absPathOf(privKeyFile),
                                     "--public-out-file",  absPathOf(pubKeyFile),
                                     "--overwrite-existing"));
        assertEquals(0, procOut.exitCode());

        // Keys are random, so we don't know what they'll end up being. But the likelihood of them
        // exactly matching the test keys is effectively and realistically zero.
        assertNotEquals(TEST_PRIV_KEY, Files.readString(privKeyFile));
        assertNotEquals(TEST_PUB_KEY,  Files.readString(pubKeyFile));
    }

    @Test
    void keygen_writes_private_key_with_user_only_rw_permissions() throws IOException {
        Path privKeyFile = pathInTemp("priv.txt");
        Path pubKeyFile  = pathInTemp("pub.txt");

        var procOut = runMain(List.of("keygen",
                                      "--private-out-file", absPathOf(privKeyFile),
                                      "--public-out-file",  absPathOf(pubKeyFile)));
        assertEquals(0, procOut.exitCode());
        var privKeyPerms  = Files.getPosixFilePermissions(privKeyFile);
        var expectedPerms = PosixFilePermissions.fromString("rw-------");
        assertEquals(expectedPerms, privKeyPerms);
    }

    private static final String TEST_PRIV_KEY     = "GFg54SaGNCmcSGufZCx68SKLGuAFrASoDeMk3t5AjU6L";
    private static final String TEST_PUB_KEY      = "5drrkakYLjYSBpr5Haknh13EiCYL36ndMzK4gTJo6pwh";
    // Token created for the above public key (matching the above private key), using key id "my key ID"
    private static final String TEST_TOKEN        = "OntP9gRVAjXeZIr4zkYqRJFcnA993v7ZEE7VbcNs1NcR3HdE7Mp" +
                                                    "wlwi3r3anF1kVa5fn7O1CyeHQpBWpdayUTKkrtyFepG6WJrZdE";
    private static final String TEST_TOKEN_KEY_ID = "my key ID";
    private static final String TEST_TOKEN_SECRET = "1b33b4dcd6a94e5a4a1ee6d208197d01";

    @Test
    void encrypt_fails_with_error_message_if_no_input_file_is_given() throws IOException {
        verifyStderrEquals(List.of("encrypt",
                                   "--output-file",          "foo",
                                   "--recipient-public-key", TEST_PUB_KEY,
                                   "--key-id",               "1234"),
                "Invalid command line arguments: Expected exactly 1 file argument to encrypt\n");
    }

    @Test
    void encrypt_fails_with_error_message_if_input_file_does_not_exist() throws IOException {
        verifyStderrEquals(List.of("encrypt",
                                   "no-such-file",
                                   "--output-file",          "foo",
                                   "--recipient-public-key", TEST_PUB_KEY,
                                   "--key-id",               "1234"),
                "Invalid command line arguments: Input file 'no-such-file' does not exist\n");
    }

    @Test
    void decrypt_fails_with_error_message_if_no_input_file_is_given() throws IOException {
        Path privKeyFile = pathInTemp("priv.txt");
        Files.writeString(privKeyFile, TEST_PRIV_KEY);

        verifyStderrEquals(List.of("decrypt",
                                   "--output-file",      "foo",
                                   "--private-key-file", absPathOf(privKeyFile),
                                   "--token",            TEST_TOKEN,
                                   "--expected-key-id",  TEST_TOKEN_KEY_ID),
                "Invalid command line arguments: Expected exactly 1 file argument to decrypt\n");
    }

    @Test
    void decrypt_fails_with_error_message_if_input_file_does_not_exist() throws IOException {
        Path privKeyFile = pathInTemp("priv.txt");
        Files.writeString(privKeyFile, TEST_PRIV_KEY);

        verifyStderrEquals(List.of("decrypt",
                                   "no-such-file",
                                   "--output-file",      "foo",
                                   "--private-key-file", absPathOf(privKeyFile),
                                   "--token",            TEST_TOKEN,
                                   "--expected-key-id",  TEST_TOKEN_KEY_ID),
                "Invalid command line arguments: Input file 'no-such-file' does not exist\n");
    }

    @Test
    void decrypt_fails_with_error_message_if_expected_key_id_does_not_match_key_id_in_token() throws IOException {
        Path privKeyFile = pathInTemp("priv.txt");
        Files.writeString(privKeyFile, TEST_PRIV_KEY);

        Path inputFile = pathInTemp("input.txt");
        Files.writeString(inputFile, "dummy-not-actually-encrypted-data");

        verifyStderrEquals(List.of("decrypt",
                                   absPathOf(inputFile),
                                   "--output-file",      "foo",
                                   "--private-key-file", absPathOf(privKeyFile),
                                   "--token",            TEST_TOKEN,
                                   "--expected-key-id",  TEST_TOKEN_KEY_ID + "-wrong"),
                "Invalid command line arguments: Key ID specified with --expected-key-id does not " +
                        "match key ID used when generating the supplied token\n");
    }

    @Test
    void token_info_fails_with_error_message_if_no_token_string_given() throws IOException {
        verifyStderrEquals(List.of("token-info"),
                "Invalid command line arguments: Expected exactly 1 token string argument\n");
    }

    @Test
    void token_info_is_printed_to_stdout() throws IOException {
        verifyStdoutMatchesFile(List.of("token-info", TEST_TOKEN), "expected-token-info-output.txt");
    }

    @Test
    void convert_base_reads_stdin_and_prints_conversion_on_stdout() throws IOException {
        // Check all possible output encodings
        verifyStdoutEquals(List.of("convert-base", "--from", "16", "--to", "16"), "0000287fb4cd", "0000287fb4cd\n");
        verifyStdoutEquals(List.of("convert-base", "--from", "16", "--to", "58"), "0000287fb4cd", "11233QC4\n");
        verifyStdoutEquals(List.of("convert-base", "--from", "16", "--to", "62"), "0000287fb4cd", "00jyw3x\n");
        verifyStdoutEquals(List.of("convert-base", "--from", "16", "--to", "64"), "0000287fb4cd", "AAAof7TN\n");

        // Check a single output encoding for each input encoding, making the simplifying assumption that
        // decoding and encoding is independent. Base 16 already covered above.
        verifyStdoutEquals(List.of("convert-base", "--from", "58", "--to", "16"), "11233QC4", "0000287fb4cd\n");
        verifyStdoutEquals(List.of("convert-base", "--from", "62", "--to", "16"), "00jyw3x",  "0000287fb4cd\n");
        verifyStdoutEquals(List.of("convert-base", "--from", "64", "--to", "16"), "AAAof7TN", "0000287fb4cd\n");
    }

    @Test
    void convert_base_tool_ignores_whitespace_on_stdin() throws IOException {
        verifyStdoutEquals(List.of("convert-base", "--from", "16", "--to", "58"), "  0000287fb4cd\n", "11233QC4\n");
    }

    @Test
    void can_reseal_a_token_to_another_recipient() throws IOException {
        String recipientPrivKeyStr = "GdgfBZzPDqrCVs5f1xaYJpXVGwJzgdTAF1NNWiDk16YZ";
        String recipientPubKeyStr  = "AiUirFvFuLJ6s71QBNxiRcctB4umzM6r2roP4Rf8WDKM";

        Path privKeyFile = pathInTemp("my-priv.txt");
        Files.writeString(privKeyFile, TEST_PRIV_KEY);

        var procOut = runMain(List.of(
                "reseal",
                TEST_TOKEN,
                "--private-key-file",     absPathOf(privKeyFile),
                "--recipient-public-key", recipientPubKeyStr,
                "--expected-key-id",      TEST_TOKEN_KEY_ID,
                "--key-id",               "some-recipient-key-id"));
        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdErr());
        var resealedToken = procOut.stdOut().strip();

        // Verify that the resealed token wraps the same secret as the original one
        var recipientPrivKey = KeyUtils.fromBase58EncodedX25519PrivateKey(recipientPrivKeyStr);
        var recvShared = SharedKeyGenerator.fromSealedKey(SealedSharedKey.fromTokenString(resealedToken), recipientPrivKey);
        assertEquals(KeyId.ofString("some-recipient-key-id"), recvShared.sealedSharedKey().keyId());
        assertEquals(TEST_TOKEN_SECRET, hex(recvShared.secretKey().getEncoded()));
    }

    @Test
    void can_end_to_end_keygen_encrypt_and_decrypt_via_files() throws IOException {
        String greatSecret = "Dogs can't look up";

        Path secretFile = pathInTemp("secret.txt");
        Files.writeString(secretFile, greatSecret);

        var privPath = pathInTemp("priv.txt");
        var pubPath = pathInTemp("pub.txt");
        var procOut = runMain(List.of(
                "keygen",
                "--private-out-file", absPathOf(privPath),
                "--public-out-file",  absPathOf(pubPath)));
        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdOut());
        assertEquals("", procOut.stdErr());

        assertTrue(Files.exists(privPath));
        assertTrue(Files.exists(pubPath));

        var encryptedPath = pathInTemp("encrypted.bin");
        // TODO support (and test) public key via file
        procOut = runMain(List.of(
                "encrypt",
                absPathOf(secretFile),
                "--output-file",          absPathOf(encryptedPath),
                "--recipient-public-key", Files.readString(pubPath),
                "--key-id",               "1234"));
        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdErr());

        var token = procOut.stdOut();
        assertFalse(token.isBlank());

        assertTrue(Files.exists(encryptedPath));

        var decryptedPath = pathInTemp("decrypted.txt");
        procOut = runMain(List.of(
                "decrypt",
                absPathOf(encryptedPath),
                "--output-file",      absPathOf(decryptedPath),
                "--private-key-file", absPathOf(privPath),
                "--expected-key-id",  "1234",
                "--token",            token
                ));
        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdOut());
        assertEquals("", procOut.stdErr());

        assertEquals(greatSecret, Files.readString(decryptedPath));
    }

    @Test
    void can_end_to_end_keygen_encrypt_and_decrypt_via_stdio_streams() throws IOException {
        String greatSecret = "forbidden knowledge about cats.txt";

        var privPath = pathInTemp("priv.txt");
        var pubPath = pathInTemp("pub.txt");
        var procOut = runMain(List.of(
                "keygen",
                "--private-out-file", absPathOf(privPath),
                "--public-out-file",  absPathOf(pubPath)));
        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdOut());
        assertEquals("", procOut.stdErr());

        assertTrue(Files.exists(privPath));
        assertTrue(Files.exists(pubPath));

        var encryptedPath = pathInTemp("encrypted.bin");
        // Encryption emits token on stdout, so can't support ciphertext output via that channel.
        procOut = runMain(List.of(
                "encrypt",
                "-", // Encrypt stdin
                "--output-file",          absPathOf(encryptedPath),
                "--recipient-public-key", Files.readString(pubPath),
                "--key-id",               "1234"),
                toUtf8Bytes(greatSecret));
        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdErr());

        var token = procOut.stdOut();
        assertFalse(token.isBlank());

        assertTrue(Files.exists(encryptedPath));

        procOut = runMain(List.of(
                "decrypt",
                "-", // Decrypt stdin
                "--output-file",      "-", // Plaintext to stdout
                "--private-key-file", absPathOf(privPath),
                "--expected-key-id",  "1234",
                "--token",            token
        ), Files.readAllBytes(encryptedPath));

        assertEquals(0, procOut.exitCode());
        assertEquals("", procOut.stdErr());
        assertEquals(greatSecret, procOut.stdOut());
    }

    private ProcessOutput runMain(List<String> args) {
        return runMain(args, EMPTY_BYTES);
    }

    private ProcessOutput runMain(List<String> args, byte[] stdInBytes) {
        // Expect that this is used for running a command that is not supposed to fail. But if it does,
        // include exception trace in stderr to make it easier to debug.
        return runMain(args, stdInBytes, Map.of("VESPA_DEBUG", "true"));
    }

    private ProcessOutput runMain(List<String> args, byte[] stdInBytes, Map<String, String> env) {
        var stdOutBytes = new ByteArrayOutputStream();
        var stdErrBytes = new ByteArrayOutputStream();
        var stdIn       = new ByteArrayInputStream(stdInBytes);
        var stdOut      = new PrintStream(stdOutBytes);
        var stdError    = new PrintStream(stdErrBytes);

        int exitCode = new Main(stdIn, stdOut, stdError).execute(args.toArray(new String[0]), env);

        stdOut.flush();
        stdError.flush();

        return new ProcessOutput(exitCode, stdOutBytes.toString(), stdErrBytes.toString());
    }

    private static String readTestResource(String fileName) throws IOException {
        return Files.readString(Paths.get(CryptoToolsTest.class.getResource('/' + fileName).getFile()));
    }

    private Path pathInTemp(String fileName) {
        return tmpFolder.toPath().resolve(fileName);
    }

    private static String absPathOf(Path path) {
        return path.toAbsolutePath().toString();
    }

}
