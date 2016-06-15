// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.xml;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.application.validation.ValidationId;
import com.yahoo.vespa.model.application.validation.ValidationOverrides;
import org.w3c.dom.Element;

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
 * Reader of the validation-allows.xml file in application packages.
 *
 * @author bratseth
 */
public class ValidationOverridesXMLReader {

    /**
     * Returns a ValidationOverrides instance with the content of the given Reader.
     * An empty ValidationOverrides is returned if the argument is empty.
     *
     * @param reader the reader which optionally contains a validation-overrides XML structure
     * @param now the instant to use as "now", settable for unit testing
     * @return a ValidationOverrides from the argument
     * @throws IllegalArgumentException if the validation-allows.xml file exists but is invalid
     */
    public ValidationOverrides read(Optional<Reader> reader, Instant now) {
        if ( ! reader.isPresent()) return ValidationOverrides.empty();

        try {
            // Assume valid structure is ensured by schema validation
            Element root = XML.getDocument(reader.get()).getDocumentElement();
            List<ValidationOverrides.Allow> overrides = new ArrayList<>();
            for (Element allow : XML.getChildren(root, "allow")) {
                Instant until = LocalDate.parse(allow.getAttribute("until"), DateTimeFormatter.ISO_DATE)
                                         .atStartOfDay().atZone(ZoneOffset.UTC).toInstant()
                                         .plus(Duration.ofDays(1)); // Make the override valid *on* the "until" date
                Optional<ValidationId> validationId = ValidationId.from(XML.getValue(allow));
                if (validationId.isPresent()) // skip unknonw ids as they may be valid for other model versions
                    overrides.add(new ValidationOverrides.Allow(validationId.get(), until));
            }
            return new ValidationOverrides(overrides, now);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("validation-overrides is invalid", e);
        }
    }

}
