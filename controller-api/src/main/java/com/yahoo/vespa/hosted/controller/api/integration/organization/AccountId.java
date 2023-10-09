// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import ai.vespa.validation.StringWrapper;

public class AccountId extends StringWrapper<AccountId> {

    public AccountId(String value) {
        super(value);
        if (value.isBlank()) throw new IllegalArgumentException("id must be non-blank");
    }

}
