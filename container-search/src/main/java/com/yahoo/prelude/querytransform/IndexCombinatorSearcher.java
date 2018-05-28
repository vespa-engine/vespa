// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import static com.yahoo.prelude.querytransform.PhrasingSearcher.PHRASE_REPLACEMENT;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.Index.Attribute;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.*;

/**
 * Searcher to rewrite queries to achieve mixed recall between indices and
 * memory attributes.
 *
 * @author Steinar Knutsen
 * @deprecated do not use
 */
@After({PhaseNames.RAW_QUERY, PHRASE_REPLACEMENT})
@Before(PhaseNames.TRANSFORMED_QUERY)
@Provides(IndexCombinatorSearcher.MIXED_RECALL_REWRITE)
@Deprecated // TODO: Remove on Vespa 7 (not necessary any more)
public class IndexCombinatorSearcher extends Searcher {

    public static final String MIXED_RECALL_REWRITE = "MixedRecallRewrite";

    private static class ArrayComparator implements Comparator<Attribute[]> {
        /**
         * Note, this ignores if there is a difference in whether to
         * attributes have tokenized content. (If this is the case,
         * we are having worse problems anyway.)
         */
        public int compare(Attribute[] o1, Attribute[] o2 ) {
            if (o1.length < o2.length) {
                return -1;
            } else if (o1.length > o2.length) {
                return 1;
            }
            int limit = o1.length;
            for (int i = 0; i < limit; ++i) {
                int r = o1[i].name.compareTo(o2[i].name);
                if (r != 0) {
                    return r;
                }
            }
            return 0;
        }
    }

    private final ArrayComparator comparator = new ArrayComparator();

    private enum RewriteStrategies {
        NONE, CHEAP_AND, EXPENSIVE_AND, FLAT
    }

    @Override
    public Result search(Query query, Execution execution) {
        Item root = query.getModel().getQueryTree().getRoot();
        IndexFacts.Session session = execution.context().getIndexFacts().newSession(query);
        String oldQuery = (query.getTraceLevel() >= 2) ? root.toString() : "";

        if (root instanceof BlockItem || root instanceof PhraseItem) {
            root = convertSinglePhraseOrBlock(root, session);
        } else if (root instanceof CompositeItem) {
            root = rewrite((CompositeItem) root, session);
        }
        query.getModel().getQueryTree().setRoot(root);

        if ((query.getTraceLevel() >= 2) && !(oldQuery.equals(root.toString()))) {
            query.trace("Rewriting for mixed recall between indices and attributes", true, 2);
        }
        return execution.search(query);
    }

    private RewriteStrategies chooseRewriteStrategy(CompositeItem c, IndexFacts.Session session) {
        if (c instanceof OrItem) {
            return RewriteStrategies.FLAT;
        } else if (!(c instanceof AndItem)) {
            return RewriteStrategies.NONE;
        }
        Map<Attribute[], Integer> m = new TreeMap<>(comparator);
        for (Iterator<Item> i = c.getItemIterator(); i.hasNext();) {
            Item j = i.next();
            if (j instanceof BlockItem || j instanceof PhraseItem) {
                Attribute[] attributes= getIndices((HasIndexItem) j, session);
                if (attributes == null) {
                    continue;
                }
                Integer count = m.get(attributes);
                if (count == null) {
                    count = 1;
                } else {
                    count = count.intValue() + 1;
                }
                m.put(attributes, count);
            }
        }

        if (m.size() == 0) {
            return RewriteStrategies.NONE;
        }

        int singles = 0;
        int pairs = 0;
        int higher = 0;
        // count the number of sets being associated with 1, 2 or more terms
        for (Integer i : m.values()) {
            switch (i.intValue()) {
            case 1:
                ++singles;
                break;
            case 2:
                pairs += 2;
                break;
            default:
                ++higher;
                break;
            }
        }
        if (higher == 0 && pairs + singles <= 2) {
            return RewriteStrategies.EXPENSIVE_AND;
        } else {
            return RewriteStrategies.CHEAP_AND;
        }
    }

