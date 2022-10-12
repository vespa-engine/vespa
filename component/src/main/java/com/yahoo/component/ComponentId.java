// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The id of a component.
 * Consists of a name, optionally a version, and optionally a namespace.
 * This is an immutable value object.
 *
 * @author bratseth
 * @author Tony Vaagenes
 */
public final class ComponentId implements Comparable<ComponentId> {

    private final Spec<Version> spec;
    private final boolean anonymous;

    private static final AtomicInteger threadIdCounter = new AtomicInteger(0);

    private static final ThreadLocal<Counter> threadLocalUniqueId = ThreadLocal.withInitial(Counter::new);

    private static final ThreadLocal<String> threadId = ThreadLocal.withInitial(() -> "_" + threadIdCounter.getAndIncrement() + "_");

    /** Precomputed string value */
    private final String stringValue;

    /**
     * Creates a component id from the id string form: name(:version)?(@namespace)?,
     * where version has the form 1(.2(.3(.identifier)?)?)?
     * and namespace is a component id
     */
    public ComponentId(String id) {
        this(new SpecSplitter(id));
    }

    private ComponentId(SpecSplitter splitter) {
        this(splitter.name, Version.fromString(splitter.version), splitter.namespace);
    }

    public ComponentId(String name, Version version, ComponentId namespace) {
        this(name, version, namespace, false);
    }

    /** Creates a component id from a name and version. The version may be null */
    public ComponentId(String name, Version version) {
        this(name, version, null);
    }

    private ComponentId(String id, Version version, ComponentId namespace, boolean anonymous) {
        this.spec = new Spec<>(new VersionHandler(), id, version, namespace);
        this.anonymous = anonymous;
        this.stringValue = spec.createStringValue();
    }

    public ComponentId nestInNamespace(ComponentId namespace) {
        if (namespace == null) {
            return this;
        } else {
            ComponentId newNamespace = (getNamespace() == null)
                    ? namespace
                    : getNamespace().nestInNamespace(namespace);
            return new ComponentId(getName(), getVersion(), newNamespace);
        }
    }

    /** Returns the name of this. This is never null */
    public String getName() { return spec.name; }

    /** Returns the version of this id, or emptyVersion if no version is specified */
    public Version getVersion() { return spec.version; }

    /** The namespace is null if this is a top level component id **/
    public ComponentId getNamespace() { return spec.namespace; }

    /**
     * Returns the string value of this id.
     * If no version is given, this is simply the name.
     * If a version is given, it is name:version.
     * Trailing ".0"'s are stripped from the version part.
     */
    public String stringValue() { return stringValue; }

    @Override
    public String toString() {
        return spec.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ComponentId c)) return false;

        if (isAnonymous() || c.isAnonymous()) // TODO: Stop doing this
            return false;

        return c.stringValue().equals(stringValue);
    }

    @Override
    public int hashCode() {
        return stringValue.hashCode();
    }

    public ComponentSpecification toSpecification() {
        if (isAnonymous())
            throw new RuntimeException("Can't generate a specification for an anonymous component id.");
        return new ComponentSpecification(getName(),
                getVersion().toSpecification(), getNamespace());
    }

    public int compareTo(ComponentId other) {
        //anonymous must never be equal to non-anonymous
        if (isAnonymous() ^ other.isAnonymous()) {
            return isAnonymous() ? -1 : 1;
        }

        return spec.compareTo(other.spec);
    }

    /** Creates a componentId that is unique for this run-time instance */
    public static ComponentId createAnonymousComponentId(String baseName) {
        return new ComponentId(createAnonymousId(baseName), null, null, true);
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    /** Returns a copy of this id with namespace set to null **/
    public ComponentId withoutNamespace() {
        return new ComponentId(getName(), getVersion(), null);
    }

    /**
     * Creates a component id from the id string form: name(:version)?(@namespace)?,
     * where version has the form 1(.2(.3(.identifier)?)?)?
     * and namespace is a component id.
     *
     * @return new ComponentId(componentId), or null if the input string is null
     */
    public static ComponentId fromString(String componentId) {
        try {
            return (componentId != null) ? new ComponentId(componentId) : null;
        } catch(Exception e) {
            throw new IllegalArgumentException("Illegal component id: '" + componentId + "'", e);
        }
    }

    /**
     * Returns this id's stringValue (i.e the id without trailing ".0"'s) translated to a file name using the
     * <i>standard translation:</i>
     * <pre><code>
     *     : → -
     *     / → _
     * </code></pre>
     */
    public String toFileName() {
        return stringValue.replace(":","-").replace("/",".");
    }

    /**
     * Creates an id from a file <b>first</b> name string encoded in the standard translation (see {@link #toFileName}).
     * <b>Note</b> that any file last name, like e.g ".xml" must be stripped off before handoff to this method.
     */
    public static ComponentId fromFileName(String fileName) {
        // Initial assumptions
        String id = fileName;
        Version version = null;
        ComponentId namespace = null;

        // Split out namespace, if any
        int at = id.indexOf("@");
        if (at > 0) {
            String newId = id.substring(0, at);
            namespace = ComponentId.fromString(id.substring(at + 1));
            id = newId;
        }

        // Split out version, if any
        int dash = id.lastIndexOf("-");
        if (dash > 0) {
            String newId = id.substring(0, dash);
            try {
                version = new Version(id.substring(dash + 1));
                id = newId;
            }
            catch (IllegalArgumentException e) {
                // don't interpret the text following the dash as a version
            }
        }

        // Convert dots in id portion back - this is the part which prevents us from
        id=id.replace(".","/");

        return new ComponentId(id,version,namespace);
    }

    /** WARNING: For testing only: Resets counters creating anonymous component ids for this thread. */
    public static void resetGlobalCountersForTests() {
        threadId.set("_0_");
        threadLocalUniqueId.set(new Counter());
    }

    private static String createAnonymousId(String name) {
        return name + threadId.get() + threadLocalUniqueId.get().getAndIncrement();
    }

    /** Creates a component id with the given value, marked as anonymous */
    public static ComponentId newAnonymous(String spec) {
        var splitter = new SpecSplitter(spec);
        return new ComponentId(splitter.name, Version.fromString(splitter.version), splitter.namespace, true);
    }

    private static final class VersionHandler implements Spec.VersionHandler<Version> {

        @Override
        public Version emptyVersion() {
            return Version.emptyVersion;
        }

        @Override
        public int compare(Version v1, Version v2) {
            return v1.compareTo(v2);
        }

    }

    private final static class Counter {

        private int count = 0;
        public int getAndIncrement() { return count++; }

    }

}
