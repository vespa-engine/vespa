// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ExportPackageParser {
    public static List<ExportPackages.Export> parseExports(String exportAttribute) {
        ParsingContext p = new ParsingContext(exportAttribute.trim());

        List<ExportPackages.Export> exports = parseExportPackage(p);
        if (exports.isEmpty()) {
            p.fail("Expected a list of exports");
        } else if (p.atEnd() == false) {
            p.fail("Exports not fully processed");
        }
        return exports;
    }

    private static class ParsingContext {
        private enum State {
            Invalid, WantMore, End
        }

        private CharSequence input;
        private int pos;
        private State state;
        private int length;
        private char ch;

        private ParsingContext(CharSequence input) {
            this.input = input;
            this.pos = 0;
        }

        private Optional<String> read(Consumer<ParsingContext> rule) {
            StringBuilder ret = new StringBuilder();

            parse: while (true) {
                if (input.length() < pos + 1) {
                    break;
                }
                ch = input.charAt(pos);
                state = State.WantMore;
                length = ret.length();
                rule.accept(this);

                switch (state) {
                case Invalid:
                    if (ret.length() == 0) {
                        break parse;
                    } else {
                        String printable = Character.isISOControl(ch) ? "#" + Integer.toString((int) ch)
                                : "[" + Character.toString(ch) + "]";
                        pos++;
                        fail("Character " + printable + " was not acceptable");
                    }
                    break;
                case WantMore:
                    ret.append(ch);
                    pos++;
                    break;
                case End:
                    break parse;
                }
            }

            if (ret.length() == 0) {
                return Optional.empty();
            } else {
                return Optional.of(ret.toString());
            }
        }

        private Optional<String> regexp(Pattern pattern) {
            Matcher matcher = pattern.matcher(input);
            matcher.region(pos, input.length());
            if (matcher.lookingAt()) {
                String value = matcher.group();
                pos += value.length();
                return Optional.of(value);
            } else {
                return Optional.empty();
            }
        }

        private Optional<String> exactly(String string) {
            if (input.length() - pos < string.length()) {
                return Optional.empty();
            }
            if (input.subSequence(pos, pos + string.length()).equals(string)) {
                pos += string.length();
                return Optional.of(string);
            }
            return Optional.empty();
        }

        private boolean atEnd() {
            return pos == input.length();
        }

        private void invalid() {
            this.state = State.Invalid;
        }

        private void end() {
            this.state = State.End;
        }

        private void fail(String message) {
            throw new RuntimeException("Failed parsing Export-Package: " + message + " at position " + pos);
        }
    }

    /* ident = ? a valid Java identifier ? */
    private static Optional<String> parseIdent(ParsingContext p) {
        Optional<String> ident = p.read(ctx -> {
            if (ctx.length == 0) {
                if (Character.isJavaIdentifierStart(ctx.ch) == false) {
                    ctx.invalid();
                }
            } else {
                if (Character.isJavaIdentifierPart(ctx.ch) == false) {
                    ctx.end();
                }
            }
        });
        return ident;
    }

    /* stringLiteral = ? sequence of any character except double quotes, control characters or backslash,
         a backslash followed by another backslash, a single or double quote, or one of the letters b,f,n,r or t
         a backslash followed by u followed by four hexadecimal digits ? */
    private static Pattern STRING_LITERAL_PATTERN = Pattern
            .compile("\"" + "(?:[^\"\\p{Cntrl}\\\\]|\\\\[\\\\'\"bfnrt]|\\\\u[0-9a-fA-F]{4})+" + "\"");

    private static Optional<String> parseStringLiteral(ParsingContext p) {
        return p.regexp(STRING_LITERAL_PATTERN).map(quoted -> quoted.substring(1, quoted.length() - 1));
    }

    /* extended = { \p{Alnum} | '_' | '-' | '.' }+ */
    private static Pattern EXTENDED_PATTERN = Pattern.compile("[\\p{Alnum}_.-]+");

    private static Optional<String> parseExtended(ParsingContext p) {
        return p.regexp(EXTENDED_PATTERN);
    }

    /* argument = extended | stringLiteral | ? failure ? */
    private static String parseArgument(ParsingContext p) {
        Optional<String> argument = parseExtended(p);
        if (argument.isPresent() == false) {
            argument = parseStringLiteral(p);
        }
        if (argument.isPresent() == false) {
            p.fail("Expected an extended token or a string literal");
        }
        return argument.get();
    }

    /*
     * parameter = ( directive | attribute )
     * directive = extended, ':=', argument
     * attribute = extended, '=', argument
     */
    private static Pattern DIRECTIVE_OR_ATTRIBUTE_SEPARATOR_PATTERN = Pattern.compile("\\s*:?=\\s*");

    private static Optional<ExportPackages.Parameter> parseParameter(ParsingContext p) {
        int backtrack = p.pos;
        Optional<String> ext = parseExtended(p);
        if (ext.isPresent()) {
            Optional<String> sep = p.regexp(DIRECTIVE_OR_ATTRIBUTE_SEPARATOR_PATTERN);
            if (sep.isPresent() == false) {
                p.pos = backtrack;
                return Optional.empty();
            }
            String argument = parseArgument(p);
            return Optional.of(new ExportPackages.Parameter(ext.get(), argument));
        } else {
            return Optional.empty();
        }
    }

    /* parameters = parameter, { ';' parameter } */
    private static Pattern PARAMETER_SEPARATOR_PATTERN = Pattern.compile("\\s*;\\s*");

    private static List<ExportPackages.Parameter> parseParameters(ParsingContext p) {
        List<ExportPackages.Parameter> params = new ArrayList<>();
        boolean wantMore = true;
        do {
            Optional<ExportPackages.Parameter> param = parseParameter(p);
            if (param.isPresent()) {
                params.add(param.get());
                wantMore = p.regexp(PARAMETER_SEPARATOR_PATTERN).isPresent();
            } else {
                wantMore = false;
            }
        } while (wantMore);

        return params;
    }

    /* packageName = ident, { '.', ident } */
    private static Optional<String> parsePackageName(ParsingContext p) {
        StringBuilder ret = new StringBuilder();

        boolean wantMore = true;
        do {
            Optional<String> ident = parseIdent(p);
            if (ident.isPresent()) {
                ret.append(ident.get());
                Optional<String> separator = p.exactly(".");
                if (separator.isPresent()) {
                    ret.append(separator.get());
                    wantMore = true;
                } else {
                    wantMore = false;
                }
            } else {
                wantMore = false;
            }
        } while (wantMore);

        if (ret.length() > 0) {
            return Optional.of(ret.toString());
        } else {
            return Optional.empty();
        }
    }

    /* export = packageName, [ ';', ( parameters | export ) ] */
    private static ExportPackages.Export parseExport(ParsingContext p) {
        List<String> exports = new ArrayList<>();

        boolean wantMore = true;
        do {
            if (exports.isEmpty() == false) { // second+ iteration
                List<ExportPackages.Parameter> params = parseParameters(p);
                if (params.isEmpty() == false) {
                    return new ExportPackages.Export(exports, params);
                }
            }

            Optional<String> packageName = parsePackageName(p);
            if (packageName.isPresent()) {
                exports.add(packageName.get());
            } else {
                p.fail(exports.isEmpty() ? "Expected a package name" : "Expected either a package name or a parameter list");
            }

            wantMore = p.regexp(PARAMETER_SEPARATOR_PATTERN).isPresent();
        } while (wantMore);

        return new ExportPackages.Export(exports, new ArrayList<>());
    }

    /* exportPackage = export, { ',', export } */
    private static Pattern EXPORT_SEPARATOR_PATTERN = Pattern.compile("\\s*,\\s*");

    private static List<ExportPackages.Export> parseExportPackage(ParsingContext p) {
        List<ExportPackages.Export> exports = new ArrayList<>();

        boolean wantMore = true;
        do {
            ExportPackages.Export export = parseExport(p);
            if (export.getPackageNames().isEmpty()) {
                wantMore = false;
            } else {
                exports.add(export);
                wantMore = p.regexp(EXPORT_SEPARATOR_PATTERN).isPresent();
            }
        } while (wantMore);

        return exports;
    }
}
