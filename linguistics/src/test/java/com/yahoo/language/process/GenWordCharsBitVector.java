// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.process;
import java.nio.charset.StandardCharsets;

// program to generate code tables used by Fast_UnicodeUtil::IsWordChar()

public class GenWordCharsBitVector {

    static CharacterClasses cc = new CharacterClasses();

    static boolean isWordChar(int codepoint) {
        return cc.isLetterOrDigit(codepoint);
    }

    // Each block covers 0x100 codepoints. Currently there is nothing
    // useful beyond the first 804 blocks:
    // 323AF is <CJK Ideograph Extension H, Last>
    static final int maxCodeBlocks = 0x324;

    static String genTable() {
        StringBuilder s = new StringBuilder();
        s.append("unsigned long Fast_UnicodeUtil::_wordCharBits[");
        s.append(maxCodeBlocks * 0x100 / 64);
        s.append("] = {\n");
        for (int codepoint = 0; codepoint < maxCodeBlocks * 0x100; ) {
            int nextpos = s.length() + 4;
            for (int w = 0; w < 4; w++) {
                long val = 0;
                for (int j = 0; j < 64; j++) {
                    if (isWordChar(codepoint)) {
                        val |= (1L << j);
                    }
                    ++codepoint;
                }
                while (s.length() < nextpos) s.append(' ');
                nextpos += 18;
                s.append("0x");
                String hex = Long.toHexString(val).toUpperCase();
                while (s.length() + hex.length() < nextpos) s.append('0');
                s.append(hex);
                if (codepoint + 1 < maxCodeBlocks * 0x100) s.append(",");
                nextpos += 4;
            }
            s.append("\n");
        }
        s.append("};\n\n");
        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        String output = genTable();
        System.out.write(output.getBytes(StandardCharsets.UTF_8));
    }

}
