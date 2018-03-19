// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.PhraseMatcher;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author bratseth
 */
public class PhraseMatcherTestCase {

    @Test
    public void testSingleItemMatching() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");
        matcher.setMatchSingleItems(true);
        List<?> matches=matcher.matchPhrases(new WordItem("aword"));

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(1,match.getLength());
        assertEquals("",match.getData());
        assertEquals(null,match.getOwner());
        assertEquals(0,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem("aword"),i.next());
        assertNull(i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testSingleItemMatchingCaseInsensitive() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");
        matcher.setMatchSingleItems(true);
        final String mixedCase = "aWoRD";
        List<?> matches=matcher.matchPhrases(new WordItem(mixedCase));

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(1,match.getLength());
        assertEquals("",match.getData());
        assertEquals(null,match.getOwner());
        assertEquals(0,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem(mixedCase),i.next());
        assertNull(i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testSingleItemMatchingWithPluralIgnore() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        matcher.setMatchSingleItems(true);
        List<?> matches=matcher.matchPhrases(new WordItem("awords"));

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(1,match.getLength());
        assertEquals("",match.getData());
        assertEquals(null,match.getOwner());
        assertEquals(0,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem("awords"),i.next());
        assertEquals("aword",i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testSingleItemMatchingCaseInsensitiveWithPluralIgnore() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        matcher.setMatchSingleItems(true);
        final String mixedCase = "aWoRDS";
        List<?> matches=matcher.matchPhrases(new WordItem(mixedCase));

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(1,match.getLength());
        assertEquals("",match.getData());
        assertEquals(null,match.getOwner());
        assertEquals(0,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem(mixedCase),i.next());
        assertEquals("aword",i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testPhraseMatching() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        AndItem and=new AndItem();
        and.addItem(new WordItem("noisebefore"));
        and.addItem(new WordItem("this"));
        and.addItem(new WordItem("is"));
        and.addItem(new WordItem("a"));
        and.addItem(new WordItem("test"));
        and.addItem(new WordItem("noiseafter"));
        List<?> matches=matcher.matchPhrases(and);

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(4,match.getLength());
        assertEquals("",match.getData());
        assertEquals(and,match.getOwner());
        assertEquals(1,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem("this"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("is"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("a"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("test"),i.next());
        assertEquals(null,i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testPhraseMatchingCaseInsensitive() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        AndItem and=new AndItem();
        and.addItem(new WordItem("noisebefore"));
        final String firstWord = "thIs";
        and.addItem(new WordItem(firstWord));
        final String secondWord = "Is";
        and.addItem(new WordItem(secondWord));
        final String thirdWord = "A";
        and.addItem(new WordItem(thirdWord));
        final String fourthWord = "tEst";
        and.addItem(new WordItem(fourthWord));
        and.addItem(new WordItem("noiseafter"));
        List<?> matches=matcher.matchPhrases(and);

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(4,match.getLength());
        assertEquals("",match.getData());
        assertEquals(and,match.getOwner());
        assertEquals(1,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem(firstWord),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem(secondWord),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem(thirdWord),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem(fourthWord),i.next());
        assertEquals(null,i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testPhraseMatchingWithNumber() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        AndItem and=new AndItem();
        and.addItem(new WordItem("noisebefore"));
        and.addItem(new WordItem("this"));
        and.addItem(new WordItem("is"));
        and.addItem(new IntItem("3"));
        and.addItem(new WordItem("tests"));
        and.addItem(new WordItem("noiseafter"));
        List<?> matches=matcher.matchPhrases(and);

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(4,match.getLength());
        assertEquals("",match.getData());
        assertEquals(and,match.getOwner());
        assertEquals(1,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem("this"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("is"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new IntItem("3"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("tests"),i.next());
        assertEquals(null,i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testPhraseMatchingWithPluralIgnore() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        AndItem and=new AndItem();
        and.addItem(new WordItem("noisebefore"));
        and.addItem(new WordItem("thi"));
        and.addItem(new WordItem("is"));
        and.addItem(new WordItem("a"));
        and.addItem(new WordItem("tests"));
        and.addItem(new WordItem("noiseafter"));
        List<?> matches=matcher.matchPhrases(and);

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(4,match.getLength());
        assertEquals("",match.getData());
        assertEquals(and,match.getOwner());
        assertEquals(1,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem("thi"),i.next());
        assertEquals("this",i.getReplace());
        assertEquals(new WordItem("is"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("a"),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem("tests"),i.next());
        assertEquals("test",i.getReplace());
        assertFalse(i.hasNext());
    }

    @Test
    public void testPhraseMatchingCaseInsensitiveWithPluralIgnore() {
        PhraseMatcher matcher=new PhraseMatcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa",true);
        AndItem and=new AndItem();
        and.addItem(new WordItem("noisebefore"));
        final String firstWord = "thI";
        and.addItem(new WordItem(firstWord));
        final String secondWord = "Is";
        and.addItem(new WordItem(secondWord));
        final String thirdWord = "A";
        and.addItem(new WordItem(thirdWord));
        final String fourthWord = "tEsts";
        and.addItem(new WordItem(fourthWord));
        and.addItem(new WordItem("noiseafter"));
        List<?> matches=matcher.matchPhrases(and);

        assertNotNull(matches);
        assertEquals(1,matches.size());
        PhraseMatcher.Phrase match=(PhraseMatcher.Phrase)matches.get(0);
        assertEquals(4,match.getLength());
        assertEquals("",match.getData());
        assertEquals(and,match.getOwner());
        assertEquals(1,match.getStartIndex());
        PhraseMatcher.Phrase.MatchIterator i=match.itemIterator();
        assertEquals(new WordItem(firstWord),i.next());
        assertEquals("this",i.getReplace());
        assertEquals(new WordItem(secondWord),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem(thirdWord),i.next());
        assertEquals(null,i.getReplace());
        assertEquals(new WordItem(fourthWord),i.next());
        assertEquals("test",i.getReplace());
        assertFalse(i.hasNext());
    }

}
