// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.yahoo.fsa.FSA;
import com.yahoo.prelude.query.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Detects query phrases using an automaton. This class is thread safe.
 *
 * @author bratseth
 */
public class PhraseMatcher {

    private FSA phraseFSA = null;

    private boolean matchPhraseItems = false;

    private boolean matchSingleItems = false;

    /** Whether this should ignore regular plural/singular form differences when matching */
    private boolean ignorePluralForm = false;

    /** False to matche the longest phrase, true to match <i>all</i> phrases */
    private boolean matchAll = false;

    /** For null subclass only */
    private PhraseMatcher() {
    }

    /**
     * Creates a phrase matcher. This will not ignore plural/singular form differences when matching
     *
     * @param  phraseAutomatonFile the file containing phrases to match
     * @throws IllegalArgumentException if the file is not found
     */
    public PhraseMatcher(String phraseAutomatonFile) {
        this(phraseAutomatonFile,false);
    }

    /**
     * Creates a phrase matcher
     *
     * @param  phraseAutomatonFile the file containing phrases to match
     * @param ignorePluralForm whether we should ignore plural and singular forms as matches
     * @throws IllegalArgumentException if the file is not found
     */
    public PhraseMatcher(String phraseAutomatonFile,boolean ignorePluralForm) {
        this.ignorePluralForm=ignorePluralForm;
        phraseFSA=new FSA(phraseAutomatonFile);
    }

    /**
     * Creates a phrase matcher
     *
     * @param  phraseAutomatonFSA the fsa containing phrases to match
     * @param ignorePluralForm whether we should ignore plural and singular forms as matches
     * @throws IllegalArgumentException if FSA is null
     */
    public PhraseMatcher(FSA phraseAutomatonFSA,boolean ignorePluralForm) {
        if(phraseAutomatonFSA==null) throw new IllegalArgumentException("FSA is null");
        this.ignorePluralForm=ignorePluralForm;
        phraseFSA=phraseAutomatonFSA;
    }

    public boolean isEmpty() { return phraseFSA == null; }
    
    /**
     * Set whether to match words contained in phrase items as well.
     * Default is false - don't match words contained in phrase items
     */
    public void setMatchPhraseItems(boolean matchPhraseItems) {
        this.matchPhraseItems=matchPhraseItems;
    }

    /**
     * Sets whether single items should be matched and returned as phrase matches.
     * Default is false.
     */
    public void setMatchSingleItems(boolean matchSingleItems) {
        this.matchSingleItems=matchSingleItems;
    }

    /** Sets whether we should ignore plural/singular form when matching */
    public void setIgnorePluralForm(boolean ignorePluralForm) { this.ignorePluralForm=ignorePluralForm; }

    /**
     * Sets whether to return the longest matching phrase when there are overlapping matches (default),
     * or <i>all</i> matching phrases
     */
    public void setMatchAll(boolean matchAll) { this.matchAll =matchAll; }

    /**
     * Finds all phrases (word sequences of length 1 or higher)
     * of the same index, not negative items of a notitem,
     * which constitutes a complete entry in the automaton of this matcher
     *
     * @param queryItem the root query item in which to match phrases
     * @return the matched phrases, or <b>null</b> if there was no matches
     */
    public List<Phrase> matchPhrases(Item queryItem) {
        if (matchSingleItems && (queryItem instanceof TermItem)) {
            return matchSingleItem((TermItem)queryItem);
        }
        else {
            MatchedPhrases phrases=new MatchedPhrases();
            recursivelyMatchPhrases(queryItem,phrases);
            return phrases.toList();
        }
    }

    /** Returns null if this word does not match the automaton, a single-item list if it does */
    private List<Phrase> matchSingleItem(TermItem termItem) {
        String matchWord=toLowerCase(termItem.stringValue());
        String replaceWord=null;
        FSA.State state = phraseFSA.getState();
        if (!matches(state,matchWord)) {
            if (!ignorePluralForm) return null;
            matchWord=switchForm(matchWord);
            if (!matches(state,matchWord)) return null;
            replaceWord=matchWord;
        }

        List<Phrase> itemList=new java.util.ArrayList<>(1);
        itemList.add(new Phrase(termItem,replaceWord,state.dataString()));
        return itemList;

    }

