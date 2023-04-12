// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Linguistics;
import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.SpecialTokens;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.Substring;

import java.util.Collections;
import java.util.List;

import static com.yahoo.prelude.query.parser.Token.Kind.*;

/**
 * Query tokenizer. Singlethreaded.
 *
 * @author bratseth
 */
public final class Tokenizer {

    private final List<Token> tokens = new java.util.ArrayList<>();

    private String source;

    /** Tokens which should be words, regardless of which characters they contain */
    private SpecialTokens specialTokens = null;

    /** Whether to recognize tokens also as substrings of other tokens, needed for cjk */
    private boolean substringSpecialTokens = false;

    private final CharacterClasses characterClasses;

    private int parensToEat = 0;

    private int indexLastExplicitlyChangedAt = 0;

    /** Creates a tokenizer which initializes from a given Linguistics */
    public Tokenizer(Linguistics linguistics) {
        this.characterClasses = linguistics.getCharacterClasses();
    }

    /**
     * Sets a list of tokens (Strings) which should be returned as WORD tokens regardless
     * of their content. This list is used directly by the Tokenizer and should not be changed
     * after calling this. The tokenizer will not change it. Special tokens are case
     * sensitive.
     */
    public void setSpecialTokens(SpecialTokens specialTokens) {
        this.specialTokens = specialTokens;
    }

    /** Sets whether to recognize tokens also as substrings of other tokens, needed for cjk. Default false. */
    public void setSubstringSpecialTokens(boolean substringSpecialTokens) {
        this.substringSpecialTokens = substringSpecialTokens;
    }

    /**
     * Resets this tokenizer and create tokens from the given string, using
     * "default" as the default index, and using no index information.
     *
     * @return a read-only list of tokens. This list can only be used by this thread
     */
    public List<Token> tokenize(String string) {
        return tokenize(string, new IndexFacts().newSession(Collections.emptySet(), Collections.emptySet()));
    }

    /**
     * Resets this tokenizer and create tokens from the given string, using
     * "default" as the default index
     *
     * @return a read-only list of tokens. This list can only be used by this thread
     */
    public List<Token> tokenize(String string, IndexFacts.Session indexFacts) {
        return tokenize(string, "default", indexFacts);
    }

