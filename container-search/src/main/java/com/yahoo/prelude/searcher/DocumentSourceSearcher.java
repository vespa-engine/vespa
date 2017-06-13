// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * <p>Implements a document source.  You pass in a query and a Result
 * set.  When this Searcher is called with that query it will return
 * that result set.</p>
 *
 * <p>This supports multi-phase search.</p>
 *
 * <p>To avoid having to add type information for the fields, a quck hack is used to
 * support testing of attribute prefetching.
 * Any field in the configured hits which has a name starting by attribute
 * will be returned when attribute prefetch filling is requested.</p>
 *
 * @author  bratseth
 */
@SuppressWarnings({"rawtypes"})
public class DocumentSourceSearcher extends Searcher {
    // as for the SuppressWarnings annotation above, we are inside
    // com.yahoo.prelude, this is old stuff, really no point firing off those
    // warnings here...

    private Result defaultFilledResult;
    private Map<Query, Result> completelyFilledResults = new HashMap<>();
    private Map<Query, Result> attributeFilledResults = new HashMap<>();
    private Map<Query, Result> unFilledResults = new HashMap<>();
    //private Result defaultUnfilledResult;

    /** Time (in ms) at which the index of this searcher was last modified */
    long editionTimeStamp=0;

    private int queryCount;

    public DocumentSourceSearcher() {
        addDefaultResults();
    }

    /**
     * Adds a result which can be returned either as empty,
     * filled or attribute only filled later.
     * Summary fields starting by "a" are attributes, others are not.
     *
     * @return true when replacing an existing &lt;query, result&gt; pair.
     */
    public boolean addResultSet(Query query, Result fullResult) {
        Result emptyResult = new Result(query.clone());
        Result attributeResult = new Result(query.clone());
        emptyResult.setTotalHitCount(fullResult.getTotalHitCount());
        attributeResult.setTotalHitCount(fullResult.getTotalHitCount());
        int counter=0;
        for (Iterator i = fullResult.hits().deepIterator();i.hasNext();) {
            Hit fullHit = (Hit)i.next();

            Hit emptyHit = fullHit.clone();
            emptyHit.clearFields();
            emptyHit.setFillable();
            emptyHit.setRelevance(fullHit.getRelevance());

            Hit attributeHit = fullHit.clone();
            removePropertiesNotStartingByA(attributeHit);
            attributeHit.setFillable();
            attributeHit.setRelevance(fullHit.getRelevance());
            for (Object propertyKeyObject : (Set) fullHit.fields().keySet()) {
                String propertyKey=propertyKeyObject.toString();
                if (propertyKey.startsWith("attribute"))
                    attributeHit.setField(propertyKey, fullHit.getField(propertyKey));
            }
            if (fullHit.getField(Hit.SDDOCNAME_FIELD)!=null)
                attributeHit.setField(Hit.SDDOCNAME_FIELD, fullHit.getField(Hit.SDDOCNAME_FIELD));

            // A simple summary lookup mechanism, similar to FastSearch's
            emptyHit.setField("summaryid", String.valueOf(counter));
            attributeHit.setField("summaryid", String.valueOf(counter));
            fullHit.setField("summaryid", String.valueOf(counter));

            counter++;
            emptyResult.hits().add(emptyHit);
            attributeResult.hits().add(attributeHit);
        }
        unFilledResults.put(getQueryKeyClone(query), emptyResult);
        attributeFilledResults.put(getQueryKeyClone(query), attributeResult);
        if (completelyFilledResults.put(getQueryKeyClone(query), fullResult.clone()) != null) {
            setEditionTimeStamp(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    /**
     * Returns a query clone which has offset and hits set to null. This is used by access to
     * the maps using the query as key to achieve lookup independent of offset/hits value
     */
    private com.yahoo.search.Query getQueryKeyClone(com.yahoo.search.Query query) {
        com.yahoo.search.Query key=query.clone();
        key.setWindow(0,0);
        key.getModel().setSources("");
        return key;
    }

    private void removePropertiesNotStartingByA(Hit hit) {
        List<String> toRemove=new java.util.ArrayList<>();
        for (Iterator i= ((Set) hit.fields().keySet()).iterator(); i.hasNext(); ) {
            String key=(String)i.next();
            if (!key.startsWith("a"))
                toRemove.add(key);
        }
        for (Iterator<String> i=toRemove.iterator(); i.hasNext(); ) {
            String propertyName=i.next();
            hit.removeField(propertyName);
        }
    }

    private void addDefaultResults() {
        Query q = new Query("?query=default");
        Result r = new Result(q);
        r.hits().add(new Hit("http://default-1.html"));
        r.hits().add(new Hit("http://default-2.html"));
        r.hits().add(new Hit("http://default-3.html"));
        r.hits().add(new Hit("http://default-4.html"));
        defaultFilledResult = r;
        addResultSet(q, r);
    }

    public long getEditionTimeStamp(){
        long myEditionTime;
        synchronized(this){
            myEditionTime=this.editionTimeStamp;
        }
        return myEditionTime;
    }

    public void setEditionTimeStamp(long editionTime) {
        synchronized(this){
            this.editionTimeStamp=editionTime;
        }
    }

    public Result search(com.yahoo.search.Query query, Execution execution)  {
        queryCount++;
        Result r;
        r = unFilledResults.get(getQueryKeyClone(query));
        if (r == null) {
            r = defaultFilledResult.clone();
        } else {
            r = r.clone();
        }
        r.setQuery(query);
        r.hits().trim(query.getOffset(), query.getHits());
        return r;
    }

    @Override
    public void fill(com.yahoo.search.Result result, String summaryClass, Execution execution) {
        Result filledResult;
        if ("attributeprefetch".equals(summaryClass))
            filledResult=attributeFilledResults.get(getQueryKeyClone(result.getQuery()));
        else
            filledResult = completelyFilledResults.get(getQueryKeyClone(result.getQuery()));

        if (filledResult == null) {
            filledResult = defaultFilledResult;
        }
        fillHits(filledResult,result,summaryClass);
    }

    private void fillHits(Result source,Result target,String summaryClass) {
        for (Iterator hitsToFill= target.hits().deepIterator() ; hitsToFill.hasNext();) {
            Hit hitToFill = (Hit) hitsToFill.next();
            String summaryId= (String) hitToFill.getField("summaryid");
            if (summaryId==null) continue; // Can not fill this
            Hit filledHit = lookupBySummaryId(source,summaryId);
            if (filledHit==null)
                throw new RuntimeException("Can't fill hit with summaryid '" + summaryId + "', not present");

            for (Iterator props= filledHit.fieldIterator();props.hasNext();) {
                Map.Entry propertyEntry = (Map.Entry)props.next();
                hitToFill.setField(propertyEntry.getKey().toString(),
                                   propertyEntry.getValue());
            }
            hitToFill.setFilled(summaryClass);
        }
        target.analyzeHits();
    }

    private Hit lookupBySummaryId(Result result,String summaryId) {
        for (Iterator i= result.hits().deepIterator(); i.hasNext(); ) {
            Hit hit=(Hit)i.next();
            if (summaryId.equals(hit.getField("summaryid"))) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Returns the number of queries made to this searcher since the last
     * reset. For testing - not reliable if multiple threads makes
     * queries simultaneously
     */
    public int getQueryCount() {
        return queryCount;
    }

    public void resetQueryCount() {
        queryCount=0;
    }

}
