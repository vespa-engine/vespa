// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import org.apache.commons.cli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * @author vekterli
 */
public class CliUtils {

    public static String optionOrThrow(CommandLine arguments, String option) {
        var value = arguments.getOptionValue(option);
        if (value == null) {
            throw new IllegalArgumentException("Required argument '--%s' must be provided".formatted(option));
        }
        return value;
    }

    public static boolean useStdIo(String pathOrDash) {
        return "-".equals(pathOrDash);
    }

    public static InputStream inputStreamFromFileOrStream(String pathOrDash, InputStream stdIn) throws IOException {
        if (useStdIo(pathOrDash)) {
            return stdIn;
        } else {
            var inputPath = Paths.get(pathOrDash);
            if (!Files.exists(inputPath)) {
                throw new IllegalArgumentException("Input file '%s' does not exist".formatted(inputPath.toString()));
            }
            return Files.newInputStream(inputPath);
        }
    }

    public static OutputStream outputStreamToFileOrStream(String pathOrDash, OutputStream stdOut) throws IOException {
        if (useStdIo(pathOrDash)) {
            return stdOut;
        } else {
            // TODO fail if file already exists?
            var privFilePerms = PosixFilePermissions.fromString("rw-------");
            var outPath = Paths.get(pathOrDash);
            Files.createFile(outPath, PosixFilePermissions.asFileAttribute(privFilePerms));
            return Files.newOutputStream(outPath);
        }
    }

}
