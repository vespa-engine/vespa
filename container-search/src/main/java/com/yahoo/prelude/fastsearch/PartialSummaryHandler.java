// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;

import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * PartialSummaryHandler is a helper class to help handling of fill()
 * requests which only cover some of the possible summary fields.
 *
 * Usage:
 * 1) construct from DocumentDatabase (it needs the DocsumDefinitionSet)
 * 2) call wantToFill to specify intent
 * 3) use askForSummary and askForFields for making the backend request
 * 4) use needFill to see if the Hit actually needs filling
 * 5) use effectiveDocsumDef for decoding the backend response
 * 6) use markFilled to mark the Hit with what actually got filled
 *
 * @author arnej
 */
public class PartialSummaryHandler {

    /** resolve summary class to use when none provided */
    public static String resolveSummaryClass(Result result) {
        // TODO: consider using "[presentation]" instead
        return result.getQuery().getPresentation().getSummary();
    }

    private final DocsumDefinitionSet docsumDefinitions;
    private DocsumDefinition effectiveDocsumDef = null;
    private final Map<String, Set<String>> knownSummaryClasses = new HashMap<>();
    private String summaryFromQuery = null;
    private String summaryRequestedInFill = null;
    private String askForSummary = null;
    private String fillMarker = null;
    private Set<String> fieldsFromQuery = null;
    private Set<String> resultHasFilled = null;
    private Set<String> askForFields = null;

    public PartialSummaryHandler(DocumentDatabase docDb) {
        this.docsumDefinitions = docDb.getDocsumDefinitionSet();
    }

    // for unit testing:
    public PartialSummaryHandler(Map<String, Set<String>> knownSummaryClasses) {
        this.docsumDefinitions = null;
        this.knownSummaryClasses.putAll(knownSummaryClasses);
    }

    public void wantToFill(Result result, String summaryClass) {
        this.summaryRequestedInFill = summaryClass;
        analyzeResult(result);
        // NOTE: ordering here is important, there are dependencies between these steps:
        computeAskForSum();
        computeAskForFields();
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
        hit.setFilled(fillMarker);
    }

    private void analyzeResult(Result result) {
        this.resultHasFilled = result.hits().getFilled();
        var presentation = result.getQuery().getPresentation();
        // TODO: summaryFromQuery is currently not used
        this.summaryFromQuery = presentation.getSummary();
        this.fieldsFromQuery = presentation.getSummaryFields();
    }

    private static boolean isFieldListRequest(String summaryClass) {
        return summaryClass != null && summaryClass.startsWith("[f:");
    }

    private static boolean isDefaultRequest(String summaryClass) {
        return summaryClass == null || summaryClass.equals("default");
    }

    private void computeAskForSum() {
        this.askForSummary = summaryRequestedInFill;
        if (isFieldListRequest(summaryRequestedInFill)) {
            this.askForSummary = "default";
        }
    }

    private void computeAskForFields() {
        this.askForFields = null;
        if (isFieldListRequest(summaryRequestedInFill)) {
            var fieldSet = parseFieldList(summaryRequestedInFill);
            if (! fieldSet.isEmpty()) {
                this.askForFields = fieldSet;
            }
        } else if (isDefaultRequest(summaryRequestedInFill)) {
            if (! fieldsFromQuery.isEmpty()) {
                this.askForFields = fieldsFromQuery;
            }
        }
    }

    private void computeFillMarker() {
        this.fillMarker = askForSummary;
        if (askForFields != null) {
            fillMarker = syntheticName(askForFields);
        }
    }

    private static String syntheticName(Set<String> summaryFields) {
        var buf = new StringBuilder();
        for (String field : summaryFields) {
            buf.append(buf.length() == 0 ? "[f:" : ",");
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
            return summaryClass;
        }
    }

    private void computeEffectiveDocsumDef() {
        if ((docsumDefinitions != null) && docsumDefinitions.hasDocsum(askForSummary)) {
            effectiveDocsumDef = docsumDefinitions.getDocsum(askForSummary);
            if (askForFields != null) {
                effectiveDocsumDef = new DocsumDefinition(fillMarker, effectiveDocsumDef, askForFields);
            }
        }
    }

    private Set<String> parseFieldList(String fieldList) {
        if (fieldList.startsWith("[f:") && fieldList.endsWith("]")) {
            String content = fieldList.substring(3, fieldList.length() - 1);
            String[] parts = content.split(",");
            return Set.copyOf(Arrays.asList(parts));
        }
        return Set.of();
    }

    private Set<String> getFieldsForClass(String wantClass) {
        if (! knownSummaryClasses.containsKey(wantClass)) {
            if (docsumDefinitions != null && docsumDefinitions.hasDocsum(wantClass)) {
                var docsumDef = docsumDefinitions.getDocsum(wantClass);
                var fields = docsumDef.fields().keySet();
                var fieldSet = Set.copyOf(fields);
                knownSummaryClasses.put(wantClass, fieldSet);
            }
            else if (isFieldListRequest(wantClass)) {
                var fieldSet = parseFieldList(wantClass);
                knownSummaryClasses.put(wantClass, fieldSet);
            }
        }
        var set = knownSummaryClasses.get(wantClass);
        if (set != null) {
            return set;
        } else {
            return Set.of();
        }
    }

    private boolean needsMoreFill(Set<String> alreadyFilled) {
        // unfillable?
        if (alreadyFilled == null) return false;

        // do we already have the entire thing?
        if (alreadyFilled.contains(askForSummary)) return false;
        // do we already have the entire subset?
        if (alreadyFilled.contains(fillMarker)) return false;

        // no, see what we have got:
        var gotFields = new HashSet<String>();
        for (var hasSummary : alreadyFilled) {
            var hasSome = getFieldsForClass(hasSummary);
            for (String field : hasSome) {
                gotFields.add(field);
            }
        }

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
