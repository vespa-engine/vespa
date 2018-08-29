// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import java.util.logging.Level;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes;

/**
 * Makes implicitly defined summaries into explicit summaries
 *
 * @author bratseth
 */
public class ImplicitSummaries extends Processor {

    public ImplicitSummaries(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        DocumentSummary defaultSummary = search.getSummary("default");
        if (defaultSummary == null) {
            defaultSummary = new DocumentSummary("default");
            search.addSummary(defaultSummary);
        }

        for (SDField field : search.allConcreteFields()) {
            collectSummaries(field, search, validate);
        }

        for (DocumentSummary documentSummary : search.getSummaries().values()) {
            documentSummary.purgeImplicits();
        }
    }

    private void addSummaryFieldSources(SummaryField summaryField, SDField sdField) {
        sdField.addSummaryFieldSources(summaryField);
    }

    private void collectSummaries(SDField field ,Search search, boolean validate) {
        SummaryField addedSummaryField=null;

        // Implicit
        String fieldName = field.getName();
        SummaryField fieldSummaryField = field.getSummaryField(fieldName);
        if (fieldSummaryField == null && field.doesSummarying()) {
            fieldSummaryField = new SummaryField(fieldName, field.getDataType());
            fieldSummaryField.setImplicit(true);
            addSummaryFieldSources(fieldSummaryField, field);
            fieldSummaryField.addDestination("default");
            field.addSummaryField(fieldSummaryField);
            addedSummaryField = fieldSummaryField;
        }
        if (fieldSummaryField != null) {
            for (String dest : fieldSummaryField.getDestinations()) {
                DocumentSummary summary = search.getSummary(dest);
                if (summary != null) {
                    summary.add(fieldSummaryField);
                }
            }
        }

        // Attribute prefetch
        for (Attribute attribute : field.getAttributes().values()) {
            if (attribute.getName().equals(fieldName)) {
                if (addedSummaryField != null) {
                    addedSummaryField.setTransform(SummaryTransform.ATTRIBUTE);
                }
                if (attribute.isPrefetch()) {
                    addPrefetchAttribute(attribute, field, search);
                }
            }
        }

        if (addedSummaryField != null && isComplexFieldWithOnlyStructFieldAttributes(field)) {
            addedSummaryField.setTransform(SummaryTransform.ATTRIBUTECOMBINER);
        }

        // Position attributes
        if (field.doesSummarying()) {
            for (Attribute attribute : field.getAttributes().values()) {
                if ( ! attribute.isPosition()) continue;
                DocumentSummary attributePrefetchSummary = getOrCreateAttributePrefetchSummary(search);
                attributePrefetchSummary.add(field.getSummaryField(PositionDataType.getDistanceSummaryFieldName(fieldName)));
                attributePrefetchSummary.add(field.getSummaryField(PositionDataType.getPositionSummaryFieldName(fieldName)));
            }
        }

        // Explicits
        for (SummaryField summaryField : field.getSummaryFields()) {
            // Make sure we fetch from attribute here too
            Attribute attribute = field.getAttributes().get(fieldName);
            if (attribute != null && summaryField.getTransform() == SummaryTransform.NONE) {
                summaryField.setTransform(SummaryTransform.ATTRIBUTE);
            }

            if (isValid(summaryField, search, validate)) {
                addToDestinations(summaryField, search);
            }
        }

    }

    private DocumentSummary getOrCreateAttributePrefetchSummary(Search search) {
        DocumentSummary summary = search.getSummary("attributeprefetch");
        if (summary == null) {
            summary = new DocumentSummary("attributeprefetch");
            search.addSummary(summary);
        }
        return summary;
    }


