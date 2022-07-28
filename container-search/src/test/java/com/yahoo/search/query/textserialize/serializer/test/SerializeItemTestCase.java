// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.jupiter.api.Test;

import static com.yahoo.search.query.textserialize.item.test.ParseItemTestCase.parse;
import static com.yahoo.search.query.textserialize.item.test.ParseItemTestCase.getWord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tony Vaagenes
 */
public class SerializeItemTestCase {

    @Test
    void serialize_word_item() {
        WordItem item = new WordItem("test that \" and \\ works");
        item.setIndexName("index\"Name");

        WordItem deSerialized = serializeThenParse(item);
        assertEquals(item.getWord(), deSerialized.getWord());
        assertEquals(item.getIndexName(), deSerialized.getIndexName());
    }

    @Test
    void serialize_and_item() {
        AndItem andItem = new AndItem();
        andItem.addItem(new WordItem("first"));
        andItem.addItem(new WordItem("second"));

        AndItem deSerialized = serializeThenParse(andItem);
        assertEquals("first", getWord(deSerialized.getItem(0)));
        assertEquals("second", getWord(deSerialized.getItem(1)));
        assertEquals(2, deSerialized.getItemCount());
    }

    @Test
    void serialize_or_item() {
        assertTrue(serializeThenParse(new OrItem()) instanceof OrItem);
    }

    @Test
    void serialize_not_item() {
        NotItem notItem = new NotItem();
        {
            notItem.addItem(new WordItem("first"));
            notItem.addItem(new WordItem("second"));
        }

        serializeThenParse(notItem);
    }

    @Test
    void serialize_near_item() {
        int distance = 23;
        NearItem nearItem = new NearItem(distance);
        {
            nearItem.addItem(new WordItem("first"));
            nearItem.addItem(new WordItem("second"));
        }

        NearItem deSerialized = serializeThenParse(nearItem);

        assertEquals(distance, deSerialized.getDistance());
        assertEquals(2, deSerialized.getItemCount());
    }

    @Test
    void serialize_phrase_item() throws ParseException {
        PhraseItem phraseItem = new PhraseItem(new String[]{"first", "second"});
        phraseItem.setIndexName("indexName");

        PhraseItem deSerialized = serializeThenParse(phraseItem);
        assertEquals(phraseItem.getItem(0), deSerialized.getItem(0));
        assertEquals(phraseItem.getItem(1), deSerialized.getItem(1));
        assertEquals(phraseItem.getIndexName(), deSerialized.getIndexName());
    }

    @Test
    void serialize_equiv_item() {
        EquivItem equivItem = new EquivItem();
        equivItem.addItem(new WordItem("first"));

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertEquals(1, deSerialized.getItemCount());
    }

    @Test
    void serialize_connectivity() {
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

        assertEquals(second, first.getConnectedItem());
        assertEquals(3.14, first.getConnectivity(), 0.0000001);
    }

    @Test
    void serialize_significance() {
        EquivItem equivItem = new EquivItem();
        equivItem.setSignificance(24.2);

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertEquals(24.2, deSerialized.getSignificance(), 0.00000001);
    }

    @Test
    void serialize_unique_id() {
        EquivItem equivItem = new EquivItem();
        equivItem.setUniqueID(42);

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertEquals(42, deSerialized.getUniqueID());
    }

    @Test
    void serialize_weight() {
        EquivItem equivItem = new EquivItem();
        equivItem.setWeight(42);

        EquivItem deSerialized = serializeThenParse(equivItem);
        assertEquals(42, deSerialized.getWeight());
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