    /**
     * Resets this tokenizer and create tokens from the given string.
     *
     * @param string the string to tokenize
     * @param defaultIndexName the name of the index to use as default
     * @param indexFacts information about the indexes we will search
     * @return a read-only list of tokens. This list can only be used by this thread
     */
    // To avoid this we need to pass an IndexFacts.session down instead - easily done but not without breaking API's
    public List<Token> tokenize(String string, String defaultIndexName, IndexFacts.Session indexFacts) {
        this.source = string;
        tokens.clear();
        parensToEat = 0;
        Index topLevelIndex = Index.nullIndex;
        Index defaultIndex = indexFacts.getIndex(defaultIndexName);
        if (defaultIndexName != null) {
            topLevelIndex = defaultIndex;
        }
        Index currentIndex = topLevelIndex;
        for (int i = 0; i < source.length(); i++) {
            if (currentIndex.isExact()) {
                i = consumeExact(i, currentIndex); // currentIndex may change after seeing a colon below
                currentIndex = topLevelIndex;
            }
            else {
                i = consumeSpecialToken(i);
            }

            if (i >= source.length()) break;

            int c = source.codePointAt(i);
            if (characterClasses.isLetterOrDigit(c) || (c == '\'' && acceptApostropheAsWordCharacter(currentIndex))) {
                i = consumeWordOrNumber(i, currentIndex);
            } else if (Character.isWhitespace(c)) {
                addToken(SPACE, " ", i, i + 1);
            } else if (c == '"' || c == '\u201C' || c == '\u201D'
                    || c == '\u201E' || c == '\u201F' || c == '\u2039'
                    || c == '\u203A' || c == '\u00AB' || c == '\u00BB'
                    || c == '\u301D' || c == '\u301E' || c == '\u301F'
                    || c == '\uFF02') {
                addToken(QUOTE, "\"", i, i + 1);
            } else if (c == '-' || c == '\uFF0D') {
                addToken(MINUS, "-", i, i + 1);
            } else if (c == '+' || c == '\uFF0B') {
                addToken(PLUS, "+", i, i + 1);
            } else if (c == '.' || c == '\uFF0E') {
                addToken(DOT, ".", i, i + 1);
            } else if (c == ',' || c == '\uFF0C') {
                addToken(COMMA, ",", i, i + 1);
            } else if (c == ':' || c == '\uFF1A') {
                currentIndex = determineCurrentIndex(defaultIndex, indexFacts);
                addToken(COLON, ":", i, i + 1);
            } else if (c == '(' || c == '\uFF08') {
                addToken(LBRACE, "(", i, i + 1);
                parensToEat++;
            } else if (c == ')' || c == '\uFF09') {
                addToken(RBRACE, ")", i, i + 1);
                parensToEat--;
                if (parensToEat < 0) parensToEat = 0;
            } else if (c == '[' || c == '\uFF3B') {
                addToken(LSQUAREBRACKET, "[", i, i + 1);
            } else if (c == ']' || c == '\uFF3D') {
                addToken(RSQUAREBRACKET, "]", i, i + 1);
            } else if (c == ';' || c == '\uFF1B') {
                addToken(SEMICOLON, ";", i, i + 1);
            } else if (c == '>' || c == '\uFF1E') {
                addToken(GREATER, ">", i, i + 1);
            } else if (c == '<' || c == '\uFF1C') {
                addToken(SMALLER, "<", i, i + 1);
            } else if (c == '!' || c == '\uFF01') {
                addToken(EXCLAMATION, "!", i, i + 1);
            } else if (c == '_' || c == '\uFF3F') {
                addToken(UNDERSCORE, "_", i, i + 1);
            } else if (c == '^' || c == '\uFF3E') {
                addToken(HAT, "^", i, i + 1);
            } else if (c == '*' || c == '\uFF0A') {
                addToken(STAR, "*", i, i + 1);
            } else if (c == '$' || c == '\uFF04') {
                addToken(DOLLAR, "$", i, i + 1);
            } else {
                addToken(NOISE, "<NOISE>", i, i + 1);
            }
        }
        addToken(EOF, "<EOF>", source.length(), source.length());
        source = null;
        return tokens;
    }

    private boolean acceptApostropheAsWordCharacter(Index currentIndex) {
        if ( ! (currentIndex.isUriIndex() || currentIndex.isHostIndex())) {
            return true;
        }
        // this is a heuristic to check whether we probably have reached the end of an URL element
        for (int i = tokens.size() - 1; i >= 0; --i) {
            switch (tokens.get(i).kind) {
                case COLON:
                    if (i == indexLastExplicitlyChangedAt) return false;
                    break;
                case SPACE:
                    return true;
                default:
                    // do nothing
            }
        }
        // really not sure whether we should choose false instead, on cause of the guard at
        // the start, but this seems like the conservative choice
        return true;
    }

