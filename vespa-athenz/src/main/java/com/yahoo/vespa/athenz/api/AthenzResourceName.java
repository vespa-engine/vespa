// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * Athenz resource name
 *
 * @author bjorncs
 */
public class AthenzResourceName {

    private final AthenzDomain domain;
    private final String entityName;

    public AthenzResourceName(AthenzDomain domain, String entityName) {
        this.domain = domain;
        this.entityName = entityName;
    }

    public AthenzResourceName(String domain, String entityName) {
        this(new AthenzDomain(domain), entityName);
    }

    /**
     * @param resourceName A resource name string on format 'domain:entity'
     * @return the parsed resource name
     */
    public static AthenzResourceName fromString(String resourceName) {
        String[] split = resourceName.split(":");
        if (split.length != 2 || split[0].isEmpty() || split[1].isEmpty()) {
            throw new IllegalArgumentException("Invalid resource name: " + resourceName);
        }
        return new AthenzResourceName(split[0], split[1]);
    }

    public AthenzDomain getDomain() {
        return domain;
    }

    public String getDomainName() {
        return domain.getName();
    }

    public String getEntityName() {
        return entityName;
    }

    public String toResourceNameString() {
        return String.format("%s:%s", domain.getName(), entityName);
    }

    @Override
    public String toString() {
        return "AthenzResourceName{" +
                "domain=" + domain +
                ", entityName='" + entityName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzResourceName that = (AthenzResourceName) o;
        return Objects.equals(domain, that.domain) &&
                Objects.equals(entityName, that.entityName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, entityName);
    }
}
