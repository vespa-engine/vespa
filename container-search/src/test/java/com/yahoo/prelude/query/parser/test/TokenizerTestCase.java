// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;

import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.parser.SpecialTokenRegistry;
import com.yahoo.prelude.query.parser.SpecialTokens;
import com.yahoo.prelude.query.parser.Token;
import com.yahoo.prelude.query.parser.Tokenizer;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.yahoo.prelude.query.parser.Token.Kind.COLON;
import static com.yahoo.prelude.query.parser.Token.Kind.COMMA;
import static com.yahoo.prelude.query.parser.Token.Kind.DOT;
import static com.yahoo.prelude.query.parser.Token.Kind.EXCLAMATION;
import static com.yahoo.prelude.query.parser.Token.Kind.LBRACE;
import static com.yahoo.prelude.query.parser.Token.Kind.NOISE;
import static com.yahoo.prelude.query.parser.Token.Kind.NUMBER;
import static com.yahoo.prelude.query.parser.Token.Kind.PLUS;
import static com.yahoo.prelude.query.parser.Token.Kind.RBRACE;
import static com.yahoo.prelude.query.parser.Token.Kind.SPACE;
import static com.yahoo.prelude.query.parser.Token.Kind.STAR;
import static com.yahoo.prelude.query.parser.Token.Kind.UNDERSCORE;
import static com.yahoo.prelude.query.parser.Token.Kind.WORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the tokenizer
 *
 * @author  bratseth
 */
public class TokenizerTestCase {

    private SpecialTokenRegistry defaultRegistry = new SpecialTokenRegistry("file:src/test/java/com/yahoo/prelude/query/parser/test/replacingtokens.cfg");

    @Test
    public void testPlainTokenization() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(createSpecialTokens());
        List<?> tokens = tokenizer.tokenize("drive (to hwy88, 88) +or language:en ugcapi_1");

        assertEquals(new Token(WORD, "drive"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(LBRACE, "("), tokens.get(2));
        assertEquals(new Token(WORD, "to"), tokens.get(3));
        assertEquals(new Token(SPACE, " "), tokens.get(4));
        assertEquals(new Token(WORD, "hwy88"), tokens.get(5));
        assertEquals(new Token(COMMA, ","), tokens.get(6));
        assertEquals(new Token(SPACE, " "), tokens.get(7));
        assertEquals(new Token(NUMBER, "88"), tokens.get(8));
        assertEquals(new Token(RBRACE, ")"), tokens.get(9));
        assertEquals(new Token(SPACE, " "), tokens.get(10));
        assertEquals(new Token(PLUS, "+"), tokens.get(11));
        assertEquals(new Token(WORD, "or"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "language"), tokens.get(14));
        assertEquals(new Token(COLON, ":"), tokens.get(15));
        assertEquals(new Token(WORD, "en"), tokens.get(16));
        assertEquals(new Token(SPACE, " "), tokens.get(17));
        assertEquals(new Token(WORD, "ugcapi"), tokens.get(18));
        assertEquals(new Token(UNDERSCORE, "_"), tokens.get(19));
        assertEquals(new Token(NUMBER, "1"), tokens.get(20));
    }

