// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.security.SharedKeyGenerator;
import com.yahoo.security.SharedKeyResealingSession;
import com.yahoo.vespa.security.tool.CliUtils;
import com.yahoo.vespa.security.tool.Tool;
import com.yahoo.vespa.security.tool.ToolDescription;
import com.yahoo.vespa.security.tool.ToolInvocation;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.security.tool.crypto.ToolUtils.NO_INTERACTIVE_OPTION;
import static com.yahoo.vespa.security.tool.crypto.ToolUtils.PRIVATE_KEY_DIR_OPTION;
import static com.yahoo.vespa.security.tool.crypto.ToolUtils.PRIVATE_KEY_FILE_OPTION;

/**
 * Tooling for resealing a token for another recipient. This allows for delegating
 * decryption to another party without having to reveal the private key of the original
 * recipient.
 *
 * @author vekterli
 */
public class ResealTool implements Tool {

    static final String EXPECTED_KEY_ID_OPTION      = "expected-key-id";
    static final String RECIPIENT_KEY_ID_OPTION     = "key-id";
    static final String RECIPIENT_PUBLIC_KEY_OPTION = "recipient-public-key";
    static final String RESEAL_REQUEST_OPTION       = "reseal-request";

    private static final List<Option> OPTIONS = List.of(
            Option.builder("k")
                    .longOpt(PRIVATE_KEY_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Private key file in Base58 encoded format")
                    .build(),
            Option.builder("d")
                    .longOpt(PRIVATE_KEY_DIR_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Private key file directory used for automatically looking up " +
                          "private keys based on the key ID specified as part of a token.")
                    .build(),
            Option.builder()
                    .longOpt(NO_INTERACTIVE_OPTION)
                    .hasArg(false)
                    .required(false)
                    .desc("Never ask for private key interactively if no private key file or " +
                          "directory is provided, even if process is running in a console")
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
                    .build(),
            Option.builder()
                    .longOpt(RESEAL_REQUEST_OPTION)
                    .hasArg(false)
                    .required(false)
                    .desc("Handle input as a resealing request instead of a token")
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
            var inputArg   = leftoverArgs[0].strip();
            var maybeKeyId = Optional.ofNullable(arguments.hasOption(EXPECTED_KEY_ID_OPTION)
                                                 ? arguments.getOptionValue(EXPECTED_KEY_ID_OPTION)
                                                 : null);
            if (arguments.hasOption(RESEAL_REQUEST_OPTION)) {
                handleResealingRequest(invocation, inputArg, maybeKeyId);
            } else {
                handleTokenResealing(invocation, arguments, inputArg, maybeKeyId);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    private static void handleTokenResealing(ToolInvocation invocation, CommandLine arguments, String inputArg, Optional<String> maybeKeyId) throws IOException {
        var sealedSharedKey = SealedSharedKey.fromTokenString(inputArg);
        ToolUtils.verifyExpectedKeyId(sealedSharedKey, maybeKeyId);

        var recipientPubKey = KeyUtils.fromBase58EncodedX25519PublicKey(CliUtils.optionOrThrow(arguments, RECIPIENT_PUBLIC_KEY_OPTION).strip());
        var recipientKeyId  = KeyId.ofString(CliUtils.optionOrThrow(arguments, RECIPIENT_KEY_ID_OPTION));
        var privateKey      = ToolUtils.resolvePrivateKeyFromInvocation(invocation, sealedSharedKey.keyId(), true);
        var secretShared    = SharedKeyGenerator.fromSealedKey(sealedSharedKey, privateKey);
        var resealedShared  = SharedKeyGenerator.reseal(secretShared, recipientPubKey, recipientKeyId);

        invocation.stdOut().println(resealedShared.sealedSharedKey().toTokenString());
    }

    private static void handleResealingRequest(ToolInvocation invocation, String inputArg, Optional<String> maybeKeyId) throws IOException {
        var request = SharedKeyResealingSession.ResealingRequest.fromSerializedString(inputArg);
        ToolUtils.verifyExpectedKeyId(request.sealedKey(), maybeKeyId);

        var privateKey = ToolUtils.resolvePrivateKeyFromInvocation(invocation, request.sealedKey().keyId(), true);
        var resealed   = SharedKeyResealingSession.reseal(request, (keyId) -> Optional.of(privateKey));

        invocation.stdOut().println(resealed.toSerializedString());
    }
}
