// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Logger;

import java.util.logging.Level;

import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.metrics.simple.Counter;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.yolean.Exceptions;

/**
 * Check whether the query tree seems to be "well formed". In other words, run heuristics against
 * the input data to see whether the query should sent to the search backend.
 *
 * @author Steinar Knutsen
 */
@Before(PhaseNames.BACKEND)
public class InputCheckingSearcher extends Searcher {

    private final Counter utfRejections;
    private final Counter repeatedConsecutiveTermsInPhraseRejections;
    private final Counter repeatedTermsInPhraseRejections;
    private static final Logger log = Logger.getLogger(InputCheckingSearcher.class.getName());
    private final int MAX_REPEATED_CONSECUTIVE_TERMS_IN_PHRASE = 5;
    private final int MAX_REPEATED_TERMS_IN_PHRASE = 10;

    public InputCheckingSearcher(MetricReceiver metrics) {
        utfRejections = metrics.declareCounter("double_encoded_utf8_rejections");
        repeatedTermsInPhraseRejections = metrics.declareCounter("repeated_terms_in_phrase_rejections");
        repeatedConsecutiveTermsInPhraseRejections = metrics.declareCounter("repeated_consecutive_terms_in_phrase_rejections");
    }

    @Override
    public Result search(Query query, Execution execution) {
        try {
            checkQuery(query);
        } catch (IllegalInputException e) {
            log.log(Level.FINE, () -> "Rejected query '" + query.toString() + "' on cause of: " + Exceptions.toMessageString(e));
            return new Result(query, ErrorMessage.createIllegalQuery(e.getMessage()));
        }
        return execution.search(query);
    }

    private void checkQuery(Query query) {
        doubleEncodedUtf8(query);
        checkPhrases(query.getModel().getQueryTree().getRoot());
        // add new heuristics here
    }

    private void checkPhrases(Item queryItem) {
        if (queryItem instanceof PhraseItem phrase) {
            repeatedConsecutiveTermsInPhraseCheck(phrase);
            repeatedTermsInPhraseCheck(phrase);
        } else  if (queryItem instanceof CompositeItem asComposite) {
            for (ListIterator<Item> i = asComposite.getItemIterator(); i.hasNext();) {
                checkPhrases(i.next());
            }
        }
    }

    private void repeatedConsecutiveTermsInPhraseCheck(PhraseItem phrase) {
        if (phrase.getItemCount() > MAX_REPEATED_CONSECUTIVE_TERMS_IN_PHRASE) {
            String prev = null;
            int repeatedCount = 0;
            for (int i = 0; i < phrase.getItemCount(); ++i) {
                Item item = phrase.getItem(i);
                if (item instanceof TermItem term) {
                    String current = term.getIndexedString();
                    if (prev != null) {
                        if (prev.equals(current)) {
                            repeatedCount++;
                            if (repeatedCount >= MAX_REPEATED_CONSECUTIVE_TERMS_IN_PHRASE) {
                                repeatedConsecutiveTermsInPhraseRejections.add();
                                throw new IllegalInputException("More than " + MAX_REPEATED_CONSECUTIVE_TERMS_IN_PHRASE +
                                                                " occurrences of term '" + current +
                                                                "' in a row detected in phrase : " + phrase.toString());
                            }
                        } else {
                            repeatedCount = 0;
                        }
                    }
                    prev = current;
                } else {
                    prev = null;
                    repeatedCount = 0;
                }
            }
        }
    }
    private static final class Count {
        private int v;
        Count(int initial) { v = initial; }
        void inc() { v++; }
        int get() { return v; }
    }
    private void repeatedTermsInPhraseCheck(PhraseItem phrase) {
        if (phrase.getItemCount() > MAX_REPEATED_TERMS_IN_PHRASE) {
            Map<String, Count> repeatedCount = new HashMap<>();
            for (int i = 0; i < phrase.getItemCount(); ++i) {
                Item item = phrase.getItem(i);
                if (item instanceof TermItem term) {
                    String current = term.getIndexedString();
                    Count count = repeatedCount.get(current);
                    if (count != null) {
                        if (count.get() >= MAX_REPEATED_TERMS_IN_PHRASE) {
                            repeatedTermsInPhraseRejections.add();
                            throw new IllegalInputException("Phrase contains more than " + MAX_REPEATED_TERMS_IN_PHRASE +
                                                            " occurrences of term '" + current + "' in phrase : " + phrase.toString());
                        }
                        count.inc();
                    } else {
                        repeatedCount.put(current, new Count(1));
                    }
                }
            }
        }
    }


    private void doubleEncodedUtf8(Query query) {
        int singleCharacterTerms = countSingleCharacterUserTerms(query.getModel().getQueryTree());
        if (singleCharacterTerms <= 4) {
            return;
        }
        String userInput = query.getModel().getQueryString();
        ByteBuffer asOctets = ByteBuffer.allocate(userInput.length());
        boolean asciiOnly = true;
        for (int i = 0; i < userInput.length(); ++i) {
            char c = userInput.charAt(i);
            if (c > 255) {
                return; // not double (or more) encoded
            }
            if (c > 127) {
                asciiOnly = false;
            }
            asOctets.put((byte) c);
        }
        if (asciiOnly) {
            return;
        }
        asOctets.flip();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                                                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        // OK, unmappable character is sort of theoretical, but added to be explicit
        try {
            decoder.decode(asOctets);
        } catch (CharacterCodingException e) {
            return;
        }
        utfRejections.add();
        throw new IllegalInputException("The user input has been determined to be double encoded UTF-8."
                                        + " Please investigate whether this is a false positive.");
    }

    private int countSingleCharacterUserTerms(Item queryItem) {
        if (queryItem instanceof CompositeItem asComposite) {
            int sum = 0;
            for (ListIterator<Item> i = asComposite.getItemIterator(); i.hasNext();) {
                sum += countSingleCharacterUserTerms(i.next());
            }
            return sum;
        } else if (queryItem instanceof WordItem word) {
            return (word.isFromQuery() && word.stringValue().length() == 1) ? 1 : 0;
        } else {
            return 0;
        }
    }

}
