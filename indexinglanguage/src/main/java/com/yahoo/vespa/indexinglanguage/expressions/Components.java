// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A collection of components of a given type, of which one will be selected by the appropriate ranking expression.
 *
 * @author bratseth
 */
public abstract class Components<TYPE> {

    private final Function<String, TYPE> failingComponentFactory;

    Components(Function<String, TYPE> failingComponentFactory) {
        this.failingComponentFactory = failingComponentFactory;
    }

    /**
     * Returns all the known instance id's in this.
     */
    public abstract Set<String> ids();

    /**
     * Returns whether this is empty.
     */
    public abstract boolean isEmpty();

    /**
     * Returns the single component selected by this without supplying an id, or empty if an id is required.
     */
    public abstract Optional<TYPE> singleSelected();

    /**
     * Returns whether this contains the given id.
     */
    public abstract boolean contains(String componentId);

    /**
     * Returns the component with this id, or null if it does not exist.
     */
    public abstract TYPE get(String componentId);

    /**
     * Returns a component instance which will fail with the given message if used.
     */
    public TYPE failingComponent(String message) {
        return failingComponentFactory.apply(message);
    }

    /** A component instance backed by a map. */
    public static class Map<TYPE> extends Components<TYPE> {

        private final java.util.Map<String, TYPE> components;

        public Map(java.util.Map<String, TYPE> components, Function<String, TYPE> failingComponentFactory) {
            super(failingComponentFactory);
            this.components = components;
        }

        @Override
        public Set<String> ids() {
            return components.keySet();
        }

        @Override
        public boolean isEmpty() {
            return components.isEmpty();
        }

        @Override
        public Optional<TYPE> singleSelected() {
            if (components.size() != 1) return Optional.empty();
            return components.values().stream().findFirst();
        }

        @Override
        public boolean contains(String componentId) {
            return components.containsKey(componentId);
        }

        @Override
        public TYPE get(String componentId) {
            return components.get(componentId);
        }

    }

    /**
     * A components instance for environments where no components are available:
     * This will claim to have any component, but will only return a failing instance.
     */
    public static class Ignored<TYPE> extends Components<TYPE> {

        public Ignored(Function<String, TYPE> failingComponentFactory) {
            super(failingComponentFactory);
        }

        @Override
        public Set<String> ids() { return Set.of(); }

        @Override
        public boolean isEmpty() { return false; }

        @Override
        public Optional<TYPE> singleSelected() {
            return Optional.of(failingComponent("Components can not be created in this environment"));
        }

        @Override
        public boolean contains(String componentId) { return true; }

        @Override
        public TYPE get(String componentId) {
            return failingComponent("Components can not be created in this environment");
        }

    }

    /** A collection of components of a single type, of which one is selected. */
    public static class Selected<TYPE> {

        private final String id;
        private final TYPE component;
        private final List<String> arguments;

        public Selected(String name, Components<TYPE> components, String selectedId, boolean noIdIsAllowed,
                        List<String> arguments) {
            this.id = selectedId;
            this.arguments = List.copyOf(arguments);

            boolean selectedIdProvided = selectedId != null && !selectedId.isEmpty();

            if (components.isEmpty()) {
                throw new IllegalStateException("No " + name + "s provided");  // should never happen
            }
            else if (! selectedIdProvided && ! noIdIsAllowed) {
                throw new IllegalArgumentException("A " + name + " id must be specified. "+
                                                   "Valid " + name + "s are " + validComponents(components));
            }
            else if (components.singleSelected().isPresent() && ! selectedIdProvided) {
                this.component = components.singleSelected().get();
            }
            else if (! components.singleSelected().isPresent() && ! selectedIdProvided) {
                this.component = components.failingComponent("Multiple " + name + "s are provided but no " + name +
                                                             " id is given. " + "Valid " + name + "s are " +
                                                             validComponents(components));
            }
            else if ( ! components.contains(selectedId)) {
                this.component = components.failingComponent("Can't find " + name + " '" + selectedId + "'. " +
                                                             "Valid " + name + "s are " + validComponents(components));
            } else  {
                this.component = components.get(selectedId);
            }
        }

        public String id() { return id; }
        public TYPE component() { return component; }
        public List<String> arguments() { return arguments; }

        public String argumentsString() {
            var sb = new StringBuilder();
            if (id != null && !id.isEmpty())
                sb.append(" ").append(id);

            arguments.forEach(arg -> sb.append(" ").append(arg));
            return sb.toString();
        }

        private String validComponents(Components<TYPE> components) {
            List<String> componentIds = new ArrayList<>(components.ids());
            componentIds.sort(null);
            return String.join(", ", componentIds);
        }

        @Override
        public String toString() {
            return "selected " + component;
        }

        @Override
        public boolean equals(Object o) {
            if ( ! (o instanceof Components.Selected<?> other)) return false;
            if ( ! Objects.equals(this.id, other.id)) return false;
            if ( ! Objects.equals(this.arguments, other.arguments)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Selected.class, id, arguments);
        }

    }

}
