// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public class InvalidOptionValueException extends StateRestApiException {

    public InvalidOptionValueException(String option, String value, String description) {
        super("Option '" + option + "' have invalid value '" + value + "': " + description,
              400,
              "Option '" + option + "' have invalid value '" + value + "'");

    }

}
