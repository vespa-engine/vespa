// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public class Results<DATA, ERROR> {

    private final List<DATA> data;
    private final List<ERROR> errors;

    private Results(List<DATA> data, List<ERROR> errors) {
        this.data = List.copyOf(data);
        this.errors = List.copyOf(errors);
    }

    public List<DATA> data() {
        return data;
    }

    public List<ERROR> errors() {
        return errors;
    }

    public static class Builder<DATA, ERROR> {
        private final List<DATA> data = new ArrayList<>();
        private final List<ERROR> errors = new ArrayList<>();

        public void addData(DATA d) {
            data.add(d);
        }
        public void addError(ERROR e) {
            errors.add(e);
        }

        public Results<DATA, ERROR> build() {
            return new Results<>(data, errors);
        }
    }

}