    @Test
    public void testOutsideBMPCodepoints() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());
        List<?> tokens = tokenizer.tokenize("\ud841\udd47");
        assertEquals(new Token(WORD, "\ud841\udd47"), tokens.get(0));
    }

    @Test
    public void testOneSpecialToken() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(createSpecialTokens());
        List<?> tokens = tokenizer.tokenize("c++ lovers, please apply");

        assertEquals(new Token(WORD, "c++"), tokens.get(0));
    }

    @Test
    public void testSpecialTokenCombination() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(createSpecialTokens());
        List<?> tokens = tokenizer.tokenize("c#, c++ or .net know, not tcp/ip");

        assertEquals(new Token(WORD, "c#"), tokens.get(0));
        assertEquals(new Token(COMMA, ","), tokens.get(1));
        assertEquals(new Token(SPACE, " "), tokens.get(2));
        assertEquals(new Token(WORD, "c++"), tokens.get(3));
        assertEquals(new Token(SPACE, " "), tokens.get(4));
        assertEquals(new Token(WORD, "or"), tokens.get(5));
        assertEquals(new Token(SPACE, " "), tokens.get(6));
        assertEquals(new Token(WORD, ".net"), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "know"), tokens.get(9));
        assertEquals(new Token(COMMA, ","), tokens.get(10));
        assertEquals(new Token(SPACE, " "), tokens.get(11));
        assertEquals(new Token(WORD, "not"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "tcp/ip"), tokens.get(14));
    }

    /**
     * In cjk languages, special tokens must be recognized as substrings of strings not
     * separated by space, as special token recognition happens before tokenization
     */
    @Test
    public void testSpecialTokenCJK() {
        assertEquals("Special tokens configured", 6, defaultRegistry.getSpecialTokens("default").size());
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());
        tokenizer.setSubstringSpecialTokens(true);
        tokenizer.setSpecialTokens(defaultRegistry.getSpecialTokens("default"));

        List<?> tokens = tokenizer.tokenize("fooc#bar,c++with spacebarknowknowknow,knowknownot know");
        assertEquals(new Token(WORD, "foo"), tokens.get(0));
        assertEquals(new Token(WORD, "c#"), tokens.get(1));
        assertEquals(new Token(WORD, "bar"), tokens.get(2));
        assertEquals(new Token(COMMA, ","), tokens.get(3));
        assertEquals(new Token(WORD, "cpp"), tokens.get(4));
        assertEquals(new Token(WORD, "with-space"), tokens.get(5));
        assertEquals(new Token(WORD, "bar"), tokens.get(6));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(7));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(8));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(9));
        assertEquals(new Token(COMMA, ","), tokens.get(10));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(11));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(12));
        assertEquals(new Token(WORD, "not"), tokens.get(13));
        assertEquals(new Token(SPACE, " "), tokens.get(14));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(15));
    }

    @Test
    public void testSpecialTokenCaseInsensitive() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(createSpecialTokens());
        List<?> tokens = tokenizer.tokenize("The AS/400 is great");

        assertEquals(new Token(WORD, "The"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "as/400"), tokens.get(2));
        assertEquals(new Token(SPACE, " "), tokens.get(3));
        assertEquals(new Token(WORD, "is"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(WORD, "great"), tokens.get(6));
    }

    @Test
    public void testSpecialTokenNonMatch() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(createSpecialTokens());
        List<?> tokens = tokenizer.tokenize("c++ c+ aS/400 i/o .net i/ooo ap.net");

        assertEquals(new Token(WORD, "c++"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "c+"), tokens.get(2));
        assertEquals(new Token(SPACE, " "), tokens.get(3));
        assertEquals(new Token(WORD, "as/400"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(WORD, "i/o"), tokens.get(6));
        assertEquals(new Token(SPACE, " "), tokens.get(7));
        assertEquals(new Token(WORD, ".net"), tokens.get(8));
        assertEquals(new Token(SPACE, " "), tokens.get(9));
        assertEquals(new Token(WORD, "i"), tokens.get(10));
        assertEquals(new Token(NOISE, "<NOISE>"), tokens.get(11));
        assertEquals(new Token(WORD, "ooo"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "ap"), tokens.get(14));
        assertEquals(new Token(WORD, ".net"), tokens.get(15));
    }

    @Test
    public void testSpecialTokenConfigurationDefault() {
        String tokenFile = "file:src/test/java/com/yahoo/prelude/query/parser/test/specialtokens.cfg";

        SpecialTokenRegistry r = new SpecialTokenRegistry(tokenFile);
        assertEquals("Special tokens configured", 6,
                r.getSpecialTokens("default").size());
        assertEquals("Special tokens configured", 4,
                r.getSpecialTokens("other").size());

        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(
                r.getSpecialTokens("default"));
        List<?> tokens = tokenizer.tokenize(
                "with space, c++ or .... know, not b.s.d.");

        assertEquals(new Token(WORD, "with space"), tokens.get(0));
        assertEquals(new Token(COMMA, ","), tokens.get(1));
        assertEquals(new Token(SPACE, " "), tokens.get(2));
        assertEquals(new Token(WORD, "c++"), tokens.get(3));
        assertEquals(new Token(SPACE, " "), tokens.get(4));
        assertEquals(new Token(WORD, "or"), tokens.get(5));
        assertEquals(new Token(SPACE, " "), tokens.get(6));
        assertEquals(new Token(WORD, "...."), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "know"), tokens.get(9));
        assertEquals(new Token(COMMA, ","), tokens.get(10));
        assertEquals(new Token(SPACE, " "), tokens.get(11));
        assertEquals(new Token(WORD, "not"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "b.s.d."), tokens.get(14));
    }

    @Test
    public void testSpecialTokenConfigurationOther() {
        String tokenFile = "file:src/test/java/com/yahoo/prelude/query/parser/test/specialtokens.cfg";

        SpecialTokenRegistry r = new SpecialTokenRegistry(tokenFile);
        assertEquals("Special tokens configured", 6,
                r.getSpecialTokens("default").size());
        assertEquals("Special tokens configured", 4,
                r.getSpecialTokens("other").size());

        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(
                r.getSpecialTokens("other"));
        List<?> tokens = tokenizer.tokenize(
                "with space,!!!*** [huh] or ------ " + "know, &&&%%% b.s.d.");

        assertEquals(new Token(WORD, "with"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "space"), tokens.get(2));
        assertEquals(new Token(COMMA, ","), tokens.get(3));
        assertEquals(new Token(WORD, "!!!***"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(WORD, "[huh]"), tokens.get(6));
        assertEquals(new Token(SPACE, " "), tokens.get(7));
        assertEquals(new Token(WORD, "or"), tokens.get(8));
        assertEquals(new Token(SPACE, " "), tokens.get(9));
        assertEquals(new Token(WORD, "------"), tokens.get(10));
        assertEquals(new Token(SPACE, " "), tokens.get(11));
        assertEquals(new Token(WORD, "know"), tokens.get(12));
        assertEquals(new Token(COMMA, ","), tokens.get(13));
        assertEquals(new Token(SPACE, " "), tokens.get(14));
        assertEquals(new Token(WORD, "&&&%%%"), tokens.get(15));
        assertEquals(new Token(SPACE, " "), tokens.get(16));
        assertEquals(new Token(WORD, "b"), tokens.get(17));
        assertEquals(new Token(DOT, "."), tokens.get(18));
        assertEquals(new Token(WORD, "s"), tokens.get(19));
        assertEquals(new Token(DOT, "."), tokens.get(20));
        assertEquals(new Token(WORD, "d"), tokens.get(21));
        assertEquals(new Token(DOT, "."), tokens.get(22));

        assertTrue(((Token) tokens.get(10)).isSpecial());
    }

    @Test
    public void testSpecialTokenConfigurationMissing() {
        String tokenFile = "file:source/bogus/specialtokens.cfg";

        SpecialTokenRegistry r = new SpecialTokenRegistry(tokenFile);

        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(r.getSpecialTokens("other"));
        List<?> tokens = tokenizer.tokenize("c++");

        assertEquals(new Token(WORD, "c"), tokens.get(0));
        assertEquals(new Token(PLUS, "+"), tokens.get(1));
        assertEquals(new Token(PLUS, "+"), tokens.get(2));
    }

    @Test
    public void testTokenReplacing() {
        assertEquals("Special tokens configured", 6, defaultRegistry.getSpecialTokens("default").size());
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());
        tokenizer.setSpecialTokens(defaultRegistry.getSpecialTokens("default"));

        List<?> tokens = tokenizer.tokenize("with space, c++ or .... know, not b.s.d.");
        assertEquals(new Token(WORD, "with-space"), tokens.get(0));
        assertEquals(new Token(COMMA, ","), tokens.get(1));
        assertEquals(new Token(SPACE, " "), tokens.get(2));
        assertEquals(new Token(WORD, "cpp"), tokens.get(3));
        assertEquals(new Token(SPACE, " "), tokens.get(4));
        assertEquals(new Token(WORD, "or"), tokens.get(5));
        assertEquals(new Token(SPACE, " "), tokens.get(6));
        assertEquals(new Token(WORD, "...."), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "knuwww"), tokens.get(9));
        assertEquals(new Token(COMMA, ","), tokens.get(10));
        assertEquals(new Token(SPACE, " "), tokens.get(11));
        assertEquals(new Token(WORD, "not"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "b.s.d."), tokens.get(14));

        assertTrue(((Token) tokens.get(9)).isSpecial());
        assertFalse(((Token) tokens.get(12)).isSpecial());
    }

    @Test
    public void testExactMatchTokenization() {
        Index index1=new Index("testexact1");
        index1.setExact(true,null);
        Index index2=new Index("testexact2");
        index2.setExact(true,"()/aa*::*&");
        IndexFacts facts = new IndexFacts();
        facts.addIndex("testsd",index1);
        facts.addIndex("testsd",index2);
        IndexFacts.Session session = facts.newSession(Collections.emptySet(), Collections.emptySet());
        Tokenizer tokenizer=new Tokenizer(new SimpleLinguistics());
        List<?> tokens=tokenizer.tokenize("normal a:b (normal testexact1:/,%#%&+-+ ) testexact2:ho_/&%&/()/aa*::*& b:c", "default", session);
        // tokenizer.print();
        assertEquals(new Token(WORD, "normal"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "a"), tokens.get(2));
        assertEquals(new Token(COLON, ":"), tokens.get(3));
        assertEquals(new Token(WORD, "b"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(LBRACE, "("), tokens.get(6));
        assertEquals(new Token(WORD, "normal"), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(9));
        assertEquals(new Token(COLON, ":"), tokens.get(10));
        assertEquals(new Token(WORD, "/,%#%&+-+"), tokens.get(11));
        assertEquals(new Token(SPACE, " "), tokens.get(12));
        assertEquals(new Token(RBRACE, ")"), tokens.get(13));
        assertEquals(new Token(SPACE, " "), tokens.get(14));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(15));
        assertEquals(new Token(COLON, ":"), tokens.get(16));
        assertEquals(new Token(WORD, "ho_/&%&/"), tokens.get(17));
        assertEquals(new Token(SPACE, " "), tokens.get(18));
        assertEquals(new Token(WORD, "b"), tokens.get(19));
        assertEquals(new Token(COLON, ":"), tokens.get(20));
        assertEquals(new Token(WORD, "c"), tokens.get(21));
        assertTrue(((Token) tokens.get(11)).isSpecial());
        assertFalse(((Token) tokens.get(15)).isSpecial());
        assertTrue(((Token) tokens.get(17)).isSpecial());
    }

    @Test
    public void testExactMatchTokenizationTerminatorTerminatesQuery() {
        Index index1=new Index("testexact1");
        index1.setExact(true,null);
        Index index2=new Index("testexact2");
        index2.setExact(true,"()/aa*::*&");
        IndexFacts facts = new IndexFacts();
        facts.addIndex("testsd",index1);
        facts.addIndex("testsd",index2);
        Tokenizer tokenizer=new Tokenizer(new SimpleLinguistics());
        IndexFacts.Session session = facts.newSession(Collections.emptySet(), Collections.emptySet());
        List<?> tokens=tokenizer.tokenize("normal a:b (normal testexact1:/,%#%&+-+ ) testexact2:ho_/&%&/()/aa*::*&", session);
        assertEquals(new Token(WORD, "normal"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "a"), tokens.get(2));
        assertEquals(new Token(COLON, ":"), tokens.get(3));
        assertEquals(new Token(WORD, "b"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(LBRACE, "("), tokens.get(6));
        assertEquals(new Token(WORD, "normal"), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(9));
        assertEquals(new Token(COLON, ":"), tokens.get(10));
        assertEquals(new Token(WORD, "/,%#%&+-+"), tokens.get(11));
        assertEquals(new Token(SPACE, " "), tokens.get(12));
        assertEquals(new Token(RBRACE, ")"), tokens.get(13));
        assertEquals(new Token(SPACE, " "), tokens.get(14));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(15));
        assertEquals(new Token(COLON, ":"), tokens.get(16));
        assertEquals(new Token(WORD, "ho_/&%&/"), tokens.get(17));
        assertTrue(((Token) tokens.get(17)).isSpecial());
    }

    @Test
    public void testExactMatchTokenizationWithTerminatorTerminatedByEndOfString() {
        Index index1=new Index("testexact1");
        index1.setExact(true,null);
        Index index2=new Index("testexact2");
        index2.setExact(true,"()/aa*::*&");
        IndexFacts facts = new IndexFacts();
        facts.addIndex("testsd",index1);
        facts.addIndex("testsd",index2);
        Tokenizer tokenizer=new Tokenizer(new SimpleLinguistics());
        IndexFacts.Session session = facts.newSession(Collections.emptySet(), Collections.emptySet());
        List<?> tokens=tokenizer.tokenize("normal a:b (normal testexact1:/,%#%&+-+ ) testexact2:ho_/&%&/()/aa*::*", session);
        assertEquals(new Token(WORD, "normal"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "a"), tokens.get(2));
        assertEquals(new Token(COLON, ":"), tokens.get(3));
        assertEquals(new Token(WORD, "b"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(LBRACE, "("), tokens.get(6));
        assertEquals(new Token(WORD, "normal"), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(9));
        assertEquals(new Token(COLON, ":"), tokens.get(10));
        assertEquals(new Token(WORD, "/,%#%&+-+"), tokens.get(11));
        assertEquals(new Token(SPACE, " "), tokens.get(12));
        assertEquals(new Token(RBRACE, ")"), tokens.get(13));
        assertEquals(new Token(SPACE, " "), tokens.get(14));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(15));
        assertEquals(new Token(COLON, ":"), tokens.get(16));
        assertEquals(new Token(WORD, "ho_/&%&/()/aa*::*"), tokens.get(17));
        assertTrue(((Token) tokens.get(17)).isSpecial());
    }

    @Test
    public void testExactMatchTokenizationEndsByColon() {
        Index index1=new Index("testexact1");
        index1.setExact(true,null);
        Index index2=new Index("testexact2");
        index2.setExact(true,"()/aa*::*&");
        IndexFacts facts = new IndexFacts();
        facts.addIndex("testsd",index1);
        facts.addIndex("testsd",index2);
        Tokenizer tokenizer=new Tokenizer(new SimpleLinguistics());
        IndexFacts.Session session = facts.newSession(Collections.emptySet(), Collections.emptySet());
        List<?> tokens=tokenizer.tokenize("normal a:b (normal testexact1:!/%#%&+-+ ) testexact2:ho_/&%&/()/aa*::*&b:", session);
        assertEquals(new Token(WORD, "normal"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "a"), tokens.get(2));
        assertEquals(new Token(COLON, ":"), tokens.get(3));
        assertEquals(new Token(WORD, "b"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(LBRACE, "("), tokens.get(6));
        assertEquals(new Token(WORD, "normal"), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(9));
        assertEquals(new Token(COLON, ":"), tokens.get(10));
        assertEquals(new Token(WORD, "!/%#%&+-+"), tokens.get(11));
        assertEquals(new Token(SPACE, " "), tokens.get(12));
        assertEquals(new Token(RBRACE, ")"), tokens.get(13));
        assertEquals(new Token(SPACE, " "), tokens.get(14));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(15));
        assertEquals(new Token(COLON, ":"), tokens.get(16));
        assertEquals(new Token(WORD, "ho_/&%&/"), tokens.get(17));
        assertEquals(new Token(WORD, "b"), tokens.get(18));
        assertEquals(new Token(COLON, ":"), tokens.get(19));
    }

    @Test
    public void testExactMatchHeuristics() {
        Index index1=new Index("testexact1");
        index1.setExact(true, null);
        Index index2=new Index("testexact2");
        index2.setExact(true, "()/aa*::*&");
        IndexFacts indexFacts = new IndexFacts();
        indexFacts.addIndex("testsd", index1);
        indexFacts.addIndex("testsd", index2);
        IndexFacts.Session facts = indexFacts.newSession(Collections.emptySet(), Collections.emptySet());

        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());
        List<?> tokens = tokenizer.tokenize("normal a:b (normal testexact1:foo) testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(WORD, "a"), tokens.get(2));
        assertEquals(new Token(COLON, ":"), tokens.get(3));
        assertEquals(new Token(WORD, "b"), tokens.get(4));
        assertEquals(new Token(SPACE, " "), tokens.get(5));
        assertEquals(new Token(LBRACE, "("), tokens.get(6));
        assertEquals(new Token(WORD, "normal"), tokens.get(7));
        assertEquals(new Token(SPACE, " "), tokens.get(8));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(9));
        assertEquals(new Token(COLON, ":"), tokens.get(10));
        assertEquals(new Token(WORD, "foo"), tokens.get(11));
        assertEquals(new Token(RBRACE, ")"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(14));
        assertEquals(new Token(COLON, ":"), tokens.get(15));
        assertEquals(new Token(WORD, "bar"), tokens.get(16));

        tokens = tokenizer.tokenize("testexact1:a*teens", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "a*teens"),    tokens.get(2));

        tokens = tokenizer.tokenize("testexact1:foo\"bar", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo\"bar"),   tokens.get(2));

        tokens = tokenizer.tokenize("testexact1:foo!bar", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo!bar"),    tokens.get(2));

        tokens = tokenizer.tokenize("testexact1:foo! ", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(3));
        assertEquals(new Token(SPACE, " "),         tokens.get(4));

        tokens = tokenizer.tokenize("testexact1:foo!! ", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(3));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(4));
        assertEquals(new Token(SPACE, " "),         tokens.get(5));

        tokens = tokenizer.tokenize("testexact1:foo!100 ", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(3));
        assertEquals(new Token(NUMBER, "100"),      tokens.get(4));
        assertEquals(new Token(SPACE, " "),         tokens.get(5));

        tokens = tokenizer.tokenize("testexact1:foo*!100 ", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));
        assertEquals(new Token(STAR, "*"),          tokens.get(3));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(4));
        assertEquals(new Token(NUMBER, "100"),      tokens.get(5));
        assertEquals(new Token(SPACE, " "),         tokens.get(6));

        tokens = tokenizer.tokenize("testexact1: *\"foo bar\"*!100 ", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(STAR, "*"),          tokens.get(2));
        assertEquals(new Token(WORD, "foo bar"),        tokens.get(3));
        assertEquals(new Token(STAR, "*"),          tokens.get(4));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(5));
        assertEquals(new Token(NUMBER, "100"),      tokens.get(6));
        assertEquals(new Token(SPACE, " "),         tokens.get(7));

        tokens = tokenizer.tokenize("testexact1: *\"foo bar\"*!100", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(STAR, "*"),          tokens.get(2));
        assertEquals(new Token(WORD, "foo bar"),        tokens.get(3));
        assertEquals(new Token(STAR, "*"),          tokens.get(4));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(5));
        assertEquals(new Token(NUMBER, "100"),      tokens.get(6));

        tokens = tokenizer.tokenize("testexact1: *foobar*!100", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(STAR, "*"),          tokens.get(2));
        assertEquals(new Token(WORD, "foobar"),     tokens.get(3));
        assertEquals(new Token(STAR, "*"),          tokens.get(4));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(5));
        assertEquals(new Token(NUMBER, "100"),      tokens.get(6));

        tokens = tokenizer.tokenize("testexact1: *foobar*!100!", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(STAR, "*"),          tokens.get(2));
        assertEquals(new Token(WORD, "foobar*!100"),tokens.get(3));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(4));

        tokens = tokenizer.tokenize("testexact1:foo(bar)", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo(bar)"),    tokens.get(2));

        tokens = tokenizer.tokenize("testexact1:\"foo\"", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));

        tokens = tokenizer.tokenize("testexact1: foo", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));

        tokens = tokenizer.tokenize("testexact1: \"foo\"", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),        tokens.get(2));

        tokens = tokenizer.tokenize("testexact1: \"foo\"", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "foo"),    tokens.get(2));

        tokens = tokenizer.tokenize("testexact1:vespa testexact2:resolved", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "vespa"),      tokens.get(2));
        assertEquals(new Token(SPACE, " "),         tokens.get(3));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(4));
        assertEquals(new Token(COLON, ":"),         tokens.get(5));
        assertEquals(new Token(WORD, "resolved"),   tokens.get(6));

        tokens = tokenizer.tokenize("testexact1:\"news search\" testexact2:resolved", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "news search"),tokens.get(2));
        assertEquals(new Token(SPACE, " "),         tokens.get(3));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(4));
        assertEquals(new Token(COLON, ":"),         tokens.get(5));
        assertEquals(new Token(WORD, "resolved"),   tokens.get(6));

        tokens = tokenizer.tokenize("(testexact1:\"news search\" testexact1:vespa)", facts);
        assertEquals(new Token(LBRACE, "("),        tokens.get(0));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(1));
        assertEquals(new Token(COLON, ":"),         tokens.get(2));
        assertEquals(new Token(WORD, "news search"),tokens.get(3));
        assertEquals(new Token(SPACE, " "),         tokens.get(4));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(5));
        assertEquals(new Token(COLON, ":"),         tokens.get(6));
        assertEquals(new Token(WORD, "vespa"),      tokens.get(7));
        assertEquals(new Token(RBRACE, ")"),        tokens.get(8));

        tokens = tokenizer.tokenize("testexact1:news*", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "news"),       tokens.get(2));
        assertEquals(new Token(STAR, "*"),          tokens.get(3));

        tokens = tokenizer.tokenize("testexact1:\"news\"*", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "news"),       tokens.get(2));
        assertEquals(new Token(STAR, "*"),          tokens.get(3));

        tokens = tokenizer.tokenize("testexact1:\"news search\"!200", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "news search"),tokens.get(2));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(3));
        assertEquals(new Token(NUMBER, "200"),      tokens.get(4));

        tokens = tokenizer.tokenize("testexact1:vespa!200", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(WORD, "vespa"),      tokens.get(2));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(3));
        assertEquals(new Token(NUMBER, "200"),      tokens.get(4));

        tokens = tokenizer.tokenize("testexact1:*\"news\"*", facts);
        assertEquals(new Token(WORD, "testexact1"), tokens.get(0));
        assertEquals(new Token(COLON, ":"),         tokens.get(1));
        assertEquals(new Token(STAR, "*"),          tokens.get(2));
        assertEquals(new Token(WORD, "news"),       tokens.get(3));
        assertEquals(new Token(STAR, "*"),          tokens.get(4));

        tokens = tokenizer.tokenize("normal(testexact1:foo) testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"),     tokens.get(0));
        assertEquals(new Token(LBRACE, "("),        tokens.get(1));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(2));
        assertEquals(new Token(COLON, ":"),         tokens.get(3));
        assertEquals(new Token(WORD, "foo"),        tokens.get(4));
        assertEquals(new Token(RBRACE, ")"),        tokens.get(5));
        assertEquals(new Token(SPACE, " "),         tokens.get(6));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(7));
        assertEquals(new Token(COLON, ":"),         tokens.get(8));
        assertEquals(new Token(WORD, "bar"),        tokens.get(9));

        tokens = tokenizer.tokenize("normal testexact1:(foo testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"),     tokens.get(0));
        assertEquals(new Token(SPACE, " "),         tokens.get(1));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(2));
        assertEquals(new Token(COLON, ":"),         tokens.get(3));
        assertEquals(new Token(WORD, "(foo"),       tokens.get(4));
        assertEquals(new Token(SPACE, " "),         tokens.get(5));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(6));
        assertEquals(new Token(COLON, ":"),         tokens.get(7));
        assertEquals(new Token(WORD, "bar"),        tokens.get(8));

        tokens = tokenizer.tokenize("normal testexact1:foo! testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"),     tokens.get(0));
        assertEquals(new Token(SPACE, " "),         tokens.get(1));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(2));
        assertEquals(new Token(COLON, ":"),         tokens.get(3));
        assertEquals(new Token(WORD, "foo"),        tokens.get(4));
        assertEquals(new Token(EXCLAMATION, "!"),   tokens.get(5));
        assertEquals(new Token(SPACE, " "),         tokens.get(6));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(7));
        assertEquals(new Token(COLON, ":"),         tokens.get(8));
        assertEquals(new Token(WORD, "bar"),        tokens.get(9));

        tokens = tokenizer.tokenize("normal testexact1:foo* testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"),     tokens.get(0));
        assertEquals(new Token(SPACE, " "),         tokens.get(1));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(2));
        assertEquals(new Token(COLON, ":"),         tokens.get(3));
        assertEquals(new Token(WORD, "foo"),        tokens.get(4));
        assertEquals(new Token(STAR, "*"),          tokens.get(5));
        assertEquals(new Token(SPACE, " "),         tokens.get(6));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(7));
        assertEquals(new Token(COLON, ":"),         tokens.get(8));
        assertEquals(new Token(WORD, "bar"),        tokens.get(9));

        tokens = tokenizer.tokenize("normal testexact1: foo* testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"),     tokens.get(0));
        assertEquals(new Token(SPACE, " "),         tokens.get(1));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(2));
        assertEquals(new Token(COLON, ":"),         tokens.get(3));
        assertEquals(new Token(WORD, "foo"),        tokens.get(4));
        assertEquals(new Token(STAR, "*"),          tokens.get(5));
        assertEquals(new Token(SPACE, " "),         tokens.get(6));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(7));
        assertEquals(new Token(COLON, ":"),         tokens.get(8));
        assertEquals(new Token(WORD, "bar"),        tokens.get(9));

        tokens = tokenizer.tokenize("normal testexact1:\" foo\"* testexact2:bar", facts);
        assertEquals(new Token(WORD, "normal"),     tokens.get(0));
        assertEquals(new Token(SPACE, " "),         tokens.get(1));
        assertEquals(new Token(WORD, "testexact1"), tokens.get(2));
        assertEquals(new Token(COLON, ":"),         tokens.get(3));
        assertEquals(new Token(WORD, " foo"),        tokens.get(4));
        assertEquals(new Token(STAR, "*"),          tokens.get(5));
        assertEquals(new Token(SPACE, " "),         tokens.get(6));
        assertEquals(new Token(WORD, "testexact2"), tokens.get(7));
        assertEquals(new Token(COLON, ":"),         tokens.get(8));
        assertEquals(new Token(WORD, "bar"),        tokens.get(9));
    }

    @Test
    public void testSingleQuoteAsWordCharacter() {
        Tokenizer tokenizer = new Tokenizer(new SimpleLinguistics());

        tokenizer.setSpecialTokens(createSpecialTokens());
        List<?> tokens = tokenizer.tokenize("drive (to hwy88, 88) +or language:en nalle:a'a ugcapi_1 'a' 'a a'");

        assertEquals(new Token(WORD, "drive"), tokens.get(0));
        assertEquals(new Token(SPACE, " "), tokens.get(1));
        assertEquals(new Token(LBRACE, "("), tokens.get(2));
        assertEquals(new Token(WORD, "to"), tokens.get(3));
        assertEquals(new Token(SPACE, " "), tokens.get(4));
        assertEquals(new Token(WORD, "hwy88"), tokens.get(5));
        assertEquals(new Token(COMMA, ","), tokens.get(6));
        assertEquals(new Token(SPACE, " "), tokens.get(7));
        assertEquals(new Token(NUMBER, "88"), tokens.get(8));
        assertEquals(new Token(RBRACE, ")"), tokens.get(9));
        assertEquals(new Token(SPACE, " "), tokens.get(10));
        assertEquals(new Token(PLUS, "+"), tokens.get(11));
        assertEquals(new Token(WORD, "or"), tokens.get(12));
        assertEquals(new Token(SPACE, " "), tokens.get(13));
        assertEquals(new Token(WORD, "language"), tokens.get(14));
        assertEquals(new Token(COLON, ":"), tokens.get(15));
        assertEquals(new Token(WORD, "en"), tokens.get(16));
        assertEquals(new Token(SPACE, " "), tokens.get(17));
        assertEquals(new Token(WORD, "nalle"), tokens.get(18));
        assertEquals(new Token(COLON, ":"), tokens.get(19));
        assertEquals(new Token(WORD, "a'a"), tokens.get(20));
        assertEquals(new Token(SPACE, " "), tokens.get(21));
        assertEquals(new Token(WORD, "ugcapi"), tokens.get(22));
        assertEquals(new Token(UNDERSCORE, "_"), tokens.get(23));
        assertEquals(new Token(NUMBER, "1"), tokens.get(24));
        assertEquals(new Token(SPACE, " "), tokens.get(25));
        assertEquals(new Token(WORD, "'a'"), tokens.get(26));
        assertEquals(new Token(SPACE, " "), tokens.get(27));
        assertEquals(new Token(WORD, "'a"), tokens.get(28));
        assertEquals(new Token(SPACE, " "), tokens.get(29));
        assertEquals(new Token(WORD, "a'"), tokens.get(30));
    }

    private SpecialTokens createSpecialTokens() {
        SpecialTokens tokens = new SpecialTokens("default");

        tokens.addSpecialToken("c+", null);
        tokens.addSpecialToken("c++", null);
        tokens.addSpecialToken(".net", null);
        tokens.addSpecialToken("tcp/ip", null);
        tokens.addSpecialToken("i/o", null);
        tokens.addSpecialToken("c#", null);
        tokens.addSpecialToken("AS/400", null);
        return tokens;
    }

}
