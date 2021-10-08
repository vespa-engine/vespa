// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public class InvalidOptionValueException extends StateRestApiException {

    public InvalidOptionValueException(String option, String value, String description) {
        super("Option '" + option + "' have invalid value '" + value + "': " + description);
        setHtmlCode(400);
        setHtmlStatus("Option '" + option + "' have invalid value '" + value + "'");
    }

}
