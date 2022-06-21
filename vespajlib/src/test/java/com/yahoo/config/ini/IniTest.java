package com.yahoo.config.ini;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author mpolden
 */
class IniTest {

    @Test
    public void parse() {
        String example = """
                key1 = no section
                []
                key2 = also no section  ; in-line comment
                ; a comment
                # another comment

                [foo]
                key3 =   "with spaces; and an escaped quote: \\"  "    # in-line comment
                key4 = \\"single leading escaped quote
                key1 =    leading whitespace unquoted
                key2 = "   leading whitespace quoted"
                key6 =

                [bar]
                key1=in section

                [foo]
                key5 = quote \\" in the middle
                """;
        Ini ini = parse(example);
        assertEquals(Map.of("", Map.of("key1", "no section",
                                       "key2", "also no section"),
                            "foo", Map.of("key1", "leading whitespace unquoted",
                                          "key2", "   leading whitespace quoted",
                                          "key3", "with spaces; and an escaped quote: \\\"  ",
                                          "key4", "\\\"single leading escaped quote",
                                          "key5", "quote \\\" in the middle",
                                          "key6", ""),
                            "bar", Map.of("key1", "in section")),
                     ini.entries());

        String expected = """
                key1 = no section
                key2 = also no section
                
                [bar]
                key1 = in section
                
                [foo]
                key1 = leading whitespace unquoted
                key2 = "   leading whitespace quoted"
                key3 = "with spaces; and an escaped quote: \\"  "
                key4 = "\\"single leading escaped quote"
                key5 = "quote \\" in the middle"
                key6 = ""
                """;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ini.write(out);
        String serialized = out.toString(StandardCharsets.UTF_8);
        assertEquals(expected, serialized);
        assertEquals(ini, parse(serialized));
    }

    @Test
    public void parse_invalid() {
        var tests = Map.of("key1\n",
                           "Invalid entry on line 1: 'key1': Expected key=[value]",

                           "key0 = ok\nkey1 = \"foo bar\" trailing stuff\n",
                           "Invalid entry on line 2: 'key1 = \"foo bar\" trailing stuff': Additional character(s) after end quote at column 8",

                           "[section1]\nkey0=foo\nkey0=bar\n",
                           "Invalid entry on line 3: 'key0=bar': Key 'key0' duplicated in section 'section1'",

                           "key1 = \"foo",
                           "Invalid entry on line 1: 'key1 = \"foo': Missing closing quote");
        tests.forEach((input, errorMessage) -> {
            try {
                parse(input);
                fail("Expected exception for input '" + input + "'");
            } catch (IllegalArgumentException e) {
                assertEquals(errorMessage, e.getMessage());
            }
        });
    }

    private static Ini parse(String ini) {
        return Ini.parse(new ByteArrayInputStream(ini.getBytes(StandardCharsets.UTF_8)));
    }

}
