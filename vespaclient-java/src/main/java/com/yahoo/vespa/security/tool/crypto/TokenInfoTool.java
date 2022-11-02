// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.crypto;

import com.yahoo.security.SealedSharedKey;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.security.tool.Tool;
import com.yahoo.vespa.security.tool.ToolDescription;
import com.yahoo.vespa.security.tool.ToolInvocation;
import org.apache.commons.cli.Option;

import java.util.List;

import static com.yahoo.security.ArrayUtils.fromUtf8Bytes;
import static com.yahoo.security.ArrayUtils.hex;

/**
 * Tooling to dump the various components of a decryption token
 *
 * @author vekterli
 */
public class TokenInfoTool implements Tool {

    private static final List<Option> OPTIONS = List.of();

    @Override
    public String name() {
        return "token-info";
    }

    @Override
    public ToolDescription description() {
        return new ToolDescription(
                "<token string>",
                "Dumps information about the various components of a token",
                "Note: this is a BETA tool version; its interface may be changed at any time",
                OPTIONS);
    }

    @Override
    public int invoke(ToolInvocation invocation) {
        var arguments    = invocation.arguments();
        var leftoverArgs = arguments.getArgs();
        if (leftoverArgs.length != 1) {
            throw new IllegalArgumentException("Expected exactly 1 token string argument");
        }
        var token = SealedSharedKey.fromTokenString(leftoverArgs[0]);
        var stdOut = invocation.stdOut();

        stdOut.format("Version:         %d\n", token.tokenVersion());
        stdOut.format("Key ID:          %s (%s)\n", StringUtilities.escape(token.keyId().asString()), hex(token.keyId().asBytes()));
        stdOut.format("HPKE enc:        %s\n", hex(token.enc()));
        stdOut.format("HPKE ciphertext: %s\n", hex(token.ciphertext()));

        return 0;
    }
}
