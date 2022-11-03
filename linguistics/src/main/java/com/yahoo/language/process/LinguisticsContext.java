// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import java.util.Objects;
import java.util.Optional;

/**
 * The context in which some text is linguistically processed.
 *
 * @author bratseth
 */
public class LinguisticsContext {

    private static final LinguisticsContext empty = new LinguisticsContext(null, null);

    private final Optional<String> schema;
    private final Optional<String> field;

    private LinguisticsContext(Optional<String> schema, Optional<String> field) {
        this.schema = schema;
        this.field = field;
    }

    /** Returns the schema we are processing for, if determined. */
    public Optional<String> schema() { return schema; }

    /** Returns the schema we are processing for, if determined. */
    public Optional<String> field() { return field; }

    public static LinguisticsContext empty() { return empty; }

    public static class Builder {

        private String schema = null;
        private String field = null;

        public Builder schema(String schema) {
            this.schema = Objects.requireNonNull(schema);
            return this;
        }

        public Builder field(String field) {
            this.field = Objects.requireNonNull(field);
            return this;
        }

        public LinguisticsContext build() {
            return new LinguisticsContext(Optional.ofNullable(schema), Optional.ofNullable(field));
        }

    }

}
