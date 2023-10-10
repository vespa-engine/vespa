// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;

import java.io.IOException;
import java.io.Reader;

/**
 * Simple Validation of services.xml for unit tests against RELAX NG schemas.
 *
 * @author hmusum
 */
public class SimpleApplicationValidator {

    public static void checkServices(Reader reader, Version version) throws IOException {
        new SchemaValidators(version).servicesXmlValidator().validate(reader);
    }
}
