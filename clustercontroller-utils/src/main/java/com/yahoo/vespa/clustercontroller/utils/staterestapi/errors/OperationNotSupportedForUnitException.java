// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

import java.util.Arrays;
import java.util.List;

public class OperationNotSupportedForUnitException extends StateRestApiException {

    private static String createMessage(List<String> path, String description) {
        return new StringBuilder()
                .append(Arrays.toString(path.toArray())).append(": ").append(description)
                .toString();
    }

    public OperationNotSupportedForUnitException(List<String> path, String description) {
        super(createMessage(path, description));
        setHtmlCode(405);
        setHtmlStatus("Operation not supported for resource");
    }

}
