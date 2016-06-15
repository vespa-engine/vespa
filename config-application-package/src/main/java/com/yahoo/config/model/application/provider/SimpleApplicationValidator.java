// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import java.io.IOException;
import java.io.Reader;

/**
 * Simple Validation of services.xml for unit tests against RELAX NG schemas.
 *
 * @author <a href="mailto:musum@yahoo-inc.com">Harald Musum</a>
 */
public class SimpleApplicationValidator {

    public static void checkServices(Reader reader) throws IOException {
        SchemaValidator.createTestValidatorServices().validate(reader);
    }
}
