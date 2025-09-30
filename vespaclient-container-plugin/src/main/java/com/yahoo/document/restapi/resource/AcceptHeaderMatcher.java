// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * A matcher for deducing which media types a client should ideally receive, as
 * communicated through the HTTP <code>Accept</code> header.
 * </p>
 * <p>
 * This sounds simple enough, but is complicated by the fact that a client may
 * accept <em>many</em> different media types, with varying levels of preference
 * between the types. Media types may also contain arbitrary key/value parameters
 * whose values may be quoted strings. This means that naive string matching or
 * splitting may have subtle yet exciting failure modes. As a consequence, this
 * matcher implements a tiny, bespoke parser for the micro-DSL of the Accept header.
 * </p>
 *
 * @see <a href="https://httpwg.org/specs/rfc9110.html#field.accept">RFC 9110</a>
 *
 * @author vekterli
 */
public class AcceptHeaderMatcher {

    private final Map<String, Double> qualityByNormalizedTypeName;

    /**
     * @param input raw HTTP Accept header <em>value</em>
     * @throws IllegalArgumentException if header parsing fails
     */
    public AcceptHeaderMatcher(String input) {
        var parser = new Parser(new Lexer(input));

        qualityByNormalizedTypeName = parser.mediaRanges().stream()
                .collect(Collectors.toMap(mr -> lowerCasedTypeName(mr.mediaType.cachedFullType()),
                                          MediaRange::quality,
                                          Math::max)); // Use the best quality when collapsing multiple entries
    }

    private record RankedMediaType(String type, double quality, int rank) implements Comparable<RankedMediaType> {
        @Override
        public int compareTo(RankedMediaType rhs) {
            int qualityCmp = Double.compare(rhs.quality, quality); // Prefer higher quality
            if (qualityCmp != 0) {
                return qualityCmp;
            }
            return Integer.compare(rank, rhs.rank); // Prefer lower ranks
        }
    }

    /**
     * <p>Get the most preferred accepted media types that <em>exactly</em> (but case-
     * insensitively) matches one of the types in <strong>types</strong>. Exact matching
     * means that wildcards are not supported.</p>
     *
     * Example:
     * <ul>
     *     <li><code>Accept: text/plain</code> matches <code>text/plain</code>.</li>
     *     <li><code>Accept: text/*</code> does <em>not</em> match <code>text/plain</code>.</li>
     * </ul>
     *
     * <p>In the case that multiple media types have the same quality (weight), these will
     * be ordered in the result to be in the order specified in <code>types</code>.</p>
     *
     * @param types One or more media types in <code>type/subtype</code> format. This does
     *              <em>not</em> support matching against media type parameters.
     * @return The best exactly matching media types, or an empty list if no such media types were present.
     */
    public List<String> preferredExactMediaTypes(String... types) {
        var res = new ArrayList<RankedMediaType>();
        int callerRank = 0;
        for (String candidate : types) {
            Double maybeMatch = qualityByNormalizedTypeName.get(lowerCasedTypeName(candidate));
            if (maybeMatch != null) {
                res.add(new RankedMediaType(candidate, maybeMatch, callerRank));
                callerRank++;
            }
        }
        return res.stream().sorted().map(r -> r.type).toList();
    }

    private static String lowerCasedTypeName(String in) {
        // Media type names are ASCII-only, so US locale suffices.
        return in.toLowerCase(Locale.US);
    }

    private record MediaType(String type, String subtype, String cachedFullType) {}
    private record Parameter(String key, String value) {}
    private record Parameters(List<Parameter> params) {}
    // Note: if quality is specified in the header, a "q" parameter will also be present
    // since we don't bother to filter those out. If no quality value has been specified,
    // it is implicitly 1.0.
    private record MediaRange(MediaType mediaType, Parameters parameters, double quality) {}

    // When faced with the decision of whether to write a small custom recursive descent
    // parser or a Most Heinous Regex From Outer Space, choosing the former feels nicer.
    // There is no backtracking needed in this particular parser, so runtime is O(n).

    private enum TokenType {
        TOKEN, // Naming from RFC 9110, covers both identifiers and numbers... :I
        WS, // Whitespace (space + horizontal tab)
        QUOTED_STRING,
        COMMA,
        FWD_SLASH,
        SEMICOLON,
        EQUALS,
        EOF
    }

    private record Token(TokenType type, String lexeme) {
        static Token of(TokenType type, String lexeme) {
            return new Token(type, lexeme);
        }
        static Token of(TokenType type) {
            return new Token(type, "");
        }
    }

