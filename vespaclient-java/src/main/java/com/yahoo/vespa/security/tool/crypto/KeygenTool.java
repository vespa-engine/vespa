// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.security.tool.CliUtils;
import com.yahoo.vespa.security.tool.Tool;
import com.yahoo.vespa.security.tool.ToolDescription;
import com.yahoo.vespa.security.tool.ToolInvocation;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.List;

/**
 * Tooling to generate random X25519 key pairs.
 *
 * @author vekterli
 */
public class KeygenTool implements Tool {

    static final String PRIVATE_OUT_FILE_OPTION    = "private-out-file";
    static final String PUBLIC_OUT_FILE_OPTION     = "public-out-file";
    static final String OVERWRITE_EXISTING_OPTION  = "overwrite-existing";

    private static final List<Option> OPTIONS = List.of(
            Option.builder("k")
                    .longOpt(PRIVATE_OUT_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Output file for private (secret) key. Will be created with restrictive file permissions.")
                    .build(),
            Option.builder("p")
                    .longOpt(PUBLIC_OUT_FILE_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("Output file for public key")
                    .build(),
            Option.builder()
                    .longOpt(OVERWRITE_EXISTING_OPTION)
                    .hasArg(false)
                    .required(false)
                    .desc("Overwrite existing key files instead of failing key generation if " +
                          "any files already exist. Use with great caution!")
                    .build());

    @Override
    public String name() {
        return "keygen";
    }

    @Override
    public ToolDescription description() {
        return new ToolDescription(
                "<options>",
                "Generates an X25519 key pair and stores its private/public parts in " +
                "separate files in Base58 encoded form.",
                "Note: this is a BETA tool version; its interface may be changed at any time",
                OPTIONS);
    }

    private static void verifyNotSameKeyPaths(Path privPath, Path pubPath) {
        if (privPath.equals(pubPath)) {
            throw new IllegalArgumentException("Private and public key output files must be different");
        }
    }

    private static void handleExistingFileIfAny(Path filePath, boolean allowOverwrite) throws IOException {
        if (Files.exists(filePath)) {
            if (!allowOverwrite) {
                throw new IllegalArgumentException(("Output file '%s' already exists. No keys written. " +
                                                    "If you want to overwrite existing files, specify --%s.")
                                                   .formatted(filePath.toAbsolutePath().toString(), OVERWRITE_EXISTING_OPTION));
            } else {
                // Explicitly delete the file since Files.createFile() will fail if it already exists.
                Files.delete(filePath);
            }
        }
    }

    @Override
    public int invoke(ToolInvocation invocation) {
        try {
            var arguments   = invocation.arguments();
            var privOutPath = Paths.get(CliUtils.optionOrThrow(arguments, PRIVATE_OUT_FILE_OPTION));
            var pubOutPath  = Paths.get(CliUtils.optionOrThrow(arguments, PUBLIC_OUT_FILE_OPTION));
            verifyNotSameKeyPaths(privOutPath, pubOutPath);

            boolean allowOverwrite = arguments.hasOption(OVERWRITE_EXISTING_OPTION);
            handleExistingFileIfAny(privOutPath, allowOverwrite);
            handleExistingFileIfAny(pubOutPath,  allowOverwrite);

            var keyPair = KeyUtils.generateX25519KeyPair();
            var privKey = (XECPrivateKey) keyPair.getPrivate();
            var pubKey  = (XECPublicKey) keyPair.getPublic();

            var privFilePerms = PosixFilePermissions.fromString("rw-------");
            Files.createFile( privOutPath, PosixFilePermissions.asFileAttribute(privFilePerms));
            Files.writeString(privOutPath, KeyUtils.toBase58EncodedX25519PrivateKey(privKey) + "\n");
            Files.writeString(pubOutPath,  KeyUtils.toBase58EncodedX25519PublicKey(pubKey) + "\n");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
