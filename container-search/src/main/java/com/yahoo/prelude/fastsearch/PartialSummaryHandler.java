// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.processing.IllegalInputException;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PartialSummaryHandler is a helper class to help handling of fill()
 * requests which only cover some of the possible summary fields.
 *
 * Usage:
 * 1) construct from DocumentDatabase or DocsumDefinitionSet
 * 2) call wantToFill to specify intent
 * 3) use askForSummary and askForFields for making the backend request
 * 4) use needFill to see if the Hit actually needs filling
 * 5) use effectiveDocsumDef for decoding the backend response
 * 6) use markFilled to mark the Hit with what actually got filled
 *
 * @author arnej
 */
public class PartialSummaryHandler {
    public static final String DEFAULT_CLASS = "default";
    public static final String ALL_FIELDS_CLASS = "default"; // not really true, but best we can do for now
    public static final String PRESENTATION = "[presentation]";

    private static final Logger log = Logger.getLogger(PartialSummaryHandler.class.getName());

    /** resolve summary class to use when none provided */
    public static String resolveSummaryClass(Result result) {
        return PRESENTATION;
    }

    private final DocsumDefinitionSet docsumDefinitions;
    private DocsumDefinition effectiveDocsumDef = null;
    private final Map<String, Set<String>> knownSummaryClasses = new HashMap<>();
    private String summaryFromQuery = null;
    private String summaryRequestedInFill = null;
    private String askForSummary = null;
    private Set<String> fillMarkers = new HashSet<>();
    private Set<String> fieldsFromQuery = null;
    private Set<String> resultHasFilled = null;
    private Set<String> askForFields = null;

    public PartialSummaryHandler(DocumentDatabase docDb) {
        this(docDb.getDocsumDefinitionSet());
    }
    public PartialSummaryHandler(DocsumDefinitionSet docsumDefinitionSet) {
        this.docsumDefinitions = docsumDefinitionSet;
    }

    // for unit testing:
    public PartialSummaryHandler(Map<String, Set<String>> knownSummaryClasses) {
        this.docsumDefinitions = null;
        this.knownSummaryClasses.putAll(knownSummaryClasses);
    }

    public void wantToFill(Result result, String summaryClass) {
        this.summaryRequestedInFill = summaryClass;
        analyzeResult(result);
        analyzeQuery(result.getQuery());
        // NOTE: ordering here is important, there are dependencies between these steps:
        computeAskForFields();
        computeAskForSum();
        computeFillMarker();
        computeEffectiveDocsumDef();
    }

    // for streaming
    public void wantToFill(Query query) {
        this.summaryRequestedInFill = PRESENTATION;
        this.resultHasFilled = Set.of();
        // NOTE: ordering here is important, there are dependencies between these steps:
        analyzeQuery(query);
        computeAskForFields();
        computeAskForSum();
        computeFillMarker();
        computeEffectiveDocsumDef();
    }

    // which summary class we should ask the backend for:
    public String askForSummary() { return askForSummary; }

    // if requesting a specific set of fields, which ones, otherwise null
    public Set<String> askForFields() { return askForFields; }

    // does this Hit need to be filled
    public boolean needFill(Hit hit) {
        return needsMoreFill(hit.getFilled());
    }

    // is the entire Result already filled as needed
    public boolean resultAlreadyFilled() {
        return ! needsMoreFill(resultHasFilled);
    }

    // what is the currently-effective DocsumDefinition
    public DocsumDefinition effectiveDocsumDef() {
        if (effectiveDocsumDef == null) {
            if (docsumDefinitions == null) {
                throw new IllegalStateException("missing docsumDefinitions");
            }
            throw new IllegalStateException("docsumDefinition missing for summary=" + askForSummary);
        }
        return effectiveDocsumDef;
    }

    // mark the Hit with how it actually got filled
    public void markFilled(Hit hit) {
        for (String marker : fillMarkers) {
            hit.setFilled(marker);
        }
    }

