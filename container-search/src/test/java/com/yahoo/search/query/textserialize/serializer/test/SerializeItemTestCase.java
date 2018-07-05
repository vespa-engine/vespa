// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.serializer.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.textserialize.parser.ParseException;
import com.yahoo.search.query.textserialize.serializer.QueryTreeSerializer;
import org.junit.Test;

import static com.yahoo.search.query.textserialize.item.test.ParseItemTestCase.parse;
import static com.yahoo.search.query.textserialize.item.test.ParseItemTestCase.getWord;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * @author Tony Vaagenes
 */
public class SerializeItemTestCase {
    @Test
    public void serialize_word_item() {
        WordItem item = new WordItem("test that \" and \\ works");
        item.setIndexName("index\"Name");

        WordItem deSerialized = serializeThenParse(item);
        assertThat(deSerialized.getWord(), is(item.getWord()));
        assertThat(deSerialized.getIndexName(), is(item.getIndexName()));
    }

    @Test
    public void serialize_and_item() throws ParseException {
        AndItem andItem = new AndItem();
        andItem.addItem(new WordItem("first"));
        andItem.addItem(new WordItem("second"));

        AndItem deSerialized = serializeThenParse(andItem);
        assertThat(getWord(deSerialized.getItem(0)), is("first"));
        assertThat(getWord(deSerialized.getItem(1)), is("second"));
        assertThat(deSerialized.getItemCount(), is(2));
    }

    @Test
    public void serialize_or_item() throws ParseException {
        assertThat(serializeThenParse(new OrItem()),
                instanceOf(OrItem.class));
    }

    @Test
    public void serialize_not_item() throws ParseException {
        NotItem notItem = new NotItem();
        {
            notItem.addItem(new WordItem("first"));
            notItem.addItem(new WordItem("second"));
        }

        serializeThenParse(notItem);
    }

    @Test
    public void serialize_near_item() throws ParseException {
        int distance = 23;
        NearItem nearItem = new NearItem(distance);
        {
            nearItem.addItem(new WordItem("first"));
            nearItem.addItem(new WordItem("second"));
        }

        NearItem deSerialized = serializeThenParse(nearItem);

        assertThat(deSerialized.getDistance(), is(distance));
        assertThat(deSerialized.getItemCount(), is(2));
    }

    @Test
    public void serialize_phrase_item() throws ParseException {
        PhraseItem phraseItem = new PhraseItem(new String[] {"first", "second"});
        phraseItem.setIndexName("indexName");

        PhraseItem deSerialized = serializeThenParse(phraseItem);
        assertThat(deSerialized.getItem(0), is(phraseItem.getItem(0)));
        assertThat(deSerialized.getItem(1), is(phraseItem.getItem(1)));
        assertThat(deSerialized.getIndexName(), is(phraseItem.getIndexName()));
    }

    @Test
    public void serialize_equiv_item() throws ParseException {
        EquivItem equivItem = new EquivItem();
        equivItem.addItem(new WordItem("first"));

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertThat(deSerialized.getItemCount(), is(1));
    }

    @Test
    public void serialize_connectivity() throws ParseException {
        OrItem orItem = new OrItem();
        {
            WordItem first = new WordItem("first");
            WordItem second = new WordItem("second");
            first.setConnectivity(second, 3.14);

            orItem.addItem(first);
            orItem.addItem(second);
        }

        OrItem deSerialized = serializeThenParse(orItem);
        WordItem first = (WordItem) deSerialized.getItem(0);
        Item second = deSerialized.getItem(1);

        assertThat(first.getConnectedItem(), is(second));
        assertThat(first.getConnectivity(), is(3.14));
    }

    @Test
    public void serialize_significance() throws ParseException {
        EquivItem equivItem = new EquivItem();
        equivItem.setSignificance(24.2);

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertThat(deSerialized.getSignificance(), is(24.2));
    }

    @Test
    public void serialize_unique_id() throws ParseException {
        EquivItem equivItem = new EquivItem();
        equivItem.setUniqueID(42);

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertThat(deSerialized.getUniqueID(), is(42));
    }

    @Test
    public void serialize_weight() throws ParseException {
        EquivItem equivItem = new EquivItem();
        equivItem.setWeight(42);

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertThat(deSerialized.getWeight(), is(42));
    }

    private static String serialize(Item item) {
        return new QueryTreeSerializer().serialize(item);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Item> T serializeThenParse(T oldItem) {
        try {
            return (T) parse(serialize(oldItem));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