    private static class Lexer {

        private static final Token WS_TOKEN        = Token.of(TokenType.WS, " ");
        private static final Token COMMA_TOKEN     = Token.of(TokenType.COMMA, ",");
        private static final Token SEMICOLON_TOKEN = Token.of(TokenType.SEMICOLON, ";");
        private static final Token FWD_SLASH_TOKEN = Token.of(TokenType.FWD_SLASH, "/");
        private static final Token EQUALS_TOKEN    = Token.of(TokenType.EQUALS, "=");
        private static final Token EOF_TOKEN       = Token.of(TokenType.EOF);

        private int tokStart = 0;
        private int curIndex = 0;
        private final String input;
        private final int len;
        private Token curToken;

        Lexer(String input) {
            this.input = input;
            this.len = input.length();
            advance(); // seed the lexer with a current token
        }

        void advance() {
            tokStart = curIndex;
            if (atEnd()) {
                curToken = EOF_TOKEN;
                return;
            }
            // Always read at least 1 input char. The Accept-grammar is simple
            // enough that this char suffices to tell us the type of the token.
            char ch = advanceChar();
            curToken = switch (ch) {
                case ' ', '\t' -> WS_TOKEN;
                case '\"'      -> lexQuotedString();
                case ','       -> COMMA_TOKEN;
                case ';'       -> SEMICOLON_TOKEN;
                case '/'       -> FWD_SLASH_TOKEN;
                case '='       -> EQUALS_TOKEN;
                default -> {
                    if (isTokenChar(ch)) {
                        yield lexToken();
                    }
                    throw new IllegalArgumentException("failed to lex next token at index %d".formatted(tokStart));
                }
            };
        }

        /**
         * 5.6.2. Tokens
         * <pre>
         *   token          = 1*tchar
         *
         *   tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
         *                  / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
         *                  / DIGIT / ALPHA
         *                  ; any VCHAR, except delimiters
         * </pre>
         */
        private static boolean isTokenChar(char ch) {
            return (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || "!#$%&'*+-.^_`|~".indexOf(ch) != -1;
        }

        private Token lexToken() {
            while (!atEnd() && isTokenChar(input.charAt(curIndex))) {
                curIndex++;
            }
            return Token.of(TokenType.TOKEN, input.substring(tokStart, curIndex));
        }

        /**
         * 5.6.4. Quoted String
         * <pre>
         *   quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE
         *   qdtext         = HTAB / SP / %x21 / %x23-5B / %x5D-7E / obs-text
         *   quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text )
         * </pre>
         *
         * We simplify by ignoring any and all escape sequences except for double quotes.
         * Just need to do enough to not get confused by characters that are within a quoted string.
         */
        private Token lexQuotedString() {
            while (!atEnd()) {
                char ch = advanceChar();
                if (ch == '\"') {
                    // Note: currently includes surrounding quote char pair in the token
                    return Token.of(TokenType.QUOTED_STRING, input.substring(tokStart, curIndex));
                } else if (ch == '\\') {
                    if (atEnd()) {
                        throw new IllegalArgumentException("incomplete character escape sequence");
                    }
                    curIndex++; // consume escaped char verbatim; we don't care to unescape it.
                }
            }
            throw new IllegalArgumentException("quoted string not terminated");
        }

        private boolean atEnd() {
            return curIndex == len;
        }

        // Precondition: !atEnd()
        private char advanceChar() {
            char ch = input.charAt(curIndex);
            curIndex++;
            return ch;
        }

        Token peek() {
            return curToken;
        }

    }

    private static class Parser {

        private final Lexer lexer;

        Parser(Lexer lexer) {
            this.lexer = lexer;
        }

        /**
         * 12.5.1. Accept
         * <pre>
         *  Accept = #( media-range [ weight ] )
         * </pre>
         * Note: we parse the quality (weight) as a generic, optional parameter, then
         * process it afterward. This is a bit cheaty (since we're not validating that
         * no other parameters have a name of "q"), but has the same outcome in practice.
         *
         * 5.6.1.2. "Recipient Requirements" states that:
         * <blockquote>
         *     Empty elements do not contribute to the count of elements present.
         *     A recipient MUST parse and ignore a reasonable number of empty list elements
         * </blockquote>
         * This is very silly, but we still allow for it. Since we MUST(tm).
         */
        List<MediaRange> mediaRanges() {
            var ranges = new ArrayList<MediaRange>();
            while (!eof()) {
                skipWs();
                if (matchAndAdvance(TokenType.COMMA)) {
                    continue; // Silly empty list element
                }
                ranges.add(mediaRange());
                skipWs();
                if (!matchAndAdvance(TokenType.COMMA)) {
                    break;
                }
            }
            if (!eof()) {
                throw new IllegalArgumentException("unparsed input at end");
            }
            return ranges;
        }

