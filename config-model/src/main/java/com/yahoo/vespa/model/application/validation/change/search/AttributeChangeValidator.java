// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.IndexSchema;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRefeedAction;
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

    private final AttributeFields currentFields;
    private final IndexSchema currentIndexSchema;
    private final NewDocumentType currentDocType;
    private final AttributeFields nextFields;
    private final IndexSchema nextIndexSchema;
    private final NewDocumentType nextDocType;

    public AttributeChangeValidator(AttributeFields currentFields,
                                    IndexSchema currentIndexSchema,
                                    NewDocumentType currentDocType,
                                    AttributeFields nextFields,
                                    IndexSchema nextIndexSchema,
                                    NewDocumentType nextDocType) {
        this.currentFields = currentFields;
        this.currentIndexSchema = currentIndexSchema;
        this.currentDocType = currentDocType;
        this.nextFields = nextFields;
        this.nextIndexSchema = nextIndexSchema;
        this.nextDocType = nextDocType;
    }

    public List<VespaConfigChangeAction> validate(ValidationOverrides overrides, Instant now) {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        result.addAll(validateAddAttributeAspect());
        result.addAll(validateRemoveAttributeAspect());
        result.addAll(validateAttributeSettings());
        result.addAll(validateTensorTypes(overrides, now));
        return result;
    }

    private List<VespaConfigChangeAction> validateAddAttributeAspect() {
        return nextFields.attributes().stream().
                map(attr -> attr.getName()).
                filter(attrName -> !currentFields.containsAttribute(attrName) &&
                        currentDocType.containsField(attrName)).
                map(attrName -> new VespaRestartAction(new ChangeMessageBuilder(attrName).
                        addChange("add attribute aspect").build())).
                collect(Collectors.toList());
    }

    private List<VespaConfigChangeAction> validateRemoveAttributeAspect() {
        return currentFields.attributes().stream().
                map(attr -> attr.getName()).
                filter(attrName -> !nextFields.containsAttribute(attrName) &&
                        nextDocType.containsField(attrName) &&
                        !isIndexField(attrName)).
                map(attrName -> new VespaRestartAction(new ChangeMessageBuilder(attrName).
                        addChange("remove attribute aspect").build())).
                collect(Collectors.toList());
    }

    private boolean isIndexField(String fieldName) {
        return currentIndexSchema.containsField(fieldName) && nextIndexSchema.containsField(fieldName);
    }

    private List<VespaConfigChangeAction> validateAttributeSettings() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        for (Attribute nextAttr : nextFields.attributes()) {
            Attribute currAttr = currentFields.getAttribute(nextAttr.getName());
            if (currAttr != null) {
                validateAttributeSetting(currAttr, nextAttr, Attribute::isFastSearch, "fast-search", result);
                validateAttributeSetting(currAttr, nextAttr, Attribute::isFastAccess, "fast-access", result);
                validateAttributeSetting(currAttr, nextAttr, Attribute::isHuge, "huge", result);
                validateAttributeSetting(currAttr, nextAttr, Attribute::densePostingListThreshold, "dense-posting-list-threshold", result);
            }
        }
        return result;
    }

    private List<VespaConfigChangeAction> validateTensorTypes(final ValidationOverrides overrides, Instant now) {
        final List<VespaConfigChangeAction> result = new ArrayList<>();

        for (final Attribute nextAttr : nextFields.attributes()) {
            final Attribute currentAttr = currentFields.getAttribute(nextAttr.getName());

            if (currentAttr != null && currentAttr.tensorType().isPresent()) {
                // If the tensor attribute is not present on the new attribute, it means that the data type of the attribute
                // has been changed. This is already handled by DocumentTypeChangeValidator, so we can ignore it here
                if (!nextAttr.tensorType().isPresent()) {
                    continue;
                }

                // Tensor attribute has changed type
                if (!nextAttr.tensorType().get().equals(currentAttr.tensorType().get())) {
                    result.add(createTensorTypeChangedRefeedAction(currentAttr, nextAttr, overrides, now));
                }
            }
        }

        return result;
    }

    private static VespaRefeedAction createTensorTypeChangedRefeedAction(Attribute currentAttr, Attribute nextAttr, ValidationOverrides overrides, Instant now) {
        return VespaRefeedAction.of(
                "tensor-type-change",
                overrides,
                new ChangeMessageBuilder(nextAttr.getName())
                        .addChange(
                                "tensor type",
                                currentAttr.tensorType().get().toString(),
                                nextAttr.tensorType().get().toString()).build(), now);
    }

    private static void validateAttributeSetting(Attribute currentAttr, Attribute nextAttr,
                                                 Predicate<Attribute> predicate, String setting,
                                                 List<VespaConfigChangeAction> result) {
        final boolean nextValue = predicate.test(nextAttr);
        if (predicate.test(currentAttr) != nextValue) {
            String change = nextValue ? "add" : "remove";
            result.add(new VespaRestartAction(new ChangeMessageBuilder(nextAttr.getName()).
                    addChange(change + " attribute '" + setting + "'").build()));
        }
    }

    private static <T> void validateAttributeSetting(Attribute currentAttr, Attribute nextAttr,
                                                     Function<Attribute, T> settingValueProvider, String setting,
                                                     List<VespaConfigChangeAction> result) {
        T currentValue = settingValueProvider.apply(currentAttr);
        T nextValue = settingValueProvider.apply(nextAttr);
        if ( ! Objects.equals(currentValue, nextValue)) {
            String message = String.format("change property '%s' from '%s' to '%s'", setting, currentValue, nextValue);
            result.add(new VespaRestartAction(new ChangeMessageBuilder(nextAttr.getName()).addChange(message).build()));
        }
    }

}
