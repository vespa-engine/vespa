// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item.test;

import com.yahoo.prelude.query.*;
import com.yahoo.search.query.textserialize.item.ItemContext;
import com.yahoo.search.query.textserialize.item.ItemFormHandler;
import com.yahoo.search.query.textserialize.parser.ParseException;
import com.yahoo.search.query.textserialize.parser.Parser;
import org.junit.Test;

import java.io.StringReader;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author tonytv
 */
public class ParseItemTestCase {

    public static Object parse(String input) throws ParseException {
        ItemContext context = new ItemContext();
        Object result = new Parser(new StringReader(input.replace("'", "\"")), new ItemFormHandler(), context).start();
        context.connectItems();
        return result;
    }

    @Test
    public void parse_and() throws ParseException {
        assertThat(parse("(AND)"), instanceOf(AndItem.class));
    }

    @Test
    public void parse_and_with_children() throws ParseException {
        AndItem andItem = (AndItem) parse("(AND (WORD 'first') (WORD 'second'))");

        assertThat(andItem.getItemCount(), is(2));
        assertThat(getWord(andItem.getItem(0)), is("first"));
    }

    @Test
    public void parse_or() throws ParseException {
        assertThat(parse("(OR)"), instanceOf(OrItem.class));
    }

    @Test
    public void parse_and_not_rest() throws ParseException {
        assertThat(parse("(AND-NOT-REST)"), instanceOf(NotItem.class));
    }

    @Test
    public void parse_and_not_rest_with_children() throws ParseException {
        NotItem notItem = (NotItem) parse("(AND-NOT-REST (WORD 'positive') (WORD 'negative'))");
        assertThat(getWord(notItem.getPositiveItem()), is("positive"));
        assertThat(getWord(notItem.getItem(1)), is("negative"));
    }

    @Test
    public void parse_and_not_rest_with_only_negated_children() throws ParseException {
        NotItem notItem = (NotItem) parse("(AND-NOT-REST null (WORD 'negated-item'))");
        assertNull(notItem.getPositiveItem());
        assertThat(notItem.getItem(1), instanceOf(WordItem.class));
    }

    @Test
    public void parse_rank() throws ParseException {
        assertThat(parse("(RANK (WORD 'first'))"), instanceOf(RankItem.class));
    }

    @Test
    public void parse_word() throws ParseException {
        WordItem wordItem = (WordItem) parse("(WORD 'text')");
        assertThat(wordItem.getWord(), is("text"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fail_when_word_given_multiple_strings() throws ParseException {
        parse("(WORD 'one' 'invalid')");
    }

    @Test(expected = IllegalArgumentException.class)
    public void fail_when_word_given_no_string() throws ParseException {
        parse("(WORD)");
    }

    @Test
    public void parse_int() throws ParseException {
        IntItem intItem = (IntItem) parse("(INT '[42;]')");
        assertThat(intItem.getNumber(), is("[42;]"));
    }

    @Test
    public void parse_range() throws ParseException {
        IntItem intItem = (IntItem) parse("(INT '[42;73]')");
        assertThat(intItem.getNumber(), is("[42;73]"));
    }

    @Test
    public void parse_range_withlimit() throws ParseException {
        IntItem intItem = (IntItem) parse("(INT '[42;73;32]')");
        assertThat(intItem.getNumber(), is("[42;73;32]"));
    }

    @Test
    public void parse_prefix() throws ParseException {
        PrefixItem prefixItem = (PrefixItem) parse("(PREFIX 'word')");
        assertThat(prefixItem.getWord(), is("word"));
    }

    @Test
    public void parse_subString() throws ParseException {
        SubstringItem subStringItem = (SubstringItem) parse("(SUBSTRING 'word')");
        assertThat(subStringItem.getWord(), is("word"));
    }

    @Test
    public void parse_exactString() throws ParseException {
        ExactStringItem subStringItem = (ExactStringItem) parse("(EXACT 'word')");
        assertThat(subStringItem.getWord(), is("word"));
    }

    @Test
    public void parse_suffix() throws ParseException {
        SuffixItem suffixItem = (SuffixItem) parse("(SUFFIX 'word')");
        assertThat(suffixItem.getWord(), is("word"));
    }

    @Test
    public void parse_phrase() throws ParseException {
        PhraseItem phraseItem = (PhraseItem) parse("(PHRASE (WORD 'word'))");
        assertThat(phraseItem.getItem(0), instanceOf(WordItem.class));
    }

    @Test
    public void parse_near() throws ParseException {
        assertThat(parse("(NEAR)"), instanceOf(NearItem.class));
    }

    @Test
    public void parse_onear() throws ParseException {
        assertThat(parse("(ONEAR)"), instanceOf(ONearItem.class));
    }

    @Test
    public void parse_near_with_distance() throws ParseException {
        NearItem nearItem = (NearItem) parse("(NEAR {'distance' 42} (WORD 'first'))");
        assertThat(nearItem.getDistance(), is(42));
    }

    @Test
    public void parse_items_with_connectivity() throws ParseException {
        AndItem andItem = (AndItem) parse("(AND (WORD {'id' '1'} 'first') (WORD {'connectivity' ['1' 23.5]} 'second'))");
        WordItem secondItem = (WordItem) andItem.getItem(1);

        assertThat(secondItem.getConnectedItem(), is(andItem.getItem(0)));
        assertThat(secondItem.getConnectivity(), is(23.5));
    }

    @Test
    public void parse_word_with_index() throws ParseException {
        WordItem wordItem = (WordItem) parse("(WORD {'index' 'someIndex'} 'text')");
        assertThat(wordItem.getIndexName(), is("someIndex"));
    }

    @Test
    public void parse_unicode_word() throws ParseException {
        WordItem wordItem = (WordItem) parse("(WORD 'trăm')");
        assertThat(wordItem.getWord(), is("trăm"));
    }

    public static String getWord(Object item) {
        return ((WordItem)item).getWord();
    }
}
