// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.util.List;

/**
 * Validates routing
 */
public class RoutingValidator implements Validator {

    @Override
    public void validate(Context context) {
        List<String> errors = context.model().getRouting().getErrors();
        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("The routing specification contains ").append(errors.size()).append(" error(s):\n");
            for (int i = 0, len = errors.size(); i < len; ++i) {
                msg.append(i + 1).append(". ").append(errors.get(i)).append("\n");
            }
            context.illegal(msg.toString());
        }
    }
}
