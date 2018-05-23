// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;

import java.util.*;

/**
 * <p> Groups hits according to sddocname. </p>
 *
 * <p> For each group, the desired number of hits can be specified. </p>
 *
 *  @author  tonytv
 */
public class MultipleResultsSearcher extends Searcher {

    private final static String propertyPrefix = "multipleresultsets.";
    private static final CompoundName additionalHitsFactorName=new CompoundName(propertyPrefix + "additionalHitsFactor");
    private static final CompoundName maxTimesRetrieveHeterogeneousHitsName=new CompoundName(propertyPrefix + "maxTimesRetrieveHeterogeneousHits");
    private static final CompoundName numHits=new CompoundName(propertyPrefix + "numHits");

    @Override
    public Result search(Query query, Execution e) {
        try {
            Parameters parameters = new Parameters(query);

            query.trace("MultipleResultsSearcher: " + parameters, false, 2);
            HitsRetriever hitsRetriever = new HitsRetriever(query,e,parameters);

            for (DocumentGroup documentGroup : parameters.documentGroups) {
                if (  hitsRetriever.numHits(documentGroup) < documentGroup.targetNumberOfDocuments) {
                    hitsRetriever.retrieveMoreHits(documentGroup);
                }
            }

            return hitsRetriever.createMultipleResultSets();
        } catch(ParameterException exception) {
            Result result = new Result(query);
            result.hits().addError(ErrorMessage.createInvalidQueryParameter(exception.msg));
            return result;
        }
    }

    private class HitsRetriever {

        PartitionedResult partitionedResult;

        private int numRetrieveMoreHitsCalls = 0;
        private int nextOffset;
        private Query query;
        private final Parameters parameters;
        private final int hits;
        private final int offset;
        private Execution execution;
        private Result initialResult;

        HitsRetriever(Query query, Execution execution, Parameters parameters) throws ParameterException {
            this.offset=query.getOffset();
            this.hits=query.getHits();
            this.nextOffset = query.getOffset() + query.getHits();
            this.query = query;
            this.parameters = parameters;
            this.execution = execution;

            initialResult = retrieveHits();
            partitionedResult = new PartitionedResult(parameters.documentGroups, initialResult);

            this.query = query;
        }

        void retrieveMoreHits(DocumentGroup documentGroup) {
            if ( ++numRetrieveMoreHitsCalls <
                 parameters.maxTimesRetrieveHeterogeneousHits) {

                retrieveHeterogenousHits();

                if (numHits(documentGroup) <
                    documentGroup.targetNumberOfDocuments) {

                    retrieveMoreHits(documentGroup);
                }

            } else {
                retrieveRemainingHitsForGroup(documentGroup);
            }
        }

        void retrieveHeterogenousHits() {
            int numHitsToRetrieve = (int)(hits * parameters.additionalHitsFactor);

            final int maxNumHitsToRetrieve = 1000;
            numHitsToRetrieve = Math.min(numHitsToRetrieve,maxNumHitsToRetrieve);

            try {
                query.setWindow(nextOffset,numHitsToRetrieve);
                partitionedResult.addHits(retrieveHits());
            }
            finally {
                restoreWindow();
                nextOffset += numHitsToRetrieve;
            }
        }

        private void restoreWindow() {
            query.setWindow(offset,hits);
        }

        void retrieveRemainingHitsForGroup(DocumentGroup documentGroup) {
            Set<String> oldRestrictList = query.getModel().getRestrict();
            try {
                int numMissingHits = documentGroup.targetNumberOfDocuments - numHits(documentGroup);
                int offset = numHits(documentGroup);

                query.getModel().getRestrict().clear();
                query.getModel().getRestrict().add(documentGroup.documentName);
                query.setWindow(offset, numMissingHits);
                partitionedResult.addHits(retrieveHits());

            } finally {
                restoreWindow();
                query.getModel().getRestrict().clear();
                query.getModel().getRestrict().addAll(oldRestrictList);
            }
        }

        int numHits(DocumentGroup documentGroup) {
            return partitionedResult.numHits(documentGroup.documentName);
        }

        Result createMultipleResultSets() {
            Iterator<Hit> i = initialResult.hits().iterator();
            while (i.hasNext()) {
                i.next();
                i.remove();
            }

            for (DocumentGroup group: parameters.documentGroups) {
                partitionedResult.cropResultSet(group.documentName,group.targetNumberOfDocuments);
            }

            partitionedResult.insertInto(initialResult.hits());
            return initialResult;
        }

        private Result retrieveHits() {
            Result result = execution.search(query);
            // ensure that field sddocname is available
            execution.fill(result); // TODO: Suffices to fill attributes

            if (result.hits().getErrorHit() != null)
                initialResult.hits().getErrorHit().addErrors(
                    result.hits().getErrorHit());


            return result;
        }
    }

    // Assumes that field sddocname is available
    private static class PartitionedResult {

        private Map<String, HitGroup> resultSets = new HashMap<>();

        private List<Hit> otherHits = new ArrayList<>();

        PartitionedResult(List<DocumentGroup> documentGroups,Result result) throws ParameterException {
            for (DocumentGroup group : documentGroups)
                addGroup(group);

            addHits(result, true);
        }

