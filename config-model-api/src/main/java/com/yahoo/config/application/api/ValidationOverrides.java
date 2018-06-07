// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableList;
import com.yahoo.io.IOUtils;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A set of allows which suppresses specific validations in limited time periods.
 * This is useful to be able to complete a deployment in cases where the application
 * owner believes that the changes to be deployed have acceptable consequences.
 * Immutable.
 *
 * @author bratseth
 */
public class ValidationOverrides {

    public static final ValidationOverrides empty = new ValidationOverrides(ImmutableList.of(), "<deployment version='1.0'/>");

    private final List<Allow> overrides;

    private final String xmlForm;

    /** Creates a validation overrides which does not have an xml form */
    public ValidationOverrides(List<Allow> overrides) {
        this(overrides, null);
    }

    private ValidationOverrides(List<Allow> overrides, String xmlForm) {
        this.overrides = ImmutableList.copyOf(overrides);
        this.xmlForm = xmlForm;
    }

    /** Throws a ValidationException unless this validation is overridden at this time */
    public void invalid(ValidationId validationId, String message, Instant now) {
        if ( ! allows(validationId, now))
            throw new ValidationException(validationId, message);
    }

    public boolean allows(String validationIdString, Instant now) {
        Optional<ValidationId> validationId = ValidationId.from(validationIdString);
        if ( ! validationId.isPresent()) return false; // unknown id -> not allowed
        return allows(validationId.get(), now);
    }

    /** Returns whether the given (assumed invalid) change is allowed by this at the moment */
    public boolean allows(ValidationId validationId, Instant now) {
        for (Allow override : overrides) {
            if (now.plus(Duration.ofDays(30)).isBefore(override.until))
                throw new IllegalArgumentException(override + " is too far in the future: Max 30 days is allowed");
            if (override.allows(validationId, now))
                return true;
        }
        return false;
    }

    public static String toAllowMessage(ValidationId id) {
        return "To allow this add <allow until='yyyy-mm-dd'>" + id + "</allow> to validation-overrides.xml" +
               ", see https://docs.vespa.ai/documentation/reference/validation-overrides.html";
    }

    /** Returns the XML form of this, or null if it was not created by fromXml, nor is empty */
    public String xmlForm() { return xmlForm; }

    /**
     * Returns a ValidationOverrides instance with the content of the given Reader.
     *
     * @param reader the reader containing a validation-overrides XML structure
     * @return a ValidationOverrides from the argument
     * @throws IllegalArgumentException if the validation-allows.xml file exists but is invalid
     */
    public static ValidationOverrides fromXml(Reader reader) {
        try {
            return fromXml(IOUtils.readAll(reader));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read deployment spec", e);
        }
    }

    /**
     * Returns a ValidationOverrides instance with the content of the given XML string.
     * An empty ValidationOverrides is returned if the argument is empty.
     *
     * @param xmlForm the string which optionally contains a validation-overrides XML structure
     * @return a ValidationOverrides from the argument
     * @throws IllegalArgumentException if the validation-allows.xml file exists but is invalid
     */
    public static ValidationOverrides fromXml(String xmlForm) {
        if ( xmlForm.isEmpty()) return ValidationOverrides.empty;

        try {
            // Assume valid structure is ensured by schema validation
            Element root = XML.getDocument(xmlForm).getDocumentElement();
            List<ValidationOverrides.Allow> overrides = new ArrayList<>();
            for (Element allow : XML.getChildren(root, "allow")) {
                Instant until = LocalDate.parse(allow.getAttribute("until"), DateTimeFormatter.ISO_DATE)
                        .atStartOfDay().atZone(ZoneOffset.UTC).toInstant()
                        .plus(Duration.ofDays(1)); // Make the override valid *on* the "until" date
                Optional<ValidationId> validationId = ValidationId.from(XML.getValue(allow));
                if (validationId.isPresent()) // skip unknown ids as they may be valid for other model versions
                    overrides.add(new ValidationOverrides.Allow(validationId.get(), until));
            }
            return new ValidationOverrides(overrides, xmlForm);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("validation-overrides is invalid", e);
        }
    }

    /** A validation override which allows a particular change. Immutable. */
    public static class Allow {

        private final ValidationId validationId;
        private final Instant until;

        public Allow(ValidationId validationId, Instant until) {
            this.validationId = validationId;
            this.until = until;
        }

        public boolean allows(ValidationId validationId, Instant now) {
            return this.validationId.equals(validationId) && now.isBefore(until);
        }

        @Override
        public String toString() { return "allow '" + validationId + "' until " + until; }

    }

    /**
     * A deployment validation exception.
     * Deployment validations can be {@link ValidationOverrides overridden} based on their id.
     * The purpose of this exception is to model that id as a separate field.
     */
    public static class ValidationException extends IllegalArgumentException {

        static final long serialVersionUID = 789984668;

        private final ValidationId validationId;

        private ValidationException(ValidationId validationId, String message) {
            super(message);
            this.validationId = validationId;
        }

        /** Returns the unique id of this validation, which can be used to {@link ValidationOverrides override} it */
        public ValidationId validationId() { return validationId; }

        /** Returns "validationId: message" */
        @Override
        public String getMessage() {
            return validationId + ": " + super.getMessage() + ". " + toAllowMessage(validationId);
        }

    }

}
