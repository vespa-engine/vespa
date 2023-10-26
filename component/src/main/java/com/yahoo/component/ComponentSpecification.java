// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

/**
 * The specification of a wanted component.
 * Consists of a name, optionally a version specification, and optionally a namespace.
 * This is an immutable value object
 *
 * @author Arne Bergene Fossaa
 * @author Tony Vaagenes
 */
public final class ComponentSpecification {

    private final class VersionHandler implements Spec.VersionHandler<VersionSpecification> {
        @Override
        public VersionSpecification emptyVersion() {
            return VersionSpecification.emptyVersionSpecification;
        }

        @Override
        public int compare(VersionSpecification v1, VersionSpecification v2) {
            return v1.compareTo(v2);
        }
    }

    private final Spec<VersionSpecification> spec;
    /** Precomputed string value */
    private final String stringValue;

    /**
     * Creates a component id from the id string form: name(:version?),
     * where version has the form 1(.2(.3(.identifier)?)?)?
     *
     * @return null iff componentSpecification == null
     */
    public static ComponentSpecification fromString(String componentSpecification) {
        try {
            return (componentSpecification != null) ?
                    new ComponentSpecification(componentSpecification) :
                    null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Illegal component specification: '" + componentSpecification + "'", e);
        }
    }

    public ComponentSpecification(String name, VersionSpecification versionSpecification, ComponentId namespace) {
        spec = new Spec<>(new VersionHandler(), name, versionSpecification, namespace);
        stringValue = spec.createStringValue();
    }

    /** Creates a component id from a name and version. The version may be null */
    public ComponentSpecification(String name, VersionSpecification versionSpec) {
        this(name, versionSpec, null);
    }

    /**
     * Creates a component id from the id string form: name(:version?),
     * where version has the form 1(.2(.3(.identifier)?)?)?
     */
    public ComponentSpecification(String id) {
        this(new SpecSplitter((id)));
    }

    private ComponentSpecification(SpecSplitter splitter) {
        this(splitter.name, VersionSpecification.fromString(splitter.version), splitter.namespace);
    }

    public ComponentSpecification nestInNamespace(ComponentId namespace) {
        ComponentId newNameSpace =
                (getNamespace() == null) ?
                        namespace :
                        getNamespace().nestInNamespace(namespace);
        return new ComponentSpecification(getName(), getVersionSpecification(), newNameSpace);
    }

    /** The namespace is null if this is to match a top level component id **/
    public ComponentId getNamespace() { return spec.namespace; }

    /** Returns the name of this. This is never null */
    public String getName() { return spec.name; }

    /** Returns the version of this id, or null if no version is specified */
    public VersionSpecification getVersionSpecification() {
        return spec.version;
    }

    /**
     * Returns the string value of this id.
     * If no version is given, this is simply the name.
     * If a version is given, it is name:version.
     * Trailing ".0"'s are stripped from the version part.
     */
    public String stringValue() { return stringValue; }

    @Override
    public String toString() {
        return toId().toString();
    }

    public boolean equals(Object o) {
        if (o==this) return true;
        if ( ! (o instanceof ComponentSpecification)) return false;
        ComponentSpecification c = (ComponentSpecification) o;
        return c.stringValue.equals(this.stringValue());
    }

    @Override
    public int hashCode() {
        return stringValue.hashCode();
    }

    /** Converts the specification to an id */
    public ComponentId toId() {
        Version version =
                (getVersionSpecification() == VersionSpecification.emptyVersionSpecification) ?
                        Version.emptyVersion :
                        getVersionSpecification().lowestMatchingVersion();

        return new ComponentId(getName(), version, getNamespace());
    }

    /**
     * Checks if a componentId matches a given spec
     */
    public boolean matches(ComponentId id) {
        boolean versionMatch = getVersionSpecification().matches(id.getVersion());
        return getName().equals(id.getName())
                && versionMatch
                && namespaceMatch(id.getNamespace());
    }

    public ComponentSpecification intersect(ComponentSpecification other) {
        if (!getName().equals(other.getName())) {
            throw new RuntimeException("The names of the component specifications does not match("
                    + getName() + "!=" + other.getName() + ").");
        }
        if (!namespaceMatch(other.getNamespace())) {
            throw new RuntimeException("The namespaces of the component specifications does not match("
                    + this + ", " + other +")");
        }

        return new ComponentSpecification(getName(),
                getVersionSpecification().intersect(other.getVersionSpecification()),
                getNamespace());
    }

    /** Returns a copy of this spec with namespace set to null **/
    public ComponentSpecification withoutNamespace() {
        return new ComponentSpecification(getName(), getVersionSpecification(), null);
    }

    private boolean namespaceMatch(ComponentId otherNamespace) {
        if (getNamespace() == otherNamespace) {
            return true;
        } else if (getNamespace() == null || otherNamespace == null){
            return false;
        } else {
            return getNamespace().equals(otherNamespace);
        }
    }

}
