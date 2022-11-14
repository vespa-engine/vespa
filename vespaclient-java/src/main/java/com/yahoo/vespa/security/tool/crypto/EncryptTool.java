// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SharedKeyGenerator;
import com.yahoo.vespa.security.tool.CliUtils;
import com.yahoo.vespa.security.tool.Tool;
import com.yahoo.vespa.security.tool.ToolDescription;
import com.yahoo.vespa.security.tool.ToolInvocation;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Tooling to encrypt a file using a public key, emitting a non-secret token that can be
 * passed on to a recipient holding the corresponding private key.
 *
 * Uses the opaque token abstraction from {@link SharedKeyGenerator}.
 *
 * @author vekterli
 */
public class EncryptTool implements Tool {

    static final String OUTPUT_FILE_OPTION          = "output-file";
    static final String KEY_ID_OPTION               = "key-id";
    static final String RECIPIENT_PUBLIC_KEY_OPTION = "recipient-public-key";

    private static final List<Option> OPTIONS = List.of(
            Option.builder("o")
                    .longOpt(OUTPUT_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Output file (will be truncated if it already exists)")
                    .build(),
            Option.builder("r")
                    .longOpt(RECIPIENT_PUBLIC_KEY_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Recipient X25519 public key in Base58 encoded format")
                    .build(),
            Option.builder("i")
                    .longOpt(KEY_ID_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("ID of recipient key")
                    .build());

    @Override
    public String name() {
        return "encrypt";
    }

    @Override
    public ToolDescription description() {
        return new ToolDescription(
                "<input file> <options>",
                "One-way encrypts a file using the public key of a recipient. A public token is printed on " +
                "standard out. The recipient can use this token to decrypt the file using their private key. " +
                "The token does not have to be kept secret.\n\n" +
                "To encrypt the contents of STDIN, specify an input file of '-' (without the quotes).",
                "Note: this is a BETA tool version; its interface may be changed at any time",
                OPTIONS);
    }

    @Override
    public int invoke(ToolInvocation invocation) {
        try {
            var arguments    = invocation.arguments();
            var leftoverArgs = arguments.getArgs();
            if (leftoverArgs.length != 1) {
                throw new IllegalArgumentException("Expected exactly 1 file argument to encrypt");
            }
            var inputArg   = leftoverArgs[0];
            var outputPath = Paths.get(CliUtils.optionOrThrow(arguments, OUTPUT_FILE_OPTION));

            var recipientPubKey = KeyUtils.fromBase58EncodedX25519PublicKey(CliUtils.optionOrThrow(arguments, RECIPIENT_PUBLIC_KEY_OPTION).strip());
            var keyId  = KeyId.ofString(CliUtils.optionOrThrow(arguments, KEY_ID_OPTION));
            var shared = SharedKeyGenerator.generateForReceiverPublicKey(recipientPubKey, keyId);
            var cipher = SharedKeyGenerator.makeAesGcmEncryptionCipher(shared);

            try (var inStream  = CliUtils.inputStreamFromFileOrStream(inputArg, invocation.stdIn());
                 var outStream = Files.newOutputStream(outputPath)) {
                CipherUtils.streamEncipher(inStream, outStream, cipher);
            }

            invocation.stdOut().println(shared.sealedSharedKey().toTokenString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
