package com.yahoo.vespa.hosted.controller.dns;

import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @author jonmv
 */
public abstract class AbstractNameServiceRequest implements NameServiceRequest {

    private final Optional<TenantAndApplicationId> owner;
    private final RecordName name;

    AbstractNameServiceRequest(Optional<TenantAndApplicationId> owner, RecordName name) {
        this.owner = requireNonNull(owner);
        this.name = requireNonNull(name);
    }

    @Override
    public RecordName name() {
        return name;
    }

    @Override
    public Optional<TenantAndApplicationId> owner() {
        return owner;
    }

}
