// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.List;

/**
 * @author bjorncs
 */
public record AcceptedCountries(List<Country> countries) {

    public AcceptedCountries {
        countries = List.copyOf(countries);
    }

    public record Country(String code, String displayName, boolean taxIdMandatory, List<TaxType> taxTypes) {
        public Country {
            taxTypes = List.copyOf(taxTypes);
        }
    }

    public record TaxType(String id, String description, String pattern, String example) {}
}
