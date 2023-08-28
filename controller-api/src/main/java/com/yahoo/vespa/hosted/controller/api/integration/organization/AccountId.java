package com.yahoo.vespa.hosted.controller.api.integration.organization;

import ai.vespa.validation.StringWrapper;

public class AccountId extends StringWrapper<AccountId> {

    public AccountId(String value) {
        super(value);
        if (value.isBlank()) throw new IllegalArgumentException("id must be non-blank");
    }

}