    private void analyzeResult(Result result) {
        this.resultHasFilled = result.hits().getFilled();
    }

    private void analyzeQuery(Query query) {
        var presentation = query.getPresentation();
        this.summaryFromQuery = presentation.getSummary();
        this.fieldsFromQuery = presentation.getSummaryFields();
    }

    private static boolean isFieldListRequest(String summaryClass) {
        return summaryClass != null && summaryClass.startsWith("[f:");
    }

    private static boolean isDefaultRequest(String summaryClass) {
        return summaryClass == null || summaryClass.equals(DEFAULT_CLASS);
    }

    private static boolean isPresentationRequest(String summaryClass) {
        return summaryClass != null && summaryClass.equals(PRESENTATION);
    }

    private void computeAskForSum() {
        this.askForSummary = summaryRequestedInFill;
        if (askForSummary == null || askForSummary.equals(PRESENTATION)) {
            askForSummary = summaryFromQuery;
        }
        if (askForSummary == null) {
            askForSummary = DEFAULT_CLASS;
        }
        if (askForFields != null) {
            askForSummary = ALL_FIELDS_CLASS;
        }
    }

    private void computeAskForFields() {
        this.askForFields = null;
        if (isFieldListRequest(summaryRequestedInFill)) {
            var fieldSet = parseFieldList(summaryRequestedInFill);
            if (! fieldSet.isEmpty()) {
                this.askForFields = fieldSet;
            }
        } else if (isPresentationRequest(summaryRequestedInFill)) {
            if (! fieldsFromQuery.isEmpty()) {
                if (summaryFromQuery == null) {
                    askForFields = fieldsFromQuery;
                } else {
                    var fieldsFromClass = getFieldsForClass(summaryFromQuery);
                    if (! fieldsFromClass.containsAll(fieldsFromQuery)) {
                        askForFields = new HashSet<String>();
                        askForFields.addAll(fieldsFromQuery);
                        askForFields.addAll(fieldsFromClass);
                    }
                }
            }
        } else if (summaryRequestedInFill != null && summaryRequestedInFill.startsWith("[")) {
            throw new IllegalArgumentException("fill(" + summaryRequestedInFill + ") is not valid");
        } else if (isDefaultRequest(summaryRequestedInFill)) {
            if (! fieldsFromQuery.isEmpty()) {
                this.askForFields = fieldsFromQuery;
            }
        }
        if (askForFields != null) {
            var available = fieldsAlreadyFilled(resultHasFilled);
            available.addAll(getFieldsForClass(ALL_FIELDS_CLASS));
            askForFields.retainAll(available);
            if (askForFields.isEmpty()) {
                askForFields = null;
            }
        }
    }

    private void computeFillMarker() {
        if (isPresentationRequest(summaryRequestedInFill)) {
                fillMarkers.add(summaryRequestedInFill);
                if (askForFields != null) {
                    fillMarkers.add(syntheticName(askForFields));
                } else {
                    fillMarkers.add(summaryFromQuery);
                }
        } else if (askForFields != null) {
            fillMarkers.add(syntheticName(askForFields));
        } else {
            fillMarkers.add(summaryRequestedInFill);
            fillMarkers.add(askForSummary);
        }
    }

    private static String syntheticName(Set<String> summaryFields) {
        // ensure deterministic ordering:
        var sorted = new TreeSet<String>(summaryFields);
        var buf = new StringBuilder();
        buf.append("[f:");
        for (String field : sorted) {
            buf.append(buf.length() == 3 ? "" : ",");
            buf.append(field);
        }
        buf.append(']');
        return buf.toString();
    }

    public static String quotedSummaryClassName(String summaryClass, Set<String> summaryFields) {
        if (isDefaultRequest(summaryClass) && ! summaryFields.isEmpty()) {
            return syntheticName(summaryFields);
        } else if (summaryClass == null) {
            return "[null]";
        } else {
            return "'" + summaryClass + "'";
        }
    }