    private void addPrefetchAttribute(Attribute attribute,SDField field,Search search) {
        if (attribute.getPrefetchValue() == null) { // Prefetch by default - unless any summary makes this dynamic
            // Check if there is an implicit dynamic definition
            SummaryField fieldSummaryField = field.getSummaryField(attribute.getName());
            if (fieldSummaryField != null && fieldSummaryField.getTransform().isDynamic()) return;

            // Check if an explicit class makes it dynamic (first is enough, as all must be the same, checked later)
            SummaryField explicitSummaryField = search.getExplicitSummaryField(attribute.getName());
            if (explicitSummaryField != null && explicitSummaryField.getTransform().isDynamic()) return;
        }

        DocumentSummary summary = getOrCreateAttributePrefetchSummary(search);
        SummaryField attributeSummaryField = new SummaryField(attribute.getName(), attribute.getDataType());
        attributeSummaryField.addSource(attribute.getName());
        attributeSummaryField.addDestination("attributeprefetch");
        attributeSummaryField.setTransform(SummaryTransform.ATTRIBUTE);
        summary.add(attributeSummaryField);
    }

    // Returns whether this is valid. Warns if invalid and ignorable. Throws if not ignorable.
    private boolean isValid(SummaryField summaryField, Search search, boolean validate) {
        if (summaryField.getTransform() == SummaryTransform.DISTANCE ||
            summaryField.getTransform() == SummaryTransform.POSITIONS)
        {
            int sourceCount = summaryField.getSourceCount();
            if (validate && sourceCount != 1) {
                throw newProcessException(search.getName(), summaryField.getName(),
                                          "Expected 1 source field, got " + sourceCount + ".");
            }
            String sourceName = summaryField.getSingleSource();
            if (validate && search.getAttribute(sourceName) == null) {
                throw newProcessException(search.getName(), summaryField.getName(),
                                          "Summary source attribute '" + sourceName + "' not found.");
            }
            return true;
        }

        String fieldName = summaryField.getSourceField();
        SDField sourceField = search.getConcreteField(fieldName);
        if (validate && sourceField == null) {
            throw newProcessException(search, summaryField, "Source field '" + fieldName + "' does not exist.");
        }
        if (! sourceField.doesSummarying() &&
            ! summaryField.getTransform().equals(SummaryTransform.ATTRIBUTE) &&
            ! summaryField.getTransform().equals(SummaryTransform.GEOPOS))
        {
            // Summary transform attribute may indicate that the ilscript was rewritten to remove summary
            // by another search that uses this same field in inheritance.
            deployLogger.log(Level.WARNING, "Ignoring " + summaryField + ": " + sourceField +
                                           " is not creating a summary value in its indexing statement");
            return false;
        }

        if (summaryField.getTransform().isDynamic()
            && summaryField.getName().equals(sourceField.getName())
            && sourceField.doesAttributing()) {
            Attribute attribute = sourceField.getAttributes().get(sourceField.getName());
            if (attribute != null) {
                String destinations = "document summary 'default'";
                if (summaryField.getDestinations().size()  >0) {
                    destinations = "document summaries " + summaryField.getDestinations();
                }
                deployLogger.log(Level.WARNING,
                                 "Will fetch the disk summary value of " + sourceField + " in " + destinations +
                                 " since this summary field uses a dynamic summary value (snippet/bolding): Dynamic summaries and bolding " +
                                 "is not supported with summary values fetched from in-memory attributes yet. If you want to see partial updates " +
                                 "to this attribute, remove any bolding and dynamic snippeting from this field");
                // Note: The dynamic setting has already overridden the attribute map setting,
                // so we do not need to actually do attribute.setSummary(false) here
                // Also, we can not do this, since it makes it impossible to fetch this attribute
                // in another summary
            }
        }

        return true;
    }

    private void addToDestinations(SummaryField summaryField,Search search) {
        if (summaryField.getDestinations().size() == 0) {
            addToDestination("default", summaryField, search);
        }
        else {
            for (String destinationName : summaryField.getDestinations())
                addToDestination(destinationName, summaryField, search);
        }
    }

    private void addToDestination(String destinationName, SummaryField summaryField,Search search) {
        DocumentSummary destination = search.getSummary(destinationName);
        if (destination == null) {
            destination = new DocumentSummary(destinationName);
            search.addSummary(destination);
            destination.add(summaryField);
        }
        else {
            SummaryField existingField= destination.getSummaryField(summaryField.getName());
            SummaryField merged = summaryField.mergeWith(existingField);
            destination.add(merged);
        }
    }

}