    private boolean matches(FSA.State state,String word) {
        state.start();
        state.delta(word);
        return state.isFinal();
    }

    /** Find matches within a composite */
    private void recursivelyMatchPhrases(Item item, MatchedPhrases phrases) {
        if (item == null) return;
        if ( ! (item instanceof CompositeItem) ) return;
        if ( ! matchPhraseItems && item instanceof PhraseItem ) return;

        CompositeItem owner=(CompositeItem)item;
        int i=0;
        int checkItemCount=owner.getItemCount();
        if (owner instanceof NotItem)
            checkItemCount=1; // Skip negatives

        while (i<checkItemCount) {
            int largestFoundLength=findPhrasesAtStartpoint(i,owner,phrases);

            if (largestFoundLength==0 || matchAll) {
                recursivelyMatchPhrases(owner.getItem(i),phrases);
                i=i+1;
            }
            else {
                i=i+largestFoundLength;
            }
        }
    }

    /**
     * If (!matchAll), finds longest possible phrase starting at the
     * given index in the owner and adds it to phrases.
     *
     * If (matchAll), finds all possible phrases starting at the given index
     *
     * @return the length of the largest phrase found at this starting point, or 0 if none
     */
    private int findPhrasesAtStartpoint(int startIndex,CompositeItem owner,MatchedPhrases phrases) {
        FSA.State state = phraseFSA.getState();
        int currentIndex=startIndex;
        Phrase phrase=null;
        List<String> replaceList=null;

        String index=null;
        state.start();

        while (currentIndex<owner.getItemCount()) { // Loop until the largest possible phrase is passed
            Item current=owner.getItem(currentIndex);
            if (! (current instanceof TermItem) ) break;

            TermItem termItem=(TermItem)current;

            if (state.isStartState())
                index=termItem.getIndexName();
            else
                if (!termItem.getIndexName().equals(index)) break;

            String lowercased = toLowerCase(termItem.stringValue());
            boolean matched=state.tryDeltaWord(lowercased);
            if (!matched && ignorePluralForm) {
                String invertedWord=switchForm(lowercased);
                matched=state.tryDeltaWord(invertedWord);
                if (matched)
                    replaceList=setReplace(replaceList,currentIndex-startIndex,invertedWord);
            }
            if (!matched) break;

            if (state.isFinal()) // Legal return point reached, but we'll look for longer ones too
                phrase=new Phrase(owner,replaceList,startIndex,currentIndex-startIndex+1,state.dataString());
            if (matchAll)
                phrases.add(phrase);
            currentIndex++;
        }

        if (phrase==null) return 0;
        if (!matchAll)
            phrases.add(phrase);
        return phrase.getLength();
    }

    /** Adds a replace word at an index, and any required null's to get to this item. Creates the list if it is null */
    private List<String> setReplace(List<String> replaceList,int index,String invertedWord) {
        if (replaceList==null)
            replaceList=new ArrayList<>();
        while (replaceList.size()<index)
            replaceList.add(null);
        replaceList.add(invertedWord);
        return replaceList;
    }

    /** Makes this plural if it is singular and vice-versa */
    private String switchForm(String word) {
        if (word.endsWith("s") && word.length()>2)
            return word.substring(0,word.length()-1);
        return word + "s";
    }

    /** Holder of a lazily created list of matched phrases */
    private static class MatchedPhrases {

        private List<Phrase> phrases=null;

        private void add(Phrase phrase) {
            if (phrase==null) return;
            if (phrases==null)
                phrases=new java.util.ArrayList<>(5);
            phrases.add(phrase);
        }

        /** Returns the list of contained phrases, or null */
        public List<Phrase> toList() { return phrases; }

    }

