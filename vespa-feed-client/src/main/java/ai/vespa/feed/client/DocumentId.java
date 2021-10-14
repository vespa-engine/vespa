// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import static java.util.Objects.requireNonNull;

/**
 * Represents a Vespa document id
 *
 * @author jonmv
 */
public class DocumentId {

    private final String documentType;
    private final String namespace;
    private final OptionalLong number;
    private final Optional<String> group;
    private final String userSpecific;

    private DocumentId(String documentType, String namespace, OptionalLong number, Optional<String> group, String userSpecific) {
        this.documentType = requireNonNull(documentType);
        this.namespace = requireNonNull(namespace);
        this.number = requireNonNull(number);
        this.group = requireNonNull(group);
        this.userSpecific = requireNonNull(userSpecific);
    }

    public static DocumentId of(String namespace, String documentType, String userSpecific) {
        return new DocumentId(documentType, namespace, OptionalLong.empty(), Optional.empty(), userSpecific);
    }

    public static DocumentId of(String namespace, String documentType, long number, String userSpecific) {
        return new DocumentId(documentType, namespace, OptionalLong.of(number), Optional.empty(), userSpecific);
    }

    public static DocumentId of(String namespace, String documentType, String group, String userSpecific) {
        return new DocumentId(documentType, namespace, OptionalLong.empty(), Optional.of(group), userSpecific);
    }

    public static DocumentId of(String serialized) {
        DocumentId parsed = parse(serialized);
        if (parsed != null) return parsed;
        throw new IllegalArgumentException("Document ID must be on the form " +
                                           "'id:<namespace>:<document-type>:[n=<number>|g=<group>]:<user-specific>', " +
                                           "but was '" + serialized + "'");
    }

    private static DocumentId parse(String serialized) {
        int i, j = -1;
        if ((j = serialized.indexOf(':', i = j + 1)) < i) return null;
        if ( ! "id".equals(serialized.substring(i, j))) return null;
        if ((j = serialized.indexOf(':', i = j + 1)) <= i) return null;
        String namespace = serialized.substring(i, j);
        if ((j = serialized.indexOf(':', i = j + 1)) <= i) return null;
        String documentType = serialized.substring(i, j);
        if ((j = serialized.indexOf(':', i = j + 1)) < i) return null;
        String group = serialized.substring(i, j);
        if (serialized.length() <= (i = j + 1)) return null;
        String userSpecific = serialized.substring(i);
        if (group.startsWith("n=") && group.length() > 2)
            return DocumentId.of(namespace, documentType, Long.parseLong(group.substring(2)), userSpecific);
        if (group.startsWith("g=") && group.length() > 2)
            return DocumentId.of(namespace, documentType, group.substring(2), userSpecific);
        if (group.isEmpty())
            return DocumentId.of(namespace, documentType, userSpecific);
        return null;
    }

    public String documentType() {
        return documentType;
    }

    public String namespace() {
        return namespace;
    }

    public OptionalLong number() {
        return number;
    }

    public Optional<String> group() {
        return group;
    }

    public String userSpecific() {
        return userSpecific;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentId that = (DocumentId) o;
        return documentType.equals(that.documentType) && namespace.equals(that.namespace) && number.equals(that.number) && group.equals(that.group) && userSpecific.equals(that.userSpecific);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentType, namespace, number, group, userSpecific);
    }

    @Override
    public String toString() {
        return "id:" + namespace + ":" + documentType + ":" +
               (number.isPresent() ? "n=" + number.getAsLong() : group.map("g="::concat).orElse("")) +
               ":" + userSpecific;
    }

}
