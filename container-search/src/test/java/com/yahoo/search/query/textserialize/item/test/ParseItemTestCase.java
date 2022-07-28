// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.ONearItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.TrueItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.textserialize.item.ItemContext;
import com.yahoo.search.query.textserialize.item.ItemFormHandler;
import com.yahoo.search.query.textserialize.parser.ParseException;
import com.yahoo.search.query.textserialize.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Tony Vaagenes
 */
public class ParseItemTestCase {

    public static Object parse(String input) throws ParseException {
        ItemContext context = new ItemContext();
        Object result = new Parser(new StringReader(input.replace("'", "\"")), new ItemFormHandler(), context).start();
        context.connectItems();
        return result;
    }

    @Test
    void parse_and() throws ParseException {
        assertTrue(parse("(AND)") instanceof AndItem);
    }

    @Test
    void parse_and_with_children() throws ParseException {
        AndItem andItem = (AndItem) parse("(AND (WORD 'first') (WORD 'second'))");

        assertEquals(2, andItem.getItemCount());
        assertEquals("first", getWord(andItem.getItem(0)));
    }

    @Test
    void parse_or() throws ParseException {
        assertTrue(parse("(OR)") instanceof OrItem);
    }

    @Test
    void parse_and_not_rest() throws ParseException {
        assertTrue(parse("(AND-NOT-REST)") instanceof NotItem);
    }

    @Test
    void parse_and_not_rest_with_children() throws ParseException {
        NotItem notItem = (NotItem) parse("(AND-NOT-REST (WORD 'positive') (WORD 'negative'))");
        assertEquals("positive", getWord(notItem.getPositiveItem()));
        assertEquals("negative", getWord(notItem.getItem(1)));
    }

    @Test
    void parse_and_not_rest_with_only_negated_children() throws ParseException {
        NotItem notItem = (NotItem) parse("(AND-NOT-REST null (WORD 'negated-item'))");
        assertTrue(notItem.getPositiveItem() instanceof TrueItem);
        assertTrue(notItem.getItem(1) instanceof WordItem);
    }

    @Test
    void parse_rank() throws ParseException {
        assertTrue(parse("(RANK (WORD 'first'))") instanceof RankItem);
    }

    @Test
    void parse_word() throws ParseException {
        WordItem wordItem = (WordItem) parse("(WORD 'text')");
        assertEquals("text", wordItem.getWord());
    }

    @Test
    void fail_when_word_given_multiple_strings() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            parse("(WORD 'one' 'invalid')");
        });
    }

    @Test
    void fail_when_word_given_no_string() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> {
            parse("(WORD)");
        });
    }

    @Test
    void parse_int() throws ParseException {
        IntItem intItem = (IntItem) parse("(INT '[42;]')");
        assertEquals("[42;]", intItem.getNumber());
    }

    @Test
    void parse_range() throws ParseException {
        IntItem intItem = (IntItem) parse("(INT '[42;73]')");
        assertEquals("[42;73]", intItem.getNumber());
    }

    @Test
    void parse_range_withlimit() throws ParseException {
        IntItem intItem = (IntItem) parse("(INT '[42;73;32]')");
        assertEquals("[42;73;32]", intItem.getNumber());
    }

    @Test
    void parse_prefix() throws ParseException {
        PrefixItem prefixItem = (PrefixItem) parse("(PREFIX 'word')");
        assertEquals("word", prefixItem.getWord());
    }

    @Test
    void parse_subString() throws ParseException {
        SubstringItem subStringItem = (SubstringItem) parse("(SUBSTRING 'word')");
        assertEquals("word", subStringItem.getWord());
    }

    @Test
    void parse_exactString() throws ParseException {
        ExactStringItem subStringItem = (ExactStringItem) parse("(EXACT 'word')");
        assertEquals("word", subStringItem.getWord());
    }

    @Test
    void parse_suffix() throws ParseException {
        SuffixItem suffixItem = (SuffixItem) parse("(SUFFIX 'word')");
        assertEquals("word", suffixItem.getWord());
    }

    @Test
    void parse_phrase() throws ParseException {
        PhraseItem phraseItem = (PhraseItem) parse("(PHRASE (WORD 'word'))");
        assertTrue(phraseItem.getItem(0) instanceof WordItem);
    }

    @Test
    void parse_near() throws ParseException {
        assertTrue(parse("(NEAR)") instanceof NearItem);
    }

    @Test
    void parse_onear() throws ParseException {
        assertTrue(parse("(ONEAR)") instanceof ONearItem);
    }

    @Test
    void parse_near_with_distance() throws ParseException {
        NearItem nearItem = (NearItem) parse("(NEAR {'distance' 42} (WORD 'first'))");
        assertEquals(42, nearItem.getDistance());
    }

    @Test
    void parse_items_with_connectivity() throws ParseException {
        AndItem andItem = (AndItem) parse("(AND (WORD {'id' '1'} 'first') (WORD {'connectivity' ['1' 23.5]} 'second'))");
        WordItem secondItem = (WordItem) andItem.getItem(1);

        assertEquals(andItem.getItem(0), secondItem.getConnectedItem());
        assertEquals(23.5, secondItem.getConnectivity(), 0.000001);
    }

    @Test
    void parse_word_with_index() throws ParseException {
        WordItem wordItem = (WordItem) parse("(WORD {'index' 'someIndex'} 'text')");
        assertEquals("someIndex", wordItem.getIndexName());
    }

    @Test
    void parse_unicode_word() throws ParseException {
        WordItem wordItem = (WordItem) parse("(WORD 'trăm')");
        assertEquals("trăm", wordItem.getWord());
    }

    public static String getWord(Object item) {
        return ((WordItem)item).getWord();
    }

}
