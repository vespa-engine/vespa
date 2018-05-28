// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;


import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.*;


/**
 * Flattens a result consisting of multiple hit groups containing hits
 * into a single flat list of hits.
 *
 * @author Bob Travis
 * @author Steinar Knutsen
 * @author Arne Fossaa
 */
@After(PhaseNames.BLENDED_RESULT)
@Before(PhaseNames.UNBLENDED_RESULT)
@Provides(BlendingSearcher.BLENDING)
public class BlendingSearcher extends Searcher {

    public static final String BLENDING = "Blending";

    private final String documentId;

    @Inject
    public BlendingSearcher(ComponentId id, QrSearchersConfig cfg) {
        super(id);
        QrSearchersConfig.Com.Yahoo.Prelude.Searcher.BlendingSearcher s = cfg.com().yahoo().prelude().searcher().BlendingSearcher();
        documentId = s.docid().length() > 0 ? s.docid() : null;

    }

    /**
     * Only for legacy tests.
     */
    public BlendingSearcher(String blendingDocumentId) {
        this.documentId = blendingDocumentId;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);

        Result blended = blendResults(result, query, query.getOffset(), query.getHits(), execution);
        blended.trace("Blended result");
        return blended;
    }

    /**
     * Fills this result by forwarding to the right chained searchers
     */
    @Override
    public void fill(com.yahoo.search.Result result, String summaryClass, Execution execution) {
        execution.fill(result, summaryClass);
        result.analyzeHits();
    }

    /**
     * Produce a single blended result list from a group of hitgroups.
     *
     * It is assumed that the results are ordered in hitgroups. If not, the blend will not be performed
     */
    protected Result blendResults(Result result, Query q, int offset, int hits, Execution execution) {

        //Assert that there are more than one hitgroup and that there are only hitgroups on the lowest level

        boolean foundNonGroup = false;
        Iterator<Hit> hitIterator = result.hits().iterator();
        List<HitGroup> groups = new ArrayList<>();
        while (hitIterator.hasNext()) {
            Hit hit = hitIterator.next();
            if (hit instanceof HitGroup) {
                groups.add((HitGroup)hit);
                hitIterator.remove();
            } else if(!hit.isMeta()) {
                foundNonGroup = true;
            }
        }

        if(foundNonGroup) {
            result.hits().addError(ErrorMessage.createUnspecifiedError("Blendingsearcher could not blend - there are toplevel hits" +
                                   " that are not hitgroups"));
            return result;
        }
        if (groups.size() == 0) {
            return result;
        } else if (groups.size() == 1) {
            result.hits().addAll(groups.get(0).asUnorderedHits());
            result.hits().setOrderer(groups.get(0).getOrderer());
            return result;
        } else {
            if (documentId != null) {
                return blendResultsUniquely(result, q, offset, hits, groups, execution);
            } else {
                return blendResultsDirectly(result, q, offset, hits, groups, execution);
            }
        }
    }

    private Result sortAndTrimResults(Result result, Query q, int offset, int hits, Execution execution) {
        if (q.getRanking().getSorting() != null) {
            execution.fillAttributes(result); // Always correct as we can only sort on attributes
            result.hits().sort();
        }
        result.hits().trim(offset, hits);
        return result;
    }

    private abstract class DocumentMerger {
        protected Set<String> documentsToStrip;
        protected Result result;
        protected HitGroup group;

        abstract void put(HitGroup source, Hit hit, Execution execution);

        abstract void scan(Hit hit, int i, Execution execution);

        Result getResult() {
            return result;
        }

        //Since we cannot use prelude.hit#getProperty, we'll have to improvise
        private String getProperty(Hit hit, String field) {
            Object o = hit.getField(field);
            return o == null ? null : o.toString();
        }


        protected void storeID(Hit hit, Execution execution) {
            String id = getProperty(hit, documentId);

            if (id != null) {
                documentsToStrip.add(id);
            } else {
                if (!result.isFilled(result.getQuery().getPresentation().getSummary())) {
                    fill(result, result.getQuery().getPresentation().getSummary(), execution);
                    id = getProperty(hit, documentId);
                    if (id != null) {
                        documentsToStrip.add(id);
                    }
                }
            }
        }

        protected boolean known(HitGroup source, Hit hit, Execution execution) {
            String stripID = getProperty(hit, documentId);

            if (stripID == null) {
                if (!source.isFilled(result.getQuery().getPresentation().getSummary())) {
                    Result nResult = new Result(result.getQuery());
                    nResult.hits().add(source);
                    fill(nResult, nResult.getQuery().getPresentation().getSummary(), execution);
                    stripID = getProperty(hit, documentId);
                    if (stripID == null) {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            if (documentsToStrip.contains(stripID)) {
                return true;
            }

            documentsToStrip.add(stripID);
            return false;
        }

        void scanResult(Execution execution) {
            List<Hit> hits = group.asUnorderedHits();
            for (int i = hits.size()-1; i >= 0; i--) {
                Hit sniffHit = hits.get(i);
                if (!sniffHit.isMeta()) {
                    scan(sniffHit, i, execution);
                } else {
                    result.hits().add(sniffHit);
                }
            }
        }

        void mergeResults(List<HitGroup> groups, Execution execution) {
            // note, different loop direction from scanResult()
            for(HitGroup group : groups.subList(1, groups.size())) {
                for(Hit hit : group.asList()) {
                    if(hit.isMeta()) {
                        result.hits().add(hit);
                    } else {
                        put(group, hit, execution);
                    }
                }
            }
        }
    }


    private class BasicMerger extends DocumentMerger {
        BasicMerger(Result result, HitGroup group) {
            this.result = result;
            this.group = group;
        }

        void put(HitGroup source, Hit hit, Execution execution) {
            result.hits().add(hit);
        }

        void scan(Hit hit, int i, Execution execution) {
            result.hits().add(hit);
        }
    }


    private class UniqueMerger extends DocumentMerger {
        UniqueMerger(Result result, HitGroup group, Set<String> documentsToStrip) {
            this.documentsToStrip = documentsToStrip;
            this.result = result;
            this.group = group;
        }

        void scan(Hit hit, int i, Execution execution) {
            result.hits().add(hit);
            if (!hit.isMeta()) {
                storeID(hit, execution);
            }
        }

        void put(HitGroup source, Hit hit, Execution execution) {
            if (!hit.isMeta()) {
                if (!known(source, hit, execution)) {
                    addHit(hit);
                }
            } else {
                result.hits().add(hit);
            }
        }

        protected void addHit(Hit hit) {
            result.hits().add(hit);
        }

    }

    private Result blendResultsDirectly(Result result, Query q, int offset,
                                        int hits, List<HitGroup> groups, Execution execution) {
        DocumentMerger m = new BasicMerger(result, groups.get(0));

        m.scanResult(execution);
        m.mergeResults(groups, execution);
        return sortAndTrimResults(m.getResult(), q, offset, hits, execution);
    }

    private Result blendResultsUniquely(Result result, Query q, int offset,
                                        int hits, List<HitGroup> groups, Execution execution) {
        DocumentMerger m = new UniqueMerger(result, groups.get(0), new HashSet<>(20));

        m.scanResult(execution);
        m.mergeResults(groups, execution);
        return sortAndTrimResults(m.getResult(), q, offset, hits, execution);
    }

}
