// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.IndexSchema;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.HnswIndexParams;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRestartAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Validates the changes between the current and next set of attribute fields in a document database.
 *
 * @author geirst
 */
public class AttributeChangeValidator {

    private final ClusterSpec.Id id;
    private final AttributeFields currentFields;
    private final IndexSchema currentIndexSchema;
    private final NewDocumentType currentDocType;
    private final AttributeFields nextFields;
    private final IndexSchema nextIndexSchema;
    private final NewDocumentType nextDocType;

    public AttributeChangeValidator(ClusterSpec.Id id,
                                    AttributeFields currentFields,
                                    IndexSchema currentIndexSchema,
                                    NewDocumentType currentDocType,
                                    AttributeFields nextFields,
                                    IndexSchema nextIndexSchema,
                                    NewDocumentType nextDocType) {
        this.id = id;
        this.currentFields = currentFields;
        this.currentIndexSchema = currentIndexSchema;
        this.currentDocType = currentDocType;
        this.nextFields = nextFields;
        this.nextIndexSchema = nextIndexSchema;
        this.nextDocType = nextDocType;
    }

    public List<VespaConfigChangeAction> validate() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        result.addAll(validateAddAttributeAspect());
        result.addAll(validateRemoveAttributeAspect());
        result.addAll(validateAttributeSettings());
        return result;
    }

    private List<VespaConfigChangeAction> validateAddAttributeAspect() {
        return nextFields.attributes().stream().
                map(attr -> attr.getName()).
                filter(attrName -> !currentFields.containsAttribute(attrName) &&
                                   currentDocType.containsField(attrName)).
                map(attrName -> new VespaRestartAction(id, new ChangeMessageBuilder(attrName).addChange("add attribute aspect").build())).
                collect(Collectors.toList());
    }

    private List<VespaConfigChangeAction> validateRemoveAttributeAspect() {
        return currentFields.attributes().stream().
                map(attr -> attr.getName()).
                filter(attrName -> !nextFields.containsAttribute(attrName) &&
                                   nextDocType.containsField(attrName) &&
                                   !isIndexField(attrName)).
                map(attrName -> new VespaRestartAction(id, new ChangeMessageBuilder(attrName).addChange("remove attribute aspect").build())).
                collect(Collectors.toList());
    }

    private boolean isIndexField(String fieldName) {
        return currentIndexSchema.containsField(fieldName) && nextIndexSchema.containsField(fieldName);
    }

    private static boolean hasHnswIndex(Attribute attribute) {
        return attribute.hnswIndexParams().isPresent();
    }

    private List<VespaConfigChangeAction> validateAttributeSettings() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        for (Attribute nextAttr : nextFields.attributes()) {
            Attribute currAttr = currentFields.getAttribute(nextAttr.getName());
            if (currAttr != null) {
                validateAttributeSetting(id, currAttr, nextAttr, Attribute::isFastSearch, "fast-search", result);
                validateAttributeSetting(id, currAttr, nextAttr, Attribute::isFastAccess, "fast-access", result);
                validateAttributeSetting(id, currAttr, nextAttr, Attribute::isHuge, "huge", result);
                validateAttributeSetting(id, currAttr, nextAttr, Attribute::densePostingListThreshold, "dense-posting-list-threshold", result);
                validateAttributeSetting(id, currAttr, nextAttr, Attribute::isEnabledOnlyBitVector, "rank: filter", result);
                validateAttributeSetting(id, currAttr, nextAttr, AttributeChangeValidator::hasHnswIndex, "indexing: index", result);
                validateAttributeSetting(id, currAttr, nextAttr, Attribute::distanceMetric, "distance-metric", result);
                if (hasHnswIndex(currAttr) && hasHnswIndex(nextAttr)) {
                    validateAttributeHnswIndexSetting(id, currAttr, nextAttr, HnswIndexParams::maxLinksPerNode, "max-links-per-node", result);
                    validateAttributeHnswIndexSetting(id, currAttr, nextAttr, HnswIndexParams::neighborsToExploreAtInsert, "neighbors-to-explore-at-insert", result);
                }
            }
        }
        return result;
    }

    private static void validateAttributeSetting(ClusterSpec.Id id,
                                                 Attribute currentAttr, Attribute nextAttr,
                                                 Predicate<Attribute> predicate, String setting,
                                                 List<VespaConfigChangeAction> result) {
        boolean nextValue = predicate.test(nextAttr);
        if (predicate.test(currentAttr) != nextValue) {
            String change = nextValue ? "add" : "remove";
            result.add(new VespaRestartAction(id, new ChangeMessageBuilder(nextAttr.getName()).addChange(change + " attribute '" + setting + "'").build()));
        }
    }

    private static <T> void validateAttributeSetting(ClusterSpec.Id id,
                                                     Attribute currentAttr, Attribute nextAttr,
                                                     Function<Attribute, T> settingValueProvider, String setting,
                                                     List<VespaConfigChangeAction> result) {
        T currentValue = settingValueProvider.apply(currentAttr);
        T nextValue = settingValueProvider.apply(nextAttr);
        if ( ! Objects.equals(currentValue, nextValue)) {
            String message = String.format("change property '%s' from '%s' to '%s'", setting, currentValue, nextValue);
            result.add(new VespaRestartAction(id, new ChangeMessageBuilder(nextAttr.getName()).addChange(message).build()));
        }
    }

    private static <T> void validateAttributeHnswIndexSetting(ClusterSpec.Id id,
                                                              Attribute currentAttr, Attribute nextAttr,
                                                              Function<HnswIndexParams, T> settingValueProvider,
                                                              String setting,
                                                              List<VespaConfigChangeAction> result) {
        T currentValue = settingValueProvider.apply(currentAttr.hnswIndexParams().get());
        T nextValue = settingValueProvider.apply(nextAttr.hnswIndexParams().get());
        if (!Objects.equals(currentValue, nextValue)) {
            String message = String.format("change hnsw index property '%s' from '%s' to '%s'", setting, currentValue, nextValue);
            result.add(new VespaRestartAction(id, new ChangeMessageBuilder(nextAttr.getName()).addChange(message).build()));
        }
    }

}
