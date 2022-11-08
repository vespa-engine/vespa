// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.Base58;
import com.yahoo.security.Base62;
import com.yahoo.vespa.security.tool.CliUtils;
import com.yahoo.vespa.security.tool.Tool;
import com.yahoo.vespa.security.tool.ToolDescription;
import com.yahoo.vespa.security.tool.ToolInvocation;
import org.apache.commons.cli.Option;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

import static com.yahoo.security.ArrayUtils.fromUtf8Bytes;
import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.unhex;

/**
 * Simple tool to convert between different Base N encodings, for a fixed set of N.
 *
 * @author vekterli
 */
public class ConvertBaseTool implements Tool {

    private static final int MAX_IN_BYTES = 1024;

    static final String FROM_OPTION = "from";
    static final String TO_OPTION   = "to";

    private static final List<Option> OPTIONS = List.of(
            Option.builder("f")
                    .longOpt(FROM_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("From base. Supported values: 16, 58, 62, 64")
                    .build(),
            Option.builder("t")
                    .longOpt(TO_OPTION)
                    .hasArg(true)
                    .required(false)
                    .desc("To base. Supported values: 16, 58, 62, 64")
                    .build());

    @Override
    public String name() {
        return "convert-base";
    }

    @Override
    public ToolDescription description() {
        return new ToolDescription(
                "--from <base N> --to <base M>",
                ("Reads up to %d bytes of STDIN interpreted as a base N string (ignoring " +
                 "whitespace) and writes to STDOUT as a base M string. Note that base 64 is " +
                 "expected to be in (and is output as) the URL-safe alphabet (padding optional " +
                 "for input, no padding for output).").formatted(MAX_IN_BYTES),
                "Note: this is a BETA tool version; its interface may be changed at any time.",
                OPTIONS);
    }

    @Override
    public int invoke(ToolInvocation invocation) {
        try {
            var arguments  = invocation.arguments();
            var fromBase   = Integer.parseInt(CliUtils.optionOrThrow(arguments, FROM_OPTION));
            var toBase     = Integer.parseInt(CliUtils.optionOrThrow(arguments, TO_OPTION));
            // We cap the input length since non-base(16|64) transforms are O(n^2) and we don't want
            // to risk melting someone's CPU by them piping something large into the process by accident.
            byte[] inBytes = invocation.stdIn().readAllBytes();
            if (inBytes.length > MAX_IN_BYTES) {
                throw new IllegalArgumentException("Input size is too large (%d), max is %d"
                                                   .formatted(inBytes.length, MAX_IN_BYTES));
            }
            var inString = fromUtf8Bytes(inBytes).strip(); // We ignore whitespace to avoid trailing \n issues
            byte[] decoded = switch (fromBase) {
                case 16 -> unhex(inString);
                case 58 -> Base58.codec().decode(inString);
                case 62 -> Base62.codec().decode(inString);
                case 64 -> Base64.getUrlDecoder().decode(inString);
                default -> throw new IllegalArgumentException("Unsupported from-base: %d".formatted(fromBase));
            };
            String encoded = switch (toBase) {
                case 16 -> hex(decoded);
                case 58 -> Base58.codec().encode(decoded);
                case 62 -> Base62.codec().encode(decoded);
                case 64 -> Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
                default -> throw new IllegalArgumentException("Unsupported to-base: %d".formatted(toBase));
            };
            invocation.stdOut().println(encoded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return 0;
    }
}
