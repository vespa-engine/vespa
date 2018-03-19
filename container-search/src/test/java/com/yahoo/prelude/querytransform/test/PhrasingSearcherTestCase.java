// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.PhrasingSearcher;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests phrasing stuff
 *
 * @author bratseth
 * @author Einar M R Rosenvinge
 */
public class PhrasingSearcherTestCase {

    @Test
    public void testTotalPhrasing() {
        Searcher searcher=
            new PhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query();
        AndItem andItem=new AndItem();
        andItem.addItem(new WordItem("tudor","someindex"));
        andItem.addItem(new WordItem("vidor","someindex"));
        query.getModel().getQueryTree().setRoot(andItem);

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        Item item=((CompositeItem) query.getModel().getQueryTree().getRoot()).getItem(0);
        assertTrue(item instanceof PhraseItem);
        PhraseItem phrase=(PhraseItem)item;
        assertEquals(2,phrase.getItemCount());
        assertEquals("tudor",phrase.getWordItem(0).getWord());
        assertEquals("vidor",phrase.getWordItem(1).getWord());
        assertEquals("someindex",phrase.getIndexName());
    }

    @Test
    public void testPartialPhrasing() {
        Searcher searcher=
            new PhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query("?query=void%20tudor%20vidor%20kanoo");

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        CompositeItem item=(CompositeItem) query.getModel().getQueryTree().getRoot();
        assertEquals("void",((WordItem)item.getItem(0)).getWord());
        assertEquals("kanoo",((WordItem)item.getItem(2)).getWord());

        PhraseItem phrase=(PhraseItem)item.getItem(1);
        assertEquals(2,phrase.getItemCount());
        assertEquals("tudor",phrase.getWordItem(0).getWord());
        assertEquals("vidor",phrase.getWordItem(1).getWord());
    }

    @Test
    public void testPartialPhrasingSuggestOnly() {
        Searcher searcher=
            new PhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query("?query=void%20tudor%20vidor%20kanoo&suggestonly=true");
        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        CompositeItem item=(CompositeItem) query.getModel().getQueryTree().getRoot();
        assertEquals("void", ((WordItem)item.getItem(0)).getWord());
        assertEquals("tudor",((WordItem)item.getItem(1)).getWord());
        assertEquals("vidor",((WordItem)item.getItem(2)).getWord());
        assertEquals("kanoo",((WordItem)item.getItem(3)).getWord());
    }

    @Test
    public void testNoPhrasingIfDifferentIndices() {
        Searcher searcher=
            new PhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query();
        AndItem andItem=new AndItem();
        andItem.addItem(new WordItem("tudor","someindex"));
        andItem.addItem(new WordItem("vidor","anotherindex"));
        query.getModel().getQueryTree().setRoot(andItem);

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        CompositeItem item=(CompositeItem) query.getModel().getQueryTree().getRoot();

        assertTrue(item.getItem(0) instanceof WordItem);
        WordItem word=(WordItem)item.getItem(0);
        assertEquals("tudor",word.getWord());

        assertTrue(item.getItem(1) instanceof WordItem);
        word=(WordItem)item.getItem(1);
        assertEquals("vidor",word.getWord());
    }

    @Test
    public void testMultiplePhrases() {
        Searcher searcher=
            new PhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query();
        AndItem andItem=new AndItem();
        andItem.addItem(new WordItem("tudor","someindex"));
        andItem.addItem(new WordItem("tudor","someindex"));
        andItem.addItem(new WordItem("vidor","someindex"));
        andItem.addItem(new WordItem("vidor","someindex"));

        OrItem orItem=new OrItem();
        andItem.addItem(orItem);

        orItem.addItem(new WordItem("tudor"));
        AndItem andItem2=new AndItem();
        andItem2.addItem(new WordItem("this","anotherindex"));
        andItem2.addItem(new WordItem("is","anotherindex"));
        andItem2.addItem(new WordItem("a","anotherindex"));
        andItem2.addItem(new WordItem("test","anotherindex"));
        andItem2.addItem(new WordItem("tudor","anotherindex"));
        andItem2.addItem(new WordItem("vidor","anotherindex"));
        orItem.addItem(andItem2);
        orItem.addItem(new WordItem("vidor"));


        query.getModel().getQueryTree().setRoot(andItem);

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals("AND someindex:tudor someindex:\"tudor vidor\" someindex:vidor (OR tudor (AND anotherindex:\"this is a test\" anotherindex:\"tudor vidor\") vidor)", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testNoDetection() {
        Searcher searcher=
            new PhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query();
        AndItem andItem=new AndItem();
        andItem.addItem(new WordItem("no"));
        andItem.addItem(new WordItem("such"));
        andItem.addItem(new WordItem("phrase"));
        query.getModel().getQueryTree().setRoot(andItem);

        new Execution(searcher, Execution.Context.createContextStub()).search(query);

        assertEquals("AND no such phrase", query.getModel().getQueryTree().getRoot().toString());

    }

    @Test
    public void testNoFileNoChange() {
        Searcher searcher = new PhrasingSearcher("");

        Query query=new Query();
        AndItem andItem=new AndItem();
        andItem.addItem(new WordItem("no", "anindex"));
        andItem.addItem(new WordItem("such", "anindex"));
        andItem.addItem(new WordItem("phrase", "indexo"));
        OrItem orItem = new OrItem();
        orItem.addItem(new WordItem("habla"));
        orItem.addItem(new WordItem("babla"));
        orItem.addItem(new WordItem("habla"));
        andItem.addItem(orItem);
        query.getModel().getQueryTree().setRoot(andItem);

        new Execution(searcher, Execution.Context.createContextStub()).search(query);

        assertEquals("AND anindex:no anindex:such indexo:phrase (OR habla babla habla)", query.getModel().getQueryTree().getRoot().toString());
    }

}
