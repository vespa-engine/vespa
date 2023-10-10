// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.*;

public class CharClassStats {

    public static class TypeStat {
        public final int typecode;
        public final String name;
        public final List<Integer> codepoints = new ArrayList<Integer>();

        TypeStat(int typecode) {
            this(typecode, "[???]");
        }
        TypeStat(int typecode, String name) {
            this.typecode = typecode;
            this.name = name;
        }
        void addCodepoint(int codepoint) {
            codepoints.add(codepoint);
        }
    }

    private static void init(Map<Integer, TypeStat> map) {

        TypeStat stat;
        stat = new TypeStat(Character.COMBINING_SPACING_MARK, "COMBINING_SPACING_MARK");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.CONNECTOR_PUNCTUATION, "CONNECTOR_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.CONTROL, "CONTROL");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.CURRENCY_SYMBOL, "CURRENCY_SYMBOL");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.DASH_PUNCTUATION, "DASH_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.DECIMAL_DIGIT_NUMBER, "DECIMAL_DIGIT_NUMBER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.ENCLOSING_MARK, "ENCLOSING_MARK");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.END_PUNCTUATION, "END_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.FINAL_QUOTE_PUNCTUATION, "FINAL_QUOTE_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.FORMAT, "FORMAT");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.INITIAL_QUOTE_PUNCTUATION, "INITIAL_QUOTE_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.LETTER_NUMBER, "LETTER_NUMBER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.LINE_SEPARATOR, "LINE_SEPARATOR");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.LOWERCASE_LETTER, "LOWERCASE_LETTER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.MATH_SYMBOL, "MATH_SYMBOL");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.MODIFIER_LETTER, "MODIFIER_LETTER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.MODIFIER_SYMBOL, "MODIFIER_SYMBOL");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.NON_SPACING_MARK, "NON_SPACING_MARK");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.OTHER_LETTER, "OTHER_LETTER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.OTHER_NUMBER, "OTHER_NUMBER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.OTHER_PUNCTUATION, "OTHER_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.OTHER_SYMBOL, "OTHER_SYMBOL");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.PARAGRAPH_SEPARATOR, "PARAGRAPH_SEPARATOR");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.PRIVATE_USE, "PRIVATE_USE");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.SPACE_SEPARATOR, "SPACE_SEPARATOR");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.START_PUNCTUATION, "START_PUNCTUATION");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.SURROGATE, "SURROGATE");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.TITLECASE_LETTER, "TITLECASE_LETTER");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.UNASSIGNED, "UNASSIGNED");
        map.put(stat.typecode, stat);
        stat = new TypeStat(Character.UPPERCASE_LETTER, "UPPERCASE_LETTER");
        map.put(stat.typecode, stat);
    }

    public static void main(String[] args) {
        Map<Integer, TypeStat> map = new HashMap<Integer, TypeStat>();

        init(map);

        for (int codepoint = 0; codepoint <= 0x110000; codepoint++) {
            int type = java.lang.Character.getType(codepoint);

            if (! map.containsKey(type)) {
                map.put(type, new TypeStat(type));
            }
            map.get(type).addCodepoint(codepoint);
        }

        int[] codes = new int[map.size()];
        int numcodes = 0;
        for (Integer type : map.keySet()) {
            codes[numcodes++] = type;
        }
        Arrays.sort(codes);
        for (int type : codes) {
            TypeStat ts = map.get(type);
            System.out.println("type "+type+" typecode="+ts.typecode+" name="+ts.name+" contains "+ts.codepoints.size()+" codepoints");
        }
    }

}
