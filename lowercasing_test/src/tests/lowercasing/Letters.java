// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

// generate output with all characters Java consider letters

public class Letters {

    static String toNFD(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD);
    }

    private final static Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /** drop diacritical marks */
    static String drop(String input) {
        return pattern.matcher(toNFD(input)).replaceAll("");
    }

    static String lower(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    static String genLetters() {
        StringBuilder s = new StringBuilder();
        for (int codepoint = 0; codepoint < 0x110000; codepoint++) {
            if (Character.isLetter(codepoint)) {
                s.appendCodePoint(codepoint);
                s.append('\n');
            }
        }
        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        String input = genLetters();
        String mode = "letters";
        if (args.length > 0) mode = args[0];
        String output = switch (mode) {
            case "lower" -> lower(input);
            case "drop" -> drop(input);
            case "lower-drop" -> drop(lower(input));
            case "drop-lower" -> lower(drop(input));
            case "letters" -> input;
            default -> "Unknown mode: " + mode;
        };
        System.out.write(output.getBytes(StandardCharsets.UTF_8));
    }

}
