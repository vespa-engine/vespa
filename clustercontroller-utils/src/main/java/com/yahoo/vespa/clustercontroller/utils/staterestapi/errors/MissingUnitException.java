// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

import java.util.List;

public class MissingUnitException extends StateRestApiException {

    private static String createMessage(List<String> path, int level) {
        StringBuilder sb = new StringBuilder();
        sb.append("No such resource '");
        for (int i=0; i<=level; ++i) {
            if (i != 0) sb.append('/');
            sb.append(path.get(i));
        }
        return sb.append("'.").toString();
    }

    public MissingUnitException(List<String> path, int level) {
        super(createMessage(path, level));
        setHtmlCode(404);
        setHtmlStatus(getMessage());
    }

}