    private Index determineCurrentIndex(Index defaultIndex, IndexFacts.Session indexFacts) {
        int backtrack = tokens.size();
        int tokencnt = 0;
        for (int i = 1; i <= tokens.size(); i++) {
            backtrack = tokens.size() - i;
            Token lookAt = tokens.get(backtrack);
            if (lookAt.kind != WORD && lookAt.kind != UNDERSCORE && lookAt.kind != NUMBER && lookAt.kind != DOT) {
                // do not use this token
                backtrack++;
                break;
            }
            tokencnt++;
        }
        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < tokencnt; i++) {
            Token useToken = tokens.get(backtrack + i);
            tmp.append(useToken.image);
        }
        String indexName = tmp.toString();
        if (indexName.length() > 0) {
            String canonicIndexName = indexFacts.getCanonicName(indexName);
            Index index = indexFacts.getIndex(canonicIndexName);
            if (! index.isNull()) {
                indexLastExplicitlyChangedAt = tokens.size();
                return index;
            }
        }
        return defaultIndex;
    }

    private int consumeSpecialToken(int start) {
        SpecialTokens.Token token = getSpecialToken(start);
        if (token == null) return start;
        tokens.add(toToken(token, start, source));
        return start + token.token().length();
    }

    private SpecialTokens.Token getSpecialToken(int start) {
        if (specialTokens == null) return null;
        return specialTokens.tokenize(source.substring(start), substringSpecialTokens);
    }

    private int consumeExact(int start,Index index) {
        if (index.getExactTerminator() == null) return consumeHeuristicExact(start);
        return consumeToTerminator(start, index.getExactTerminator());
    }

    private boolean looksLikeExactEnd(int end) {
        int parens = parensToEat;
        boolean wantStar = true;
        boolean wantBang = true;
        boolean eatDigit = false;

        int endLimit = source.length();

        while (end < endLimit) {
            char c = source.charAt(end++);

            if (Character.isWhitespace(c)) {
                // ends in whitespace
                return true;
            }
            // handle digits (after a ! sign)
            if (eatDigit && Character.isDigit(c)) {
                continue;
            }
            eatDigit = false;

            // ! digits or any number of ! signs:
            if (wantBang && c == '!') {
                eatDigit = true;
                while (end < endLimit) {
                    c = source.charAt(end);
                    if (c == '!') {
                        end++;
                        // more than one ! -> do not eat digits
                        eatDigit = false;
                    } else {
                        break;
                    }
                }
                wantBang = false;
                continue;
            }

            // star meaning prefix after a string:
            if (wantStar && (c == '*' || c == '\uFF0A')) {
                wantStar = false;
                continue;
            }

            // parens ending a group:
            if (parens > 0 && c == ')') {
                parens--;
                continue;
            }

            // something else
            return false;
        }
        // end of field
        return true;
    }

    private int consumeHeuristicExact(int start) {
        int curPos = -1;
        int actualStart = -1;
        int starPos = -1;
        int endLimit = source.length();

        boolean suffStar = false;
        boolean isQuoted = false;
        boolean seenSome = false;

        boolean wantStartQuote = true;
        boolean wantEndQuote = false;
        boolean wantStartStar = true;

        // ignore whitespace at start until we something else
        boolean ignWS = true;

        for (curPos = start; curPos < endLimit; curPos++) {
            char c = source.charAt(curPos);

            if (Character.isWhitespace(c)) {
                if (ignWS) continue;
                // ends exact token unless quoted
                if (!wantEndQuote) break;
            }
            ignWS = false;

            if (c == '"') {
                if (wantStartQuote) {
                    // starts actual token
                    wantStartQuote = false;
                    wantEndQuote = true;
                    actualStart = curPos+1;
                } else if (wantEndQuote && looksLikeExactEnd(curPos+1)) {
                    seenSome = true;
                    wantEndQuote = false;
                    isQuoted = true;
                    // ends token
                    break;
                }
                // else: part of exact token
                continue;
            }
            // no processing of non-quotes inside quotes
            if (wantEndQuote) continue;

            if (c == '*' || c == '\uFF0A') {
                if (wantStartStar) {
                    suffStar = true;
                    wantStartStar = false;
                    starPos = curPos;
                    continue;
                }
            }

            if (c == '!' || c == '*' || c == '\uFF0A') {
                // ends token if non-empty
                if (seenSome && looksLikeExactEnd(curPos)) break;
            }

            if (c == ')' && seenSome && looksLikeExactEnd(curPos)) {
                break;
            }
            if (!seenSome) {
                // everything else: something that starts the actual token
                actualStart = curPos;
                seenSome = true;
                wantStartQuote = false;
                wantStartStar = false;
            }
        }

        int end = curPos;

        // handle some ill-formed inputs:

        if (wantEndQuote) {
            // missing end quote: reprocess without quote handling
            isQuoted = false;
            actualStart = -1;
            starPos = -1;
            suffStar = false;
            seenSome = false;
            wantStartStar = true;

            // ignore whitespace at start until we something else
            ignWS = true;

            for (curPos = start; curPos < endLimit; curPos++) {
                char c = source.charAt(curPos);

                if (Character.isWhitespace(c)) {
                    if (ignWS) continue;
                    // ends exact token
                    break;
                }
                ignWS = false;

                if (c == '*' || c == '\uFF0A') {
                    if (wantStartStar) {
                        suffStar = true;
                        wantStartStar = false;
                        starPos = curPos;
                        continue;
                    }
                }

                if (c == '!' || c == '*' || c == '\uFF0A') {
                    // ends token if non-empty
                    if (seenSome) break;
                }

                if (c == ')' && seenSome && parensToEat > 0) {
                    break;
                }
                if (!seenSome) {
                    // everything else: something that starts the actual token
                    actualStart = curPos;
                    seenSome = true;
                    wantStartStar = false;
                }
            }
            end = curPos;
        }

        if (! seenSome) {
            // no token content: may need to include stars or whitespace
            if (suffStar) {
                // use the star as token:
                suffStar = false;
                actualStart = starPos;
            } else {
                // just include all we have (possibly whitespace or an empty string):
                actualStart = start;
            }
        }

        if (suffStar) {
            addToken(STAR, "*", starPos, starPos + 1);
        }
        tokens.add(new Token(WORD, source.substring(actualStart, end), true, new Substring(actualStart, end, source)));

        // skip terminating quote
        if (isQuoted) {
            end++;
        }
        return end;
    }

    private int consumeToTerminator(int start,String terminator) {
        int end = start;
        while (end < source.length()) {
            if (terminatorStartsAt(end,terminator))
                break;
            end++;
        }
        tokens.add(new Token(WORD, source.substring(start, end), true, new Substring(start, end, source)));
        if (end >= source.length())
            return end;
        else
            return end + terminator.length(); // Don't create a token for the terminator
    }

    private boolean terminatorStartsAt(int start,String terminator) {
        int terminatorPosition = 0;
        while ((terminatorPosition + start) < source.length()) {
            if (source.charAt(start+terminatorPosition) != terminator.charAt(terminatorPosition))
                return false;
            terminatorPosition++;
            if (terminatorPosition >= terminator.length())
                return true; // Reached end of terminator
        }
        return false; // Reached end of source before reaching end of terminator
    }

    /** Consumes a word or number <i>and/or possibly</i> a special token starting within this word or number */
    private int consumeWordOrNumber(int start, Index currentIndex) {
        int tokenEnd = start;
        SpecialTokens.Token substringToken = null;
        boolean digitsOnly = true;
        // int underscores = 0;
        // boolean underscoresOnly = true;
        boolean quotesOnly = true;

        while (tokenEnd < source.length()) {
            if (substringSpecialTokens) {
                substringToken = getSpecialToken(tokenEnd);
                if (substringToken != null) break;
            }

            int c = source.codePointAt(tokenEnd);

            if (characterClasses.isLetter(c)) {
                digitsOnly = false;
                // if (c != '_') {
                //     if (underscores > 3) {
                //         break;
                //     } else {
                //         underscores = 0;
                //     }
                //     underscoresOnly = false;
                // } else {
                //     underscores += 1;
                // }
                quotesOnly = false;
            } else if (characterClasses.isLatinDigit(c)) {
                // Yes, do nothing as long as the underscore logic
                // is deactivated.
                // underscoresOnly = false;
                quotesOnly = false;
            } else if (c == '\'') {
                if ( ! acceptApostropheAsWordCharacter(currentIndex)) {
                    break;
                }
                // Otherwise consume apostrophes...
                digitsOnly = false;
            } else {
                break;
            }
            tokenEnd += Character.charCount(c);
        }
        // if (underscores > 3 && !underscoresOnly) {
        //     tokenEnd -= underscores;
        // }
        if (tokenEnd>start) {
            // if (underscoresOnly) {
            //     addToken(NOISE, source.substring(start, tokenEnd), start, tokenEnd);
            // } else
            if (quotesOnly) {
                addToken(NOISE, source.substring(start, tokenEnd), start, tokenEnd);
            } else {
                addToken(digitsOnly ? NUMBER : WORD, source.substring(start, tokenEnd), start, tokenEnd);
            }
        }

        if (substringToken == null)
            return --tokenEnd;
        // TODO: test the logic around tokenEnd with friends
        addToken(toToken(substringToken, tokenEnd, source));
        return --tokenEnd + substringToken.token().length();
    }

    private void addToken(Token.Kind kind, String word, int start, int end) {
        addToken(new Token(kind, word, false, new Substring(start, end, source)));
    }

    private void addToken(Token token) {
        tokens.add(token);
    }

    public Token toToken(SpecialTokens.Token specialToken, int start, String rawSource) {
        return new Token(Token.Kind.WORD,
                         specialToken.replacement(),
                         true,
                         new Substring(start, start + specialToken.token().length(), rawSource)); // XXX: Unsafe?
    }

}