        /**
         * 12.5.1. Accept
         * <pre>
         *  media-range  = ( "* /*"  (sans whitespace, breaks comment...)
         *                 / ( type "/" "*" )
         *                 / ( type "/" subtype )
         *                 ) parameters
         * </pre>
         * As mentioned in {@link #mediaRanges()} we parse the weight as-if it's a parameter.
         * <pre>
         *  weight = OWS ";" OWS "q=" qvalue
         * </pre>
         */
        private MediaRange mediaRange() {
            MediaType mediaType = mediaType();
            Parameters params = parameters();
            double quality = 1;
            for (var p : params.params()) {
                // It's technically not allowed with more than one q= (and it should always be at
                // the end), but for simplicity allow for multiple and use the last one present.
                if (p.key.equals("q")) {
                    quality = parseQvalue(p.value);
                }
            }
            return new MediaRange(mediaType, params, quality);
        }

        /**
         * 12.4.2. Quality Values:
         * <pre>
         *   qvalue = ( "0" [ "." 0*3DIGIT ] )
         *          / ( "1" [ "." 0*3("0") ] )
         * </pre>
         */
        private static final Pattern QVAL_PATTERN = Pattern.compile("^(0(\\.\\d{0,3})?|1(\\.0{0,3})?)$");

        private static double parseQvalue(String paramValue) {
            if (QVAL_PATTERN.matcher(paramValue).matches()) {
                return Double.parseDouble(paramValue);
            } else {
                throw new IllegalArgumentException("bad quality parameter value");
            }
        }

        /**
         * 8.3.1. Media Type
         * <pre>
         *   media-type = type "/" subtype parameters
         *   type       = token
         *   subtype    = token
         * </pre>
         */
        private MediaType mediaType() {
            String type = token();
            expectAndAdvance(TokenType.FWD_SLASH);
            String subtype = token();
            return new MediaType(type, subtype, "%s/%s".formatted(type, subtype));
        }

        /**
         * 5.6.6. Parameters
         * <pre>
         *   parameters      = *( OWS ";" OWS [ parameter ] )
         * </pre>
         */
        private Parameters parameters() {
            var params = new ArrayList<Parameter>();
            while (true) {
                skipWs();
                if (!matchAndAdvance(TokenType.SEMICOLON)) {
                    break;
                }
                skipWs();
                params.add(parameter());
            }
            return new Parameters(params);
        }

        /**
         * 5.6.6. Parameters
         * <pre>
         *   parameter       = parameter-name "=" parameter-value
         *   parameter-name  = token
         *   parameter-value = ( token / quoted-string )
         * </pre>
         */
        private Parameter parameter() {
            String name = token();
            expectAndAdvance(TokenType.EQUALS);
            String value;
            if (matches(TokenType.TOKEN)) {
                value = token();
            } else {
                value = quotedString();
            }
            return new Parameter(name, value);
        }

        private String token() {
            return expectAndAdvance(TokenType.TOKEN).lexeme;
        }

        private String quotedString() {
            return expectAndAdvance(TokenType.QUOTED_STRING).lexeme; // not unescaped
        }

        private boolean matches(TokenType type) {
            return lexer.peek().type == type;
        }

        private boolean mismatches(TokenType type) {
            return lexer.peek().type != type;
        }

        private boolean eof() {
            return matches(TokenType.EOF);
        }

        private boolean matchAndAdvance(TokenType type) {
            if (matches(type)) {
                lexer.advance();
                return true;
            }
            return false;
        }

        private void skipWs() {
            while (matches(TokenType.WS)) {
                lexer.advance();
            }
        }

        // Returns token _prior_ to advancing the lexer
        private Token advance() {
            Token ret = lexer.peek();
            lexer.advance();
            return ret;
        }

        private Token expectAndAdvance(TokenType expectedType) {
            if (mismatches(expectedType)) {
                throw new IllegalArgumentException("Expected token of type %s, got %s".formatted(expectedType, lexer.peek().type));
            }
            return advance();
        }

    }

}