    private CompositeItem rewriteNot(NotItem not, IndexFacts.Session session) {
        Item positive = not.getItem(0);
        if (positive instanceof BlockItem || positive instanceof PhraseItem) {
            positive = convertSinglePhraseOrBlock(positive, session);
            not.setItem(0, positive);
        } else if (positive instanceof CompositeItem) {
            CompositeItem c = (CompositeItem) positive;
            positive = rewrite(c, session);
            not.setItem(0, positive);
        }

        int length = not.getItemCount();
        // no need for keeping proximity in the negative branches, so we
        // convert them one by one, _and_ always uses cheap transform
        for (int i = 1; i < length; ++i) {
            Item exclusion = not.getItem(i);
            if (exclusion instanceof BlockItem || exclusion instanceof PhraseItem) {
                exclusion = convertSinglePhraseOrBlock(exclusion, session);
                not.setItem(i, exclusion);
            } else if (exclusion instanceof CompositeItem) {
                CompositeItem c = (CompositeItem) exclusion;
                switch (chooseRewriteStrategy(c, session)) {
                case NONE:
                    c = traverse(c, session);
                    break;
                case CHEAP_AND:
                case EXPENSIVE_AND:
                    c = cheapTransform(c, session);
                    break;
                default:
                    c = flatTransform(c, session);
                    break;
                }
                not.setItem(i, c);
            }
        }
        return not;
    }

    private Item rewrite(CompositeItem c, IndexFacts.Session session) {
        if (c instanceof NotItem) {
            c = rewriteNot((NotItem) c, session);
            return c;
        } else {
            switch (chooseRewriteStrategy(c, session)) {
                case NONE:
                    c = traverse(c, session);
                    break;
                case CHEAP_AND:
                    c = cheapTransform(c, session);
                    break;
                case EXPENSIVE_AND:
                    c = expensiveTransform((AndItem) c, session);
                    break;
                case FLAT:
                    c = flatTransform(c, session);
                    break;
                default:
                    break;
            }
        }
        return c;
    }

    private CompositeItem traverse(CompositeItem c, IndexFacts.Session session) {
        int length = c.getItemCount();
        for (int i = 0; i < length; ++i) {
            Item word = c.getItem(i);
            if (word instanceof CompositeItem && !(word instanceof PhraseItem) && !(word instanceof BlockItem)) {
                c.setItem(i, rewrite((CompositeItem) word, session));
            }
        }
        return c;
    }

    private CompositeItem expensiveTransform(AndItem c, IndexFacts.Session session) {
        int[] indices = new int[2];
        int items = 0;
        int length = c.getItemCount();
        Attribute[][] names = new Attribute[2][];
        CompositeItem result = null;
        for (int i = 0; i < length; ++i) {
            Item word = c.getItem(i);
            if (word instanceof BlockItem || word instanceof PhraseItem) {
                Attribute[] attributes = getIndices((HasIndexItem) word, session);
                if (attributes == null) {
                    continue;
                }
                // this throwing an out of bounds if more than two candidates is intentional
                names[items] = attributes;
                indices[items++] = i;
            } else if (word instanceof CompositeItem) {
                c.setItem(i, rewrite((CompositeItem) word, session));
            }
        }
        switch (items) {
        case 1:
            result = linearAnd(c, names[0], indices[0]);
            break;
        case 2:
            result = quadraticAnd(c, names[0], names[1], indices[0], indices[1]);
            break;
        default:
            // should never happen
            getLogger().log(
                    LogLevel.WARNING,
                    "Unexpected number of items for mixed recall, got " + items
                            + ", expected 1 or 2.");
            break;
        }
        return result;
    }

    private Attribute[] getIndices(HasIndexItem block, IndexFacts.Session session) {
        return session.getIndex(block.getIndexName()).getMatchGroup();
    }

