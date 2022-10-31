// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
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
import java.util.Optional;

/**
 * Tooling for decrypting a file using a private key that corresponds to the public key used
 * to originally encrypt the file.
 *
 * Uses the opaque token abstraction from {@link SharedKeyGenerator}.
 *
 * @author vekterli
 */
public class DecryptTool implements Tool {

    static final String OUTPUT_FILE_OPTION                = "output-file";
    static final String RECIPIENT_PRIVATE_KEY_FILE_OPTION = "recipient-private-key-file";
    static final String KEY_ID_OPTION                     = "key-id";
    static final String TOKEN_OPTION                      = "token";

    private static final List<Option> OPTIONS = List.of(
            Option.builder("o")
                    .longOpt(OUTPUT_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Output file for decrypted plaintext. Specify '-' (without the " +
                          "quotes) to write plaintext to STDOUT instead of a file.")
                    .build(),
            Option.builder("k")
                    .longOpt(RECIPIENT_PRIVATE_KEY_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Recipient private key file")
                    .build(),
            Option.builder("i")
                    .longOpt(KEY_ID_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Numeric ID of recipient key. If this is not provided, " +
                          "the key ID stored as part of the token is not verified.")
                    .build(),
            Option.builder("t")
                    .longOpt(TOKEN_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Token generated when the input file was encrypted")
                    .build());

    @Override
    public String name() {
        return "decrypt";
    }

    @Override
    public ToolDescription description() {
        return new ToolDescription(
                "<encrypted file> <options>",
                "Decrypts a file using a provided token and a secret private key. The file must " +
                "previously have been encrypted using the public key component of the given private key.\n\n" +
                "To decrypt the contents of STDIN, specify an input file of '-' (without the quotes).",
                "Note: this is a BETA tool version; its interface may be changed at any time",
                OPTIONS);
    }

    @Override
    public int invoke(ToolInvocation invocation) {
        try {
            var arguments    = invocation.arguments();
            var leftoverArgs = arguments.getArgs();
            if (leftoverArgs.length != 1) {
                throw new IllegalArgumentException("Expected exactly 1 file argument to decrypt");
            }
            var inputArg   = leftoverArgs[0];
            var maybeKeyId = Optional.ofNullable(arguments.hasOption(KEY_ID_OPTION)
                                                 ? Integer.parseInt(arguments.getOptionValue(KEY_ID_OPTION))
                                                 : null);
            var outputArg   = CliUtils.optionOrThrow(arguments, OUTPUT_FILE_OPTION);
            var privKeyPath = Paths.get(CliUtils.optionOrThrow(arguments, RECIPIENT_PRIVATE_KEY_FILE_OPTION));
            var tokenString = CliUtils.optionOrThrow(arguments, TOKEN_OPTION);
            var sealedSharedKey = SealedSharedKey.fromTokenString(tokenString.strip());
            if (maybeKeyId.isPresent() && (maybeKeyId.get() != sealedSharedKey.keyId())) {
                throw new IllegalArgumentException(("Key ID specified with --key-id (%d) does not match key ID " +
                                                    "used when generating the supplied token (%d)")
                                                    .formatted(maybeKeyId.get(), sealedSharedKey.keyId()));
            }
            var privateKey   = KeyUtils.fromBase64EncodedX25519PrivateKey(Files.readString(privKeyPath).strip());
            var secretShared = SharedKeyGenerator.fromSealedKey(sealedSharedKey, privateKey);
            var cipher       = SharedKeyGenerator.makeAesGcmDecryptionCipher(secretShared);

            try (var inStream  = CliUtils.inputStreamFromFileOrStream(inputArg, invocation.stdIn());
                 var outStream = CliUtils.outputStreamToFileOrStream(outputArg, invocation.stdOut())) {
                CipherUtils.streamEncipher(inStream, outStream, cipher);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