    /**
     * Points to a collection of word items (one or more)
     * which is matches a complete listing in an automat
     */
    public static class Phrase {

        /** Points to the single or multiple words matched by this phrase */
        private Matched matched;

        private String data;


        private Phrase(Matched matched,String data) {
            this.matched=matched;
            this.data=data;
        }


        public Phrase(TermItem item,String replace,String data) {
            this(new MatchedWord(item,replace),data);
        }

        /**
         * Creates a phrase match
         *
         * @param owner the composite we have matched within
         * @param replace the list of string to replace the matched by, or null to not replace.
         *        This transfers ownership of this list to this class - it can not subsequently be accessed
         *        by the caller. If this list is set, it must have the same length as <code>length</code>.
         *        No replacement is represented by null items within the list.
         * @param startIndex the first index in composite to match
         * @param length the length of the matched terms
         * @param data the data accompanying this match
         */
        private Phrase(CompositeItem owner,List<String> replace,int startIndex,int length,String data) {
            this(new MatchedComposite(owner,replace,startIndex,length),data);
        }

        /** Returns the owner, or null if this is a single item phrase with no owner */
        public CompositeItem getOwner() { return matched.getOwner(); }

        public int getStartIndex() { return matched.getStartIndex(); }

        public int getLength() { return matched.getLength(); }

        /** Returns the data stored by the automaton for this phrase at this position, or null if none */
        public String getData() { return data; }

        /** Returns the n'th item in this, throws if index out of bounds */
        public TermItem getItem(int index) {
            return matched.getItem(index);
        }

        /** Returns true if this phrase contains all the words of the owner, or if there is no owner */
        public boolean isComplete() {
            return matched.isComplete();
        }

        /** Replaces the words items of this phrase with a phrase item. Does nothing if this is not a composite match */
        public void replace() {
            matched.replace();
        }

        /** Removes the word items of this phrase. Does nothing nuless this is a composite */
        public void remove() {
            matched.remove();
        }

        /** Returns the length of the underlying phrase */
        public int getBackedLength() {
            return matched.getBackedLength();
        }

        /** Returns the items of this phrase as a read-only iterator */
        public MatchIterator itemIterator() {
            return new MatchIterator(this);
        }

        public String toString() {
            StringBuilder buffer=new StringBuilder("\"");
            for (Iterator<Item> i=itemIterator(); i.hasNext(); ) {
                buffer.append(i.next().toString());
                if (i.hasNext())
                    buffer.append(" ");
            }
            buffer.append("\"");
            return buffer.toString();
        }

        private abstract static class Matched {

            public abstract CompositeItem getOwner();

            public abstract int getStartIndex();

            public abstract int getLength();

            public abstract boolean isComplete();

            /** Returns whether there is an index at the current item */
            public abstract boolean hasItemAt(int index);

            public void replace() {}

            public void remove() {}

            public abstract TermItem getItem(int index);

            public abstract String getReplace(int index);

            /** Returns the length of the underlying item */
            public abstract int getBackedLength();

            public abstract boolean hasReplaces();

        }

        private static class MatchedWord extends Matched {

            /** The term matched by this */
            private TermItem item;

            /** The word to replace the matched word by, or null to not replace */
            private String replace;

            public MatchedWord(TermItem item,String replace) {
                this.item=item;
                this.replace=replace;
            }

            public Item getItem() { return item; }

            public boolean hasItemAt(int index) {
                return index==0;
            }

            public CompositeItem getOwner() { return null; }

            public int getStartIndex() { return 0; }

            public int getLength() { return 1; }

            @Override
            public TermItem getItem(int index) {
                if (index!=0) throw new IndexOutOfBoundsException("No word at " + index + " in " + this);
                return item;
            }

            public boolean isComplete() { return true; }

            public int getBackedLength() { return 1; }

            public String getReplace(int index) { return replace; }

            public boolean hasReplaces() { return replace!=null; }

        }

        private static class MatchedComposite extends Matched {

            /** The item having the phrase words as direct descendants */
            private CompositeItem owner;

