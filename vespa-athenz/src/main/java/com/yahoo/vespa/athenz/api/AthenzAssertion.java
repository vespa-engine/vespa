// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * @author bjorncs
 */
public class AthenzAssertion {

    public enum Effect {
        ALLOW, DENY;

        public static Effect valueOrNull(String value) {
            try { return valueOf(value); }
            catch (RuntimeException e) { return null; }
        }
    }

    private final Long id;
    private final Effect effect;
    private final AthenzRole role;
    private final AthenzResourceName resource;
    private final String action;

    private AthenzAssertion(Builder builder) {
        this.id = builder.id;
        this.effect = builder.effect;
        this.role = builder.role;
        this.resource = builder.resource;
        this.action = builder.action;
    }

    public OptionalLong id() { return id == null ? OptionalLong.empty() : OptionalLong.of(id); }
    public Optional<Effect> effect() { return Optional.ofNullable(effect); }
    public AthenzRole role() { return role; }
    public AthenzResourceName resource() { return resource; }
    public String action() { return action; }

    public static Builder newBuilder(AthenzRole role, AthenzResourceName resource, String action) {
        return new Builder(role, resource, action);
    }

    public boolean satisfies(AthenzAssertion other) {
        return role.equals(other.role()) &&
                action.equals(other.action()) &&
                effect().equals(other.effect()) &&
                resource.equals(other.resource());
    }

    public static class Builder {
        private Long id;
        private Effect effect;
        private AthenzRole role;
        private AthenzResourceName resource;
        private String action;

        private Builder(AthenzRole role, AthenzResourceName resource, String action) {
            this.role = role;
            this.resource = resource;
            this.action = action;
        }

        public Builder id(long id) { this.id = id; return this; }
        public Builder effect(Effect effect) { this.effect = effect; return this; }
        public AthenzAssertion build() { return new AthenzAssertion(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzAssertion that = (AthenzAssertion) o;
        return Objects.equals(id, that.id) && effect == that.effect && Objects.equals(role, that.role) && Objects.equals(resource, that.resource) && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, effect, role, resource, action);
    }

    @Override
    public String toString() {
        return "AthenzAssertion{" +
                "id=" + id +
                ", effect=" + effect +
                ", role=" + role +
                ", resource=" + resource +
                ", action='" + action + '\'' +
                '}';
    }
}
