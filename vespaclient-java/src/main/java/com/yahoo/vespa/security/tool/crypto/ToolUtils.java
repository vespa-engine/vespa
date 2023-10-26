// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SealedSharedKey;
import com.yahoo.vespa.security.tool.CliUtils;
import com.yahoo.vespa.security.tool.ToolInvocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.interfaces.XECPrivateKey;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @author vekterli
 */
public class ToolUtils {

    static final String PRIVATE_KEY_FILE_OPTION = "private-key-file";
    static final String PRIVATE_KEY_DIR_OPTION  = "private-key-dir";
    static final String NO_INTERACTIVE_OPTION   = "no-interactive";
    static final String PRIVATE_KEY_DIR_ENV_VAR = "VESPA_CRYPTO_CLI_PRIVATE_KEY_DIR";

    static final Pattern SAFE_KEY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-][a-zA-Z0-9_.-]*$");

    static void verifyExpectedKeyId(SealedSharedKey sealedSharedKey, Optional<String> maybeKeyId) {
        if (maybeKeyId.isPresent()) {
            var myKeyId = KeyId.ofString(maybeKeyId.get());
            if (!myKeyId.equals(sealedSharedKey.keyId())) {
                // Don't include raw key bytes array verbatim in message (may contain control chars etc.)
                throw new IllegalArgumentException("Key ID specified with --expected-key-id does not match key ID " +
                                                   "used when generating the supplied token");
            }
        }
    }

    private static void verifyKeyIdIsPathSafe(KeyId keyId) {
        String keyIdStr = keyId.asString();
        if (!SAFE_KEY_ID_PATTERN.matcher(keyIdStr).matches()) {
            throw new IllegalArgumentException("The token key ID is not comprised of path-safe characters; refusing to use it");
        }
    }

    private static void verifyPrivateKeyFileNotWorldReadable(Path keyPath) throws IOException {
        var privKeyPerms = Files.getPosixFilePermissions(keyPath);
        if (privKeyPerms.contains(PosixFilePermission.OTHERS_READ)) {
            throw new IllegalArgumentException("Private key file '%s' is insecurely world-readable; refusing to read it"
                                               .formatted(keyPath.toAbsolutePath()));
        }
    }

    private static XECPrivateKey attemptResolvePrivateKeyFromDir(Path privKeyDirPath, KeyId tokenKeyId) throws IOException {
        if (!Files.isDirectory(privKeyDirPath)) {
            throw new IllegalArgumentException("'%s' is not a valid directory".formatted(privKeyDirPath.toAbsolutePath()));
        }
        verifyKeyIdIsPathSafe(tokenKeyId);
        var keyPath = privKeyDirPath.resolve(tokenKeyId.asString() + ".key");
        if (!Files.exists(keyPath)) {
            // We've verified the key ID contents, so we know it's safe to print here
            throw new IllegalArgumentException("Could not find a private key file matching token key ID '%s'"
                                               .formatted(tokenKeyId.asString()));
        }
        verifyPrivateKeyFileNotWorldReadable(keyPath);
        return KeyUtils.fromBase58EncodedX25519PrivateKey(Files.readString(keyPath).strip());
    }

    public static XECPrivateKey resolvePrivateKeyFromInvocation(ToolInvocation invocation, KeyId tokenKeyId, boolean mayReadKeyFromStdIn) throws IOException {
        var arguments = invocation.arguments();
        var envVars   = invocation.envVars();
        var console   = invocation.consoleInputOrNull();

        if (arguments.hasOption(PRIVATE_KEY_FILE_OPTION)) {
            if (arguments.hasOption(PRIVATE_KEY_DIR_OPTION)) {
                throw new IllegalArgumentException("--%s and --%s cannot be specified at the same time"
                                                   .formatted(PRIVATE_KEY_FILE_OPTION, PRIVATE_KEY_DIR_OPTION));
            }
            var privKeyFilePath = Paths.get(arguments.getOptionValue(PRIVATE_KEY_FILE_OPTION));
            invocation.printIfDebug(() -> "Using private key file '%s'".formatted(privKeyFilePath));
            if (!Files.exists(privKeyFilePath)) {
                throw new IllegalArgumentException("Specified private key file '%s' does not exist"
                                                   .formatted(privKeyFilePath.toAbsolutePath()));
            }
            verifyPrivateKeyFileNotWorldReadable(privKeyFilePath);
            return KeyUtils.fromBase58EncodedX25519PrivateKey(Files.readString(privKeyFilePath).strip());
        } else if (arguments.hasOption(PRIVATE_KEY_DIR_OPTION) || envVars.containsKey(PRIVATE_KEY_DIR_ENV_VAR)) {
            // Explicitly provided command line directory is preferred over env var, if set
            var privKeyDirPath = Paths.get(arguments.hasOption(PRIVATE_KEY_DIR_OPTION)
                                           ? arguments.getOptionValue(PRIVATE_KEY_DIR_OPTION)
                                           : envVars.get(PRIVATE_KEY_DIR_ENV_VAR));
            invocation.printIfDebug(() -> "Using private key lookup directory '%s'".formatted(privKeyDirPath));
            return attemptResolvePrivateKeyFromDir(privKeyDirPath, tokenKeyId);
        } else if (arguments.hasOption(NO_INTERACTIVE_OPTION) || (console == null) || !mayReadKeyFromStdIn) {
            throw new IllegalArgumentException("No private key specified. Must specify either --%s or --%s"
                                               .formatted(PRIVATE_KEY_FILE_OPTION, PRIVATE_KEY_DIR_OPTION));
        } else {
            // We have a console attached to the JVM, ask for private key interactively
            verifyKeyIdIsPathSafe(tokenKeyId); // Don't want to emit random stuff to the console
            String key = console.readPassword("Private key for key id '%s' in Base-58 format: ", tokenKeyId.asString());
            if (key.length() == 0) {
                throw new IllegalArgumentException("No private key provided; aborting");
            }
            return KeyUtils.fromBase58EncodedX25519PrivateKey(key);
        }
    }

}
