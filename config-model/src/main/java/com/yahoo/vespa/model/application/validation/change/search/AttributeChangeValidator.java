// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.IndexSchema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.Dictionary;
import com.yahoo.schema.document.HnswIndexParams;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRestartAction;

import java.time.Instant;
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
    private final ValidationOverrides overrides;
    private final Instant now;

    public AttributeChangeValidator(ClusterSpec.Id id,
                                    AttributeFields currentFields,
                                    IndexSchema currentIndexSchema,
                                    NewDocumentType currentDocType,
                                    AttributeFields nextFields,
                                    IndexSchema nextIndexSchema,
                                    NewDocumentType nextDocType,
                                    ValidationOverrides overrides,
                                    Instant now) {
        this.id = id;
        this.currentFields = currentFields;
        this.currentIndexSchema = currentIndexSchema;
        this.currentDocType = currentDocType;
        this.nextFields = nextFields;
        this.nextIndexSchema = nextIndexSchema;
        this.nextDocType = nextDocType;
        this.overrides = overrides;
        this.now = now;
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

    private static Dictionary.Type extractDictionaryType(Attribute attr) {
        Dictionary dict = attr.getDictionary();
        return dict != null ? dict.getType() : Dictionary.Type.BTREE;
    }

    private static Case extractDictionaryCase(Attribute attr) {
        Dictionary dict = attr.getDictionary();
        return dict != null ? dict.getMatch() : Case.UNCASED;
    }

    private List<VespaConfigChangeAction> validateAttributeSettings() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        for (Attribute next : nextFields.attributes()) {
            Attribute current = currentFields.getAttribute(next.getName());
            if (current != null) {
                validateAttributePredicate(id, current, next, Attribute::isFastSearch, "fast-search", result);
                validateAttributePredicate(id, current, next, Attribute::isFastRank, "fast-rank", result);
                validateAttributePredicate(id, current, next, Attribute::isFastAccess, "fast-access", result);
                validateAttributeProperty(id, current, next, AttributeChangeValidator::extractDictionaryType, "dictionary: btree/hash", result);
                validateAttributeProperty(id, current, next, AttributeChangeValidator::extractDictionaryCase, "dictionary: cased/uncased", result);
                validateAttributePredicate(id, current, next, Attribute::isPaged, "paged", result);
                validatePagedAttributeRemoval(current, next);
                validateAttributeProperty(id, current, next, Attribute::densePostingListThreshold, "dense-posting-list-threshold", result);
                validateAttributePredicate(id, current, next, Attribute::isEnabledOnlyBitVector, "rank: filter", result);
                validateAttributeProperty(id, current, next, Attribute::distanceMetric, "distance-metric", result);
                validateAttributePredicate(id, current, next, AttributeChangeValidator::hasHnswIndex, "indexing: index", result);
                if (hasHnswIndex(current) && hasHnswIndex(next)) {
                    validateAttributeHnswIndexSetting(id, current, next, HnswIndexParams::maxLinksPerNode, "max-links-per-node", result);
                    validateAttributeHnswIndexSetting(id, current, next, HnswIndexParams::neighborsToExploreAtInsert, "neighbors-to-explore-at-insert", result);
                }
            }
        }
        return result;
    }

    private static void validateAttributePredicate(ClusterSpec.Id id,
                                                   Attribute currentAttr, Attribute nextAttr,
                                                   Predicate<Attribute> predicate, String setting,
                                                   List<VespaConfigChangeAction> result) {
        boolean nextValue = predicate.test(nextAttr);
        if (predicate.test(currentAttr) != nextValue) {
            String change = nextValue ? "add" : "remove";
            result.add(new VespaRestartAction(id, new ChangeMessageBuilder(nextAttr.getName()).addChange(change + " attribute '" + setting + "'").build()));
        }
    }

    private static <T> void validateAttributeProperty(ClusterSpec.Id id,
                                                      Attribute current, Attribute next,
                                                      Function<Attribute, T> settingValueProvider, String setting,
                                                      List<VespaConfigChangeAction> result) {
        T currentValue = settingValueProvider.apply(current);
        T nextValue = settingValueProvider.apply(next);
        if ( ! Objects.equals(currentValue, nextValue)) {
            String message = String.format("change property '%s' from '%s' to '%s'", setting, currentValue, nextValue);
            result.add(new VespaRestartAction(id, new ChangeMessageBuilder(next.getName()).addChange(message).build()));
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

    private void validatePagedAttributeRemoval(Attribute current, Attribute next) {
        if (current.isPaged() && !next.isPaged()) {
            overrides.invalid(ValidationId.pagedSettingRemoval,
                              current + "' has setting 'paged' removed. " +
                              "This may cause content nodes to run out of memory as the entire attribute is loaded into memory",
                              now);
        }
    }

}
