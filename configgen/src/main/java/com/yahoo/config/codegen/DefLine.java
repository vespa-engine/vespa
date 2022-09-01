// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefLine {

    private final static Pattern defaultPattern =     Pattern.compile("^\\s*default\\s*=\\s*(\\S+)");
    private final static Pattern rangePattern =       Pattern.compile("^\\s*range\\s*=\\s*([\\(\\[].*?[\\)\\]])");
    private final static Pattern restartPattern =     Pattern.compile("^\\s*restart\\s*");
    private final static Pattern wordPattern =        Pattern.compile("\\S+");
    private final static Pattern enumPattern =        Pattern.compile("\\s*\\{(\\s*\\w+\\s*)+(\\s*,\\s*\\w+\\s*)*\\s*\\}");
    private final static Pattern enumPattern2 =       Pattern.compile("\\s*,\\s*");
    private final static Pattern wordPattern2 =       Pattern.compile("\\w+");
    private final static Pattern digitPattern =       Pattern.compile("\\d");
    private final static Pattern namePattern =        Pattern.compile("\\s*[a-zA-Z0-9_]+\\s*");
    private final static Pattern whitespacePattern =  Pattern.compile("\\s+");

    private String name = null;
    private final Type type = new Type();

    private DefaultValue defaultValue = null;

    private String range = null;
    private boolean restart = false;

    String enumString = null;
    final String[] enumArray = null;

    private final static Pattern defaultNullPattern = Pattern.compile("^\\s*default\\s*=\\s*null");

    public DefLine(String line) {
        StringBuilder sb = new StringBuilder(line);
        int parsed = parseNameType(sb);
        sb.delete(0, parsed);
        if (type.name.equals("enum")) {
            parsed = parseEnum(sb);
            sb.delete(0, parsed);
        }

        while (sb.length() > 0) {
            parsed = parseOptions(sb);
            sb.delete(0, parsed);
        }
        validateName();
        validateReservedWords();
    }

    /**
     * Currently (2012-03-05) not used. Ranges are not checked by the
     */
    public String getRange() {
        return range;
    }

    public DefaultValue getDefault() {
        return defaultValue;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean getRestart() {
        return restart;
    }

    public String getEnumString() {
        return enumString;
    }

    public String[] getEnumArray() {
        return enumArray;
    }

    /**
     * Special function that searches through s and returns the index
     * of the first occurrence of " that is not escaped.
     */
    private String findStringEnd(CharSequence s, int from) {
        boolean escaped = false;
        for (int i = from; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case'\\':
                    escaped = !escaped;
                    break;
                case'"':
                    if (!escaped) {
                        return s.subSequence(from, i).toString();
                    }
                    break;
            }
        }
        return null;
    }


    private int parseOptions(CharSequence string) {
        Matcher defaultNullMatcher = defaultNullPattern.matcher(string);
        Matcher defaultMatcher = defaultPattern.matcher(string);
        Matcher rangeMatcher = rangePattern.matcher(string);
        Matcher restartMatcher = restartPattern.matcher(string);

        if (defaultNullMatcher.find()) {
            throw new IllegalArgumentException("Null default value is not allowed: " + string.toString());
        } else if (defaultMatcher.find()) {
            String deflt = defaultMatcher.group(1);
            if (deflt.charAt(0) == '"') {
                int begin = defaultMatcher.start(1) + 1;
                deflt = findStringEnd(string, begin);
                if (deflt == null) {
                    throw new IllegalArgumentException(string.toString());
                }
                defaultValue = new DefaultValue(deflt, type);
                return begin + deflt.length() + 1;
            } else {
                defaultValue = new DefaultValue(deflt, type);
            }
            return defaultMatcher.end();
        } else if (rangeMatcher.find()) {
            range = rangeMatcher.group(1);
            return rangeMatcher.end();
        } else if (restartMatcher.find()) {
            restart = true;
            return restartMatcher.end();
        } else {
            throw new IllegalArgumentException(string.toString());
        }
    }

    private int parseNameType(CharSequence string) {
        Matcher wordMatcher = wordPattern.matcher(string);
        if (wordMatcher.find()) {
            name = wordMatcher.group();
        }
        if (wordMatcher.find()) {
            type.name = wordMatcher.group();
        }
        if (type.name == null || name == null) {
            throw new IllegalArgumentException(string.toString());
        }
        return wordMatcher.end();
    }

    private int parseEnum(CharSequence string) {
        Matcher enumMatcher = enumPattern.matcher(string);
        if (enumMatcher.find()) {
            enumString = enumMatcher.group(0).trim();
        }
        if (enumString == null) {
            throw new IllegalArgumentException(string + " is not valid syntax");
        }
        enumString = enumString.replaceFirst("\\{\\s*", "");
        enumString = enumString.replaceFirst("\\s*\\}", "");
        String result[] = enumPattern2.split(enumString);
        type.enumArray = new String[result.length];
        for (int i = 0; i < result.length; i++) {
            String s = result[i].trim();
            type.enumArray[i] = s;
            Matcher wordMatcher2 = wordPattern2.matcher(s);
            if (!wordMatcher2.matches()) {
                throw new IllegalArgumentException(s + " is not valid syntax");
            }
        }
        return enumMatcher.end();
    }

    public static class Type {
        String name;
        String[] enumArray;

        public Type(String name) {
            this.name=name;
        }

        public Type() {
        }

        public String getName() {
            return name;
        }

        public String[] getEnumArray() {
            return enumArray;
        }

        public Type setEnumArray(String[] enumArray) {
            this.enumArray = enumArray;
            return this;
        }

        public String toString() {
            return "type " + name;
        }

    }

    // A naive approach to imitate the checking previously done in make-config-preproc.pl
    // TODO: method too long
    void validateName() {
        Matcher digitMatcher;
        Matcher nameMatcher;
        Matcher whitespaceMatcher;

        boolean atStart = true;
        boolean arrayOk = true;
        boolean mapOk = true;
        for (int i = 0; i < name.length(); i++) {
            String s = name.substring(i, i + 1);
            digitMatcher = digitPattern.matcher(s);
            nameMatcher = namePattern.matcher(s);
            whitespaceMatcher = whitespacePattern.matcher(s);
            if (atStart) {
                if (digitMatcher.matches()) {
                    throw new IllegalArgumentException(name + " must start with a non-digit character");
                }
                if (!nameMatcher.matches()) {
                    throw new IllegalArgumentException(name + " contains unexpected character");
                }
                atStart = false;
            } else {
                if (nameMatcher.matches()) {
                    // do nothing
                } else if (s.equals(".")) {
                    arrayOk = true;
                    mapOk = true;
                    atStart = true;
                } else if (s.equals("[")) {
                    if (!arrayOk) {
                        throw new IllegalArgumentException(name + " Arrays cannot be multidimensional");
                    }
                    arrayOk = false;
                    if ((i > (name.length() - 2)) || !(name.substring(i + 1, i + 2).equals("]"))) {
                        throw new IllegalArgumentException(name + " Expected ] to terminate array definition");
                    }
                    i++;
                } else if (s.equals("{")) {
                    if (!mapOk) {
                        throw new IllegalArgumentException(name + " Maps cannot be multidimensional");
                    }
                    mapOk = false;
                    if ((i > (name.length() - 2)) || !(name.substring(i + 1, i + 2).equals("}"))) {
                        throw new IllegalArgumentException(name + " Expected } to terminate map definition");
                    }
                    i++;
                } else if (whitespaceMatcher.matches()) {
                    break;
                } else {
                    throw new IllegalArgumentException("'" + name + "' contains an unexpected character");
                }
            }
        }
    }

    private void validateReservedWords() {
        String cleanName = (name.endsWith("[]") || name.endsWith("{}")) ?  name.substring(0, name.length()-2) : name;

        if (ReservedWords.isReservedWord(cleanName)) {
           throw new IllegalArgumentException(cleanName + " is a reserved word in " +
                   ReservedWords.getLanguageForReservedWord(cleanName));
        }
        if (ReservedWords.capitalizedPattern.matcher(cleanName).matches()) {
            throw new IllegalArgumentException("'" + cleanName + "' cannot start with an uppercase letter");
        }
        if (ReservedWords.internalPrefixPattern.matcher(cleanName).matches()) {
            throw new IllegalArgumentException("'" + cleanName + "' cannot start with '" + ReservedWords.INTERNAL_PREFIX + "'");
        }
    }

}

