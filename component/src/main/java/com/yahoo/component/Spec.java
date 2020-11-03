// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

/**
 * Code common to ComponentId and ComponentSpecification
 *
 * @author Tony Vaagenes
 */
final class Spec<VERSION> {

    private final VersionHandler<VERSION> versionHandler;

    interface VersionHandler<VERSION> {
        VERSION emptyVersion();
        int compare(VERSION v1, VERSION v2);
    }

    final String name;
    final VERSION version;
    final ComponentId namespace;

    @SuppressWarnings("unchecked")
    Spec(VersionHandler<VERSION> versionHandler, String name, VERSION version, ComponentId namespace) {
        assert (name != null);
        validateName(name);

        this.versionHandler = versionHandler;
        this.name = name;
        this.version = version == null ? versionHandler.emptyVersion() : version;
        this.namespace = namespace;
    }

    String createStringValue() {
        if (isNonEmpty(version) || (namespace != null)) {
            StringBuilder builder = new StringBuilder(name);
            if (isNonEmpty(version))
                builder.append(':').append(version);
            if (namespace != null)
                builder.append('@').append(namespace.stringValue());
            return builder.toString();
        } else {
            return name;
        }
    }

    private void validateName(String name) {
        if ( name == null || name.isEmpty() || name.contains("@") || name.contains(":")) {
            throw new IllegalArgumentException("The name '" + name + "' is expected to be non-empty and not contain {:, @}");
        }
    }

    @Override
    public String toString() {
        if (isNonEmpty(version) || (namespace != null)) {
            StringBuilder builder = new StringBuilder(name);
            if (isNonEmpty(version)) {
                builder.append(':').append(version);
            }
            if (namespace != null) {
                builder.append(" in ").append(namespace.toString());
            }
            return builder.toString();
        } else {
            return name;
        }
    }

    private boolean isNonEmpty(VERSION version) {
        return ! version.equals(versionHandler.emptyVersion());
    }

    public int compareTo(Spec<VERSION> other) {
        int result = name.compareTo(other.name);
        if (result != 0)
            return result;

        result = versionHandler.compare(version, other.version);
        if (result != 0)
            return result;

        return compare(namespace, other.namespace);
    }

    private int compare(ComponentId n1, ComponentId n2) {
        if (n1 == null && n2 == null)
            return 0;
        if (n1 == null)
            return -1;
        if (n2 == null)
            return 1;

        return n1.compareTo(n2);
    }

}
