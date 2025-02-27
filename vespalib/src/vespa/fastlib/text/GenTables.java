// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.util.Formatter;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

public class GenTables {

    static String genTables() {
        StringBuilder after = new StringBuilder();
        after.append("// tables for range [0000,FFFF]\n");
        after.append("static const char16_t *javaLower16bitTables[] = {\n");
        StringBuilder s = new StringBuilder();
        Formatter fmt = new Formatter(s, Locale.ROOT);
        int[] curr = new int[256];
        for (int table = 0; table < 490 /* 0x1100 */; table++) {
            if (table == 0x100) {
                after.append("};\n\n");
                after.append("// tables for range [10000,110000]\n");
                after.append("static const char32_t *javaLower32bitTables[] = {\n");
            }
            int useful = 0;
            for (int i = 0; i < 0x100; i++) {
                curr[i] = 0;
                int codepoint = (table << 8) + i;
                if (Character.isLetter(codepoint)) {
                    int lower = Character.toLowerCase(codepoint);
                    if (lower != codepoint) {
                        curr[i] = lower;
                        ++useful;
                    }
                }
            }
            if (useful > 0) {
                s.append("// Table ").append(table).append(" with ").append(useful).append(" useful entries\n");
                if (table < 0x100) {
                    s.append("static const char16_t javaLowerCaseTable_").append(table).append("_data[256] = {\n");
                } else {
                    s.append("static const char32_t javaLowerCaseTable_").append(table).append("_data[256] = {\n");
                }
                for (int row = 0; row < 32; row++) {
                    s.append("   ");
                    for (int col = 0; col < 8; col++) {
                        int idx = row * 8 + col;
                        int val = curr[idx];
                        if (val == 0) {
                            s.append("       0u");
                        } else {
                            fmt.format(" 0x%1$05Xu", val);
                        }
                        if (idx < 255) s.append(",");
                    }
                    s.append("\n");
                }
                s.append("};\n\n");
                after.append("    javaLowerCaseTable_").append(table).append("_data,\n");
            } else {
                after.append("    nullptr,\n");
            }
        }
        after.append("};\n\n");
        s.append(after);
        s.append(
                """
                namespace vespalib::javagenerated {

                char32_t getLowerCaseVariant(char32_t codepoint) {
                    int table = codepoint >> 8;
                    if (table < 256) {
                        const char16_t *ptr = javaLower16bitTables[table];
                        if (ptr != nullptr) {
                            char32_t val = ptr[codepoint & 0xFF];
                            if (val != 0) return val;
                        }
                    } else if (table < 490) {
                        const char32_t *ptr = javaLower32bitTables[table - 256];
                        if (ptr != nullptr) {
                            char32_t val = ptr[codepoint & 0xFF];
                            if (val != 0) return val;
                        }
                    }
                    return codepoint;
                }

                } // namespace
                """);
        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        String output = genTables();
        System.out.write(output.getBytes(StandardCharsets.UTF_8));
    }

}
