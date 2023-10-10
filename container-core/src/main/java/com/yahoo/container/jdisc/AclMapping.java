// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

import java.util.Objects;

/**
 * Mapping from request to action
 *
 * @author mortent
 */
public interface AclMapping {
    class Action {
        public static final Action READ = new Action("read");
        public static final Action WRITE = new Action("write");
        private final String name;
        public static Action custom(String name) {
            return new Action(name);
        }
        private Action(String name) {
            if(Objects.requireNonNull(name).isBlank()) {
                throw new IllegalArgumentException("Name cannot be blank");
            }
            this.name = Objects.requireNonNull(name);
        }
        public String name() { return name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Action action = (Action) o;
            return Objects.equals(name, action.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "Action{" +
                   "name='" + name + '\'' +
                   '}';
        }
    }

    Action get(RequestView requestView);
}