            /** The number of phrase items */
            private int length;

            private int initialOwnerLength;

            /** The (0-base) index of the first phrase word item in the owner */
            private int startIndex;

            /** The first matched item */
            private Item startItem;

            /**
             * The word to replace by at the given index, or null if none of the phrase words should be replaced
             * This is either null, or of length <code>length</code>, with null values where nothing should be replaced
             */
            private List<String> replace=null;

            public MatchedComposite(CompositeItem owner,List<String> replace,int startIndex,int length) {
                this.owner=owner;
                this.initialOwnerLength=owner.getItemCount();
                this.replace = replace;
                this.startIndex=startIndex;
                this.startItem=owner.getItem(startIndex);
                this.length=length;
            }

            public CompositeItem getOwner() { return owner; }

            public int getStartIndex() { return startIndex; }

            public int getLength() { return length; }

            public int getBackedLength() { return owner.getItemCount()-startIndex; }

            public boolean hasItemAt(int index) {
                adjustIfBackingChanged();
                if (startIndex<0) return false; // Invalid state because of backing changes
                if ( index >= length ) return false;
                if ( index+startIndex >= owner.getItemCount() ) return false;
                return true;
            }

            public boolean isComplete() {
                return startIndex==0 && length==owner.getItemCount();
            }

            @Override
            public TermItem getItem(int index) {
                adjustIfBackingChanged();
                return (TermItem)owner.getItem(startIndex+index);
            }

            public String getReplace(int index) {
                if (replace==null) return null;
                return replace.get(index);
            }

            public void replace() {
                PhraseItem phrase=new PhraseItem();
                TermItem firstWord=(TermItem)owner.setItem(startIndex,phrase);
                replace(firstWord,0);
                phrase.setIndexName(firstWord.getIndexName());
                phrase.addItem(firstWord);
                for (int i=1; i<length; i++) {
                    TermItem followingWord=(TermItem)owner.removeItem(startIndex+1);
                    replace(followingWord,i);
                    phrase.addItem(followingWord);
                }
            }

            private void replace(TermItem item,int index) {
                if (replace==null) return;
                String replaceString=replace.get(index);
                if (replaceString==null) return;
                item.setValue(replaceString);
            }

            public void remove() {
                for (int i=startIndex+length-1; i>=startIndex; i--)
                    owner.removeItem(i);
            }

            public boolean hasReplaces() { return replace!=null; }

            /**
             * Detects and attemts to compensate for a changed backing. Stop-gap measure until we get a through
             * design for this
             */
            private void adjustIfBackingChanged() {
                if (owner.getItemCount()==initialOwnerLength) return;
                startIndex=owner.getItemIndex(startItem);
            }

        }

        public static class MatchIterator implements Iterator<Item> {

            private Phrase phrase;

            private int currentIndex=0;

            public MatchIterator(Phrase phrase) {
                this.phrase=phrase;
            }

            public boolean hasNext() {
                return phrase.matched.hasItemAt(currentIndex);
                //return (currentIndex<phrase.getLength());
                //return phrase.matched.hasItemAt(currentIndex);
            }

            /** Returns the value to replace the item last returned by next(), or null to keep it as-is */
            public String getReplace() {
                return phrase.matched.getReplace(currentIndex-1);
            }

            public Item next() {
                if (!hasNext())
                    throw new NoSuchElementException(this + " has no more elements");

                currentIndex++;
                if ((phrase.matched instanceof MatchedWord))
                    return ((MatchedWord)phrase.matched).getItem();
                else
                    return phrase.getOwner().getItem(phrase.getStartIndex()+currentIndex-1);
            }

            public void remove() {
                throw new UnsupportedOperationException("Can not remove from a phrasematcher phrase");
            }

        }

    }

    /** Returns a phrase matcher which (quickly) never matches anything */
    public static PhraseMatcher getNullMatcher() {

        return new PhraseMatcher() {

            @Override
            public List<Phrase> matchPhrases(Item item) {
                    return null;
                }

        };

    }

}
