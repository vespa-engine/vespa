// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa;

import java.util.Iterator;

import com.yahoo.prelude.query.*;

/**
 * Marshal a query stack into an advanced query string suitable for
 * passing to another QRS.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @author <a href="mailto:rafan@yahoo-inc.com">Rong-En Fan</a>
 */
public class QueryMarshaller {
    private boolean atRoot = true;

    public String marshal(Item root) {
        if (root == null || root instanceof NullItem) {
            return null;
        }
        StringBuilder s = new StringBuilder();
        marshal(root, s);
        atRoot = true;
        return s.toString();
    }

    /**
     * We do not yet care about exact match indices
     */
    private void marshal(Item root, StringBuilder s) {
        switch (root.getItemType()) {
        case OR:
            marshalOr((OrItem) root, s);
            break;
        case AND:
            marshalAnd((CompositeItem) root, s);
            break;
        case NOT:
            marshalNot((NotItem) root, s);
            break;
        case RANK:
            marshalRank((RankItem) root, s);
            break;
        case WORD:
        case INT:
        case PREFIX:
        case SUBSTRING:
        case SUFFIX:
            marshalWord((TermItem) root, s);
            break;
        case PHRASE:
            // PhraseItem and PhraseSegmentItem don't add quotes for segmented
            // termse
            if (root instanceof PhraseSegmentItem) {
                marshalPhrase((PhraseSegmentItem) root, s);
            } else {
                marshalPhrase((PhraseItem) root, s);
            }
            break;
        case NEAR:
            marshalNear((NearItem) root, s);
            break;
        case ONEAR:
            marshalNear((ONearItem) root, s);
            break;
        case WEAK_AND:
            marshalWeakAnd((WeakAndItem)root, s);
            break;
        default:
            break;
        }
    }


    private void marshalWord(TermItem item, StringBuilder s) {
        String index = item.getIndexName();
        if (index.length() != 0) {
            s.append(item.getIndexName()).append(':');
        }
        s.append(item.stringValue());
        if (item.getWeight() != Item.DEFAULT_WEIGHT)
            s.append("!").append(item.getWeight());
    }

    private void marshalRank(RankItem root, StringBuilder s) {
        marshalComposite("RANK", root, s);
    }

    private void marshalNot(NotItem root, StringBuilder s) {
        marshalComposite("ANDNOT", root, s);
    }

    private void marshalOr(OrItem root, StringBuilder s) {
        marshalComposite("OR", root, s);
    }

    /**
     * Dump WORD items, and add space between each of them unless those
     * words came from segmentation.
     *
     * @param root CompositeItem
     * @param s current marshaled query
     */
    private void dumpWords(CompositeItem root, StringBuilder s) {
        for (Iterator<Item> i = root.getItemIterator(); i.hasNext();) {
            Item word = i.next();
            boolean useSeparator = true;
            if (word instanceof TermItem) {
                s.append(((TermItem) word).stringValue());
                if (word instanceof WordItem) {
                    useSeparator = !((WordItem) word).isFromSegmented();
                }
            } else {
                dumpWords((CompositeItem) word, s);
            }
            if (useSeparator && i.hasNext()) {
                s.append(' ');
            }
        }
    }

    private void marshalPhrase(PhraseItem root, StringBuilder s) {
        marshalPhrase(root, s, root.isExplicit(), false);
    }

    private void marshalPhrase(PhraseSegmentItem root, StringBuilder s) {
        marshalPhrase(root, s, root.isExplicit(), true);
    }

    private void marshalPhrase(IndexedItem root, StringBuilder s, boolean isExplicit, boolean isSegmented) {
        String index = root.getIndexName();
        if (index.length() != 0) {
            s.append(root.getIndexName()).append(':');
        }
        if (isExplicit || !isSegmented) s.append('"');
        dumpWords((CompositeItem) root, s);
        if (isExplicit || !isSegmented) s.append('"');
    }

    private void marshalNear(NearItem root, StringBuilder s) {
        marshalComposite(root.getName() + "(" + root.getDistance() + ")", root, s);
    }

    // Not only AndItem returns ItemType.AND
    private void marshalAnd(CompositeItem root, StringBuilder s) {
        marshalComposite("AND", root, s);
    }

    private void marshalWeakAnd(WeakAndItem root, StringBuilder s) {
        marshalComposite("WAND(" + root.getN() + ")", root, s);
    }

    private void marshalComposite(String operator, CompositeItem root, StringBuilder s) {
        boolean useParen = !atRoot;
        if (useParen) {
            s.append("( ");
        } else {
            atRoot = false;
        }
        for (Iterator<Item> i = root.getItemIterator(); i.hasNext();) {
            Item item = i.next();
            marshal(item, s);
            if (i.hasNext())
                s.append(' ').append(operator).append(' ');
        }
        if (useParen) {
            s.append(" )");
        }
    }
}