    public static String enoughFields(String summaryClass, Result result) {
        var summaryFields = result.getQuery().getPresentation().getSummaryFields();
        if (isDefaultRequest(summaryClass) && ! summaryFields.isEmpty()) {
            // it's enough to have the string we would use as fillMarker
            return syntheticName(summaryFields);
        } else {
            // this is 'always' enough:
            return ALL_FIELDS_CLASS;
        }
    }

    public void validateSummaryClass(String summaryClass, Query query) {
        if (isFieldListRequest(summaryClass)) {
            var fieldSet = parseFieldList(summaryClass);
            if (fieldSet.isEmpty()) {
                throw new IllegalArgumentException("invalid fill() with empty fieldset=" + summaryClass);
            }
        } else if (isPresentationRequest(summaryClass)) {
            String wantClass = query.getPresentation().getSummary();
            if (! ensureKnownClass(wantClass)) {
                throw new IllegalInputException("invalid presentation.summary=" + wantClass);
            }
        } else if (summaryClass != null) {
            if (! ensureKnownClass(summaryClass)) {
                throw new IllegalArgumentException("invalid fill() with summaryClass=" + summaryClass);
            }
        }
    }

    private void computeEffectiveDocsumDef() {
        if ((docsumDefinitions != null) && docsumDefinitions.hasDocsum(askForSummary)) {
            effectiveDocsumDef = docsumDefinitions.getDocsum(askForSummary);
            if (askForFields != null) {
                effectiveDocsumDef = new DocsumDefinition("<unnamed>", effectiveDocsumDef, askForFields);
            }
        }
    }

    private Set<String> parseFieldList(String fieldList) {
        if (fieldList.startsWith("[f:") && fieldList.endsWith("]")) {
            String content = fieldList.substring(3, fieldList.length() - 1);
            String[] parts = content.split(",");
            return new TreeSet<>(Arrays.asList(parts));
        }
        return Set.of();
    }

    private Set<String> getFieldsForClass(String wantClass) {
        if (! ensureKnownClass(wantClass)) {
            throw new IllegalArgumentException("unknown summary class: " + wantClass);
        }
        return knownSummaryClasses.get(wantClass);
    }

    private boolean ensureKnownClass(String wantClass) {
        if (knownSummaryClasses.containsKey(wantClass))
            return true;
        if (isFieldListRequest(wantClass)) {
            var fieldSet = parseFieldList(wantClass);
            if (fieldSet.isEmpty())
                return false;
            knownSummaryClasses.put(wantClass, fieldSet);
            return true;
        }
        if (docsumDefinitions != null && docsumDefinitions.hasDocsum(wantClass)) {
            var docsumDef = docsumDefinitions.getDocsum(wantClass);
            var fields = docsumDef.fields().keySet();
            var fieldSet = Set.copyOf(fields);
            knownSummaryClasses.put(wantClass, fieldSet);
            return true;
        }
        return false;
    }

    private Set<String> fieldsAlreadyFilled(Set<String> alreadyFilled) {
        var gotFields = new HashSet<String>();
        for (var hasSummary : alreadyFilled) {
            if (hasSummary == PRESENTATION) continue;
            var hasSome = getFieldsForClass(hasSummary);
            for (String field : hasSome) {
                gotFields.add(field);
            }
        }
        return gotFields;
    }

    private boolean needsMoreFill(Set<String> alreadyFilled) {
        // unfillable?
        if (alreadyFilled == null) return false;

        // do we already have the entire thing?
        if (alreadyFilled.contains(askForSummary)) return false;
        // do we already have the entire subset?
        for (var marker : fillMarkers) {
            if (alreadyFilled.contains(marker)) return false;
        }

        // no, see what we have got:
        var gotFields = fieldsAlreadyFilled(alreadyFilled);

        // check if got covers all that we want:
        var wantFields = (askForFields == null ? getFieldsForClass(askForSummary) : askForFields);
        for (String field : wantFields) {
            if (! gotFields.contains(field)) {
                // need this field
                return true;
            }
        }
        // we have everything needed
        return false;
    }

}
