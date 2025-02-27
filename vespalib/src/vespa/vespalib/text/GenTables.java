// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.nio.charset.StandardCharsets;

// program to generate code tables used by LowerCase::convert()

public class GenTables {

    static int fillTable(int table, int[] curr) {
        int useful = 0;
        for (int i = 0; i < 0x100; i++) {
            int codepoint = (table << 8) + i;
            curr[i] = codepoint;
            if (Character.isLetter(codepoint)) {
                int lower = Character.toLowerCase(codepoint);
                curr[i] = lower;
                if (lower != codepoint) ++useful;
            }
        }
        return useful;
    }

    static void dumpTable(int[] curr, StringBuilder s) {
        for (int row = 0; row < 32; row++) {
            s.append('\n');
            int nextpos = s.length() + 4;
            for (int col = 0; col < 8; col++) {
                int idx = row * 8 + col;
                int val = curr[idx];
                while (s.length() < nextpos) s.append(' ');
                s.append(val);
                if (idx < 255) s.append(",");
                nextpos += 8;
            }
        }
    }

    static void genTable(int table, StringBuilder s) {
        int[] curr = new int[256];
        fillTable(table, curr);
        dumpTable(curr, s);
    }

    static void genNormal(int table, StringBuilder s) {
        int[] curr = new int[256];
        int useful = fillTable(table, curr);
        if (useful > 0) {
            s.append("\nuint32_t\n");
            s.append("LowerCase::lowercase_").append(table).append("_block[256] = {");
            dumpTable(curr, s);
            s.append("\n};\n\n");
        }
    }

    static String genTables() {
        StringBuilder s = new StringBuilder();
        s.append("unsigned char\n");
        s.append("LowerCase::lowercase_0_block[256] = {");
        genTable(0, s);
        s.append("\n};\n\n");

        for (int i = 1; i < 0x1100; i++) {
            genNormal(i, s);
        }

        s.append("// special performance hack:\n");
        s.append("// concatenation of blocks 0,1,2,3,4,5\n");

        s.append("\nuint32_t\n");
        s.append("LowerCase::lowercase_0_5_blocks[0x600] = {");
        genTable(0, s); s.append(',');
        genTable(1, s); s.append(',');
        genTable(2, s); s.append(',');
        genTable(3, s); s.append(',');
        genTable(4, s); s.append(',');
        genTable(5, s);
        s.append("\n};\n");

        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        String output = genTables();
        System.out.write(output.getBytes(StandardCharsets.UTF_8));
    }

}