        void addHits(Result result, boolean addOtherHits) {
            Iterator<Hit> i = result.hits().iterator();
            while (i.hasNext()) {
                add(i.next(), addOtherHits);
            }
        }

        void addHits(Result result) {
            addHits(result, false);
        }


        void add(Hit hit, boolean addOtherHits) {
            String documentName = (String)hit.getField(Hit.SDDOCNAME_FIELD);

            if (documentName != null) {
                HitGroup resultSet = resultSets.get(documentName);

                if (resultSet != null) {
                    resultSet.add(hit);
                    return;
                }
            }

            if (addOtherHits) {
                otherHits.add(hit);
            }
        }

        int numHits(String documentName) {
            return resultSets.get(documentName).size();
        }

        void insertInto(HitGroup group) {
            for (Hit hit: otherHits) {
                group.add(hit);
            }

            for (HitGroup hit: resultSets.values() ) {
                hit.copyOrdering(group);
                group.add(hit);
            }
        }

        void cropResultSet(String documentName, int numDocuments) {
            resultSets.get(documentName).trim(0, numDocuments);
        }

        private void addGroup(DocumentGroup group) throws ParameterException {
            final String documentName = group.documentName;
            if ( resultSets.put(group.documentName,
                    new HitGroup(documentName) {
                        /**
                         *
                         */
                        private static final long serialVersionUID = 5732822886080288688L;
                    })
                 != null ) {

                throw new ParameterException("Document name " + group.documentName + "mentioned multiple times");
            }
        }

    }


    //examples:
    //multipleresultsets.numhits=music:10,movies:20
    //multipleresultsets.additionalhitsFactor=0.8
    //multipleresultsets.maxtimesretrieveheterogeneoushits=2
    private static class Parameters {
        Parameters(Query query)
            throws ParameterException {

            readNumHitsSpecification(query);
            readMaxTimesRetrieveHeterogeneousHits(query);
            readAdditionalHitsFactor(query);
        }


        List<DocumentGroup> documentGroups = new ArrayList<>();
        double additionalHitsFactor = 0.8;
        int maxTimesRetrieveHeterogeneousHits = 2;

        private void readAdditionalHitsFactor(Query query)
            throws ParameterException {

            String additionalHitsFactorStr = query.properties().getString(additionalHitsFactorName);

            if (additionalHitsFactorStr == null)
                return;

            try {
                additionalHitsFactor =
                    Double.parseDouble(additionalHitsFactorStr);
            } catch (NumberFormatException e) {
                throw new ParameterException(
                    "Expected floating point number, got '" +
                    additionalHitsFactorStr + "'.");
            }
        }

        private void readMaxTimesRetrieveHeterogeneousHits(Query query) {
            maxTimesRetrieveHeterogeneousHits = query.properties().getInteger(
                maxTimesRetrieveHeterogeneousHitsName,
                maxTimesRetrieveHeterogeneousHits);
        }


        private void readNumHitsSpecification(Query query)
            throws ParameterException {

            //example numHitsSpecification: "music:10,movies:20"
            String numHitsSpecification =
                query.properties().getString(numHits);

            if (numHitsSpecification == null)
                return;

            String[] numHitsForDocumentNames = numHitsSpecification.split(",");

            for (String s:numHitsForDocumentNames) {
                handleDocumentNameWithNumberOfHits(s);
            }

        }

        public String toString() {
            String s = "additionalHitsFactor=" + additionalHitsFactor +
                ", maxTimesRetrieveHeterogeneousHits="
                + maxTimesRetrieveHeterogeneousHits +
                ", numHitsSpecification='";

            for (DocumentGroup group : documentGroups) {
                s += group.documentName + ":" +
                    group.targetNumberOfDocuments + ", ";
            }

            s += "'";

            return s;
        }

        //example input: music:10
        private void handleDocumentNameWithNumberOfHits(String s)
            throws ParameterException {

            String[] documentNameWithNumberOfHits = s.split(":");

            if (documentNameWithNumberOfHits.length != 2) {
                String msg = "Expected a single ':' in '" + s + "'.";

                if (documentNameWithNumberOfHits.length > 2)
                    msg += " Please check for missing commas.";

                throw new ParameterException(msg);
            } else {
                String documentName =
                    documentNameWithNumberOfHits[0].trim();
                try {
                    int numHits = Integer.parseInt(
                        documentNameWithNumberOfHits[1].trim());

                    numRequestedHits(documentName, numHits);
                } catch (NumberFormatException e) {
                    throw new ParameterException(
                        "Excpected an integer but got '" +
                        documentNameWithNumberOfHits[1] + "'");
                }
            }
        }

        private void numRequestedHits(String documentName, int numHits) {
            documentGroups.add(new DocumentGroup(documentName, numHits));
        }

    }

    private static class DocumentGroup {
        String documentName;
        int targetNumberOfDocuments;

        DocumentGroup(String documentName, int targetNumberOfDocuments) {
            this.documentName = documentName;
            this.targetNumberOfDocuments = targetNumberOfDocuments;
        }
    }

    @SuppressWarnings("serial")
    private static class ParameterException extends Exception {
        String msg;

        ParameterException(String msg) {
            this.msg = msg;
        }
    }

}