    private OrItem linearAnd(AndItem c, Attribute[] names, int brancherIndex) {
        OrItem or = new OrItem();
        for (int i = 0; i < names.length; ++i) {
            AndItem duck = (AndItem) c.clone();
            Item b = retarget(duck.getItem(brancherIndex), names[i]);
            duck.setItem(brancherIndex, b);
            or.addItem(duck);
        }
        return or;
    }

    private OrItem quadraticAnd(AndItem c, Attribute[] firstNames, Attribute[] secondNames, int firstBrancher, int secondBrancher) {
        OrItem or = new OrItem();
        for (int i = 0; i < firstNames.length; ++i) {
            for (int j = 0; j < secondNames.length; ++j) {
                AndItem duck = (AndItem) c.clone();
                Item b = retarget(duck.getItem(firstBrancher), firstNames[i]);
                duck.setItem(firstBrancher, b);
                b = retarget(duck.getItem(secondBrancher), secondNames[j]);
                duck.setItem(secondBrancher, b);
                or.addItem(duck);
            }
        }
        return or;
    }

    private CompositeItem flatTransform(CompositeItem c, IndexFacts.Session session) {
        int maxIndex = c.getItemCount() - 1;
        for (int i = maxIndex; i >= 0; --i) {
            Item word = c.getItem(i);
            if (word instanceof BlockItem || word instanceof PhraseItem) {
                Attribute[] attributes = getIndices((HasIndexItem) word, session);
                if (attributes == null) {
                    continue;
                }
                c.removeItem(i);
                for (Attribute name : attributes) {
                    Item term = word.clone();
                    Item forNewIndex = retarget(term, name);
                    c.addItem(forNewIndex);
                }
            } else if (word instanceof CompositeItem) {
                c.setItem(i, rewrite((CompositeItem) word, session));
            }
        }
        return c;
    }

    private CompositeItem cheapTransform(CompositeItem c, IndexFacts.Session session) {
        if (c instanceof OrItem) {
            return flatTransform(c, session);
        }
        int length = c.getItemCount();
        for (int i = 0; i < length; ++i) {
            Item j = c.getItem(i);
            if (j instanceof BlockItem || j instanceof PhraseItem) {
                Attribute[] attributes = getIndices((HasIndexItem) j, session);
                if (attributes == null) {
                    continue;
                }
                CompositeItem or = searchAllForItem(j, attributes);
                c.setItem(i, or);
            } else if (j instanceof CompositeItem) {
                c.setItem(i, rewrite((CompositeItem) j, session));
            }
        }
        return c;
    }

    private OrItem searchAllForItem(Item word, Attribute[] attributes) {
        OrItem or = new OrItem();
        for (Attribute name : attributes) {
            Item term = word.clone();
            term = retarget(term, name);
            or.addItem(term);
        }
        return or;
    }

    private Item retarget(Item word, Attribute newIndex) {
        if (word instanceof PhraseItem && !newIndex.isTokenizedContent()) {
            PhraseItem asPhrase = (PhraseItem) word;
            WordItem newWord = new WordItem(asPhrase.getIndexedString(), newIndex.name, false);
            return newWord;
        } else if (word instanceof IndexedItem) {
            word.setIndexName(newIndex.name);
        } else if (word instanceof CompositeItem) {
            CompositeItem asComposite = (CompositeItem) word;
            for (Iterator<Item> i = asComposite.getItemIterator(); i.hasNext();) {
                Item segment = i.next();
                segment.setIndexName(newIndex.name);
            }
        }
        return word;
    }

    private Item convertSinglePhraseOrBlock(Item item, IndexFacts.Session session) {
        Item newItem;
        Attribute[] attributes = getIndices((HasIndexItem) item, session);
        if (attributes == null) {
            return item;
        }
        newItem = searchAllForItem(item, attributes);
        return newItem;
    }

}
