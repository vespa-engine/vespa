// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

/**
 * @author akvalsvik
 */
public class MetricsType extends SerializedIdentifier {

    public MetricsType(String id) {
        super(id);
    }

    @Override
    public void validate() {
        super.validate();
        validateNoUpperCase();
    }

    public static void validate(String id) {
        if (!(id.equals("deployment") || id.equals("proton"))) {
            throwInvalidId(id, "MetricsType be \"deployment\" or \"proton\"");
        }
    }
}
