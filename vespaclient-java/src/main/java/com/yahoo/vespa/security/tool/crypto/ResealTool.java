// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyId;
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
 * Tooling for resealing a token for another recipient. This allows for delegating
 * decryption to another party without having to reveal the private key of the original
 * recipient.
 *
 * @author vekterli
 */
public class ResealTool implements Tool {

    static final String PRIVATE_KEY_FILE_OPTION     = "private-key-file";
    static final String EXPECTED_KEY_ID_OPTION      = "expected-key-id";
    static final String RECIPIENT_KEY_ID_OPTION     = "key-id";
    static final String RECIPIENT_PUBLIC_KEY_OPTION = "recipient-public-key";

    private static final List<Option> OPTIONS = List.of(
            Option.builder("k")
                    .longOpt(PRIVATE_KEY_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Private key file in Base58 encoded format")
                    .build(),
            Option.builder("e")
                    .longOpt(EXPECTED_KEY_ID_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Expected key ID in token. If this is not provided, the key ID is not verified.")
                    .build(),
            Option.builder("r")
                    .longOpt(RECIPIENT_PUBLIC_KEY_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Recipient X25519 public key in Base58 encoded format")
                    .build(),
            Option.builder("i")
                    .longOpt(RECIPIENT_KEY_ID_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("ID of recipient key")
                    .build());

    @Override
    public String name() {
        return "reseal";
    }

    @Override
    public ToolDescription description() {
        return new ToolDescription(
                "<token> <options>",
                "Reseals the input token for another recipient, allowing that recipient to " +
                "decrypt the file that the input token was originally created for.\n" +
                "Prints new token to STDOUT.",
                "Note: this is a BETA tool version; its interface may be changed at any time",
                OPTIONS);
    }

    @Override
    public int invoke(ToolInvocation invocation) {
        try {
            var arguments    = invocation.arguments();
            var leftoverArgs = arguments.getArgs();
            if (leftoverArgs.length != 1) {
                throw new IllegalArgumentException("Expected exactly 1 token argument to re-seal");
            }
            var tokenString = leftoverArgs[0];
            var maybeKeyId  = Optional.ofNullable(arguments.hasOption(EXPECTED_KEY_ID_OPTION)
                                                  ? arguments.getOptionValue(EXPECTED_KEY_ID_OPTION)
                                                  : null);
            var sealedSharedKey = SealedSharedKey.fromTokenString(tokenString.strip());
            ToolUtils.verifyExpectedKeyId(sealedSharedKey, maybeKeyId);

            var recipientPubKey = KeyUtils.fromBase58EncodedX25519PublicKey(CliUtils.optionOrThrow(arguments, RECIPIENT_PUBLIC_KEY_OPTION).strip());
            var recipientKeyId  = KeyId.ofString(CliUtils.optionOrThrow(arguments, RECIPIENT_KEY_ID_OPTION));
            var privKeyPath     = Paths.get(CliUtils.optionOrThrow(arguments, PRIVATE_KEY_FILE_OPTION));
            var privateKey      = KeyUtils.fromBase58EncodedX25519PrivateKey(Files.readString(privKeyPath).strip());
            var secretShared    = SharedKeyGenerator.fromSealedKey(sealedSharedKey, privateKey);
            var resealedShared  = SharedKeyGenerator.reseal(secretShared, recipientPubKey, recipientKeyId);

            invocation.stdOut().println(resealedShared.sealedSharedKey().toTokenString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
