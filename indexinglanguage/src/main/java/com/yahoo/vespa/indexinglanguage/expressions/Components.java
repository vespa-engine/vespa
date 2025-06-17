package com.yahoo.vespa.indexinglanguage.expressions;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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

}
