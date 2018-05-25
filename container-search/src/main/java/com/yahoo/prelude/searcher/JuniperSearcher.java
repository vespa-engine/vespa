// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.search.Searcher;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.BoldCloseFieldPart;
import com.yahoo.prelude.hitfield.BoldOpenFieldPart;
import com.yahoo.prelude.hitfield.FieldPart;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.SeparatorFieldPart;
import com.yahoo.prelude.hitfield.StringFieldPart;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

/**
 * Converts juniper highlighting to XML style
 * <p>
 * Note: This searcher only converts backend binary highlighting and separators
 * to the configured highlighting and separator tags.
 *
 * @author Steinar Knutsen
 */
@After(PhaseNames.RAW_QUERY)
@Before(PhaseNames.TRANSFORMED_QUERY)
@Provides(JuniperSearcher.JUNIPER_TAG_REPLACING)
public class JuniperSearcher extends Searcher {

    public final static char RAW_HIGHLIGHT_CHAR = '\u001F';
    public final static char RAW_SEPARATOR_CHAR = '\u001E';

    private static final String ELLIPSIS = "...";

    // The name of the field containing document type
    private static final String MAGIC_FIELD = Hit.SDDOCNAME_FIELD;

    public static final String JUNIPER_TAG_REPLACING = "JuniperTagReplacing";

    private String boldOpenTag;
    private String boldCloseTag;
    private String separatorTag;

    @Inject
    public JuniperSearcher(ComponentId id, QrSearchersConfig config) {
        super(id);

        boldOpenTag = config.tag().bold().open();
        boldCloseTag = config.tag().bold().close();
        separatorTag = config.tag().separator();
    }

    /**
     * Convert Juniper style property highlighting to XML style.
     */
    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        highlight(query.getPresentation().getBolding(), result.hits().deepIterator(), null,
                  execution.context().getIndexFacts().newSession(query));
        return result;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        Result workResult = result;
        int worstCase = workResult.getHitCount();
        List<Hit> hits = new ArrayList<>(worstCase);
        for (Iterator<Hit> i = workResult.hits().deepIterator(); i.hasNext();) {
            Hit sniffHit = i.next();
            if ( ! (sniffHit instanceof FastHit)) continue;

            FastHit hit = (FastHit) sniffHit;
            if (hit.isFilled(summaryClass)) continue;

            hits.add(hit);
        }
        execution.fill(workResult, summaryClass);
        highlight(workResult.getQuery().getPresentation().getBolding(), hits.iterator(), summaryClass,
                  execution.context().getIndexFacts().newSession(result.getQuery()));
    }

    private void highlight(boolean bolding, Iterator<Hit> hitsToHighlight,
                           String summaryClass, IndexFacts.Session indexFacts) {
        while (hitsToHighlight.hasNext()) {
            Hit sniffHit = hitsToHighlight.next();
            if ( ! (sniffHit instanceof FastHit)) continue;

            FastHit hit = (FastHit) sniffHit;
            if (summaryClass != null &&  ! hit.isFilled(summaryClass)) continue;

            Object searchDefinitionField = hit.getField(MAGIC_FIELD);
            if (searchDefinitionField == null) continue;

            // TODO: Loop using callback (see below)
            for (String fieldName : hit.fields().keySet()) {
                Index index = indexFacts.getIndex(fieldName, searchDefinitionField.toString());
                if (index.getDynamicSummary() || index.getHighlightSummary())
                    insertTags(hit.buildHitField(fieldName, true, true), bolding, index.getDynamicSummary());
            }
            /*
            for (Index index : indexFacts.getIndexes(searchDefinitionField.toString())) {
                if (index.getDynamicSummary() || index.getHighlightSummary()) {
                    HitField fieldValue = hit.buildHitField(index.getName(), true, true);
                    if (fieldValue != null)
                        insertTags(fieldValue, bolding, index.getDynamicSummary());
                }
            }
            */
        }
    }

    private void insertTags(HitField oldProperty, boolean bolding, boolean dynteaser) {
        boolean insideHighlight = false;
        for (ListIterator<FieldPart> i = oldProperty.listIterator(); i.hasNext();) {
            FieldPart f = i.next();
            if (f instanceof SeparatorFieldPart)
                setSeparatorString(bolding, (SeparatorFieldPart) f);
            if (f.isFinal()) continue;

            String toQuote = f.getContent();
            List<FieldPart> newFieldParts = null;
            int previous = 0;
            for (int j = 0; j < toQuote.length(); j++) {
                char key = toQuote.charAt(j);
                switch (key) {
                    case RAW_HIGHLIGHT_CHAR:
                        newFieldParts = initFieldParts(newFieldParts);
                        addBolding(bolding, insideHighlight, f, toQuote, newFieldParts, previous, j);
                        previous = j + 1;
                        insideHighlight = !insideHighlight;
                        break;
                    case RAW_SEPARATOR_CHAR:
                        newFieldParts = initFieldParts(newFieldParts);
                        addSeparator(bolding, dynteaser, f, toQuote, newFieldParts,
                                     previous, j);
                        previous = j + 1;
                        break;
                    default:
                        // no action
                        break;
                }
            }
            if (previous > 0 && previous < toQuote.length()) {
                newFieldParts.add(new StringFieldPart(toQuote.substring(previous), f.isToken()));
            }
            if (newFieldParts != null) {
                i.remove();
                for (Iterator<FieldPart> j = newFieldParts.iterator(); j.hasNext();) {
                    i.add(j.next());
                }
            }
        }
    }

    private void setSeparatorString(boolean bolding, SeparatorFieldPart f) {
        if (bolding)
            f.setContent(separatorTag);
        else
            f.setContent(ELLIPSIS);
    }

    private void addSeparator(boolean bolding, boolean dynteaser, FieldPart f, String toQuote,
                              List<FieldPart> newFieldParts, int previous, int j) {
        if (previous != j)
            newFieldParts.add(new StringFieldPart(toQuote.substring(previous, j), f.isToken()));
        if (dynteaser)
            newFieldParts.add(bolding ? new SeparatorFieldPart(separatorTag) : new SeparatorFieldPart(ELLIPSIS));
    }

    private void addBolding(boolean bolding, boolean insideHighlight, FieldPart f, String toQuote,
                            List<FieldPart> newFieldParts, int previous, int j) {
        if (previous != j) {
            newFieldParts.add(new StringFieldPart(toQuote.substring(previous, j), f.isToken()));
        }
        if (bolding) {
            if (insideHighlight) {
                newFieldParts.add(new BoldCloseFieldPart(boldCloseTag));
            } else {
                if (newFieldParts.size() > 0
                        && newFieldParts.get(newFieldParts.size() - 1) instanceof BoldCloseFieldPart) {
                    newFieldParts.remove(newFieldParts.size() - 1);
                } else {
                    newFieldParts.add(new BoldOpenFieldPart(boldOpenTag));
                }
            }
        }
    }

    private List<FieldPart> initFieldParts(List<FieldPart> newFieldParts) {
        if (newFieldParts == null)
            newFieldParts = new ArrayList<>();
        return newFieldParts;
    }

}
