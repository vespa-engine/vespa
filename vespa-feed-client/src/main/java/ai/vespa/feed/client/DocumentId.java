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
        String[] parts = serialized.split(":");
        while (parts.length >= 5 && parts[0].equals("id")) {
            if (parts[3].startsWith("n="))
                return DocumentId.of(parts[1], parts[2], Long.parseLong(parts[3]), parts[4]);
            if (parts[3].startsWith("g="))
                return DocumentId.of(parts[1], parts[2], parts[3], parts[4]);
            else if (parts[3].isEmpty())
                return DocumentId.of(parts[1], parts[2], parts[4]);
        }
        throw new IllegalArgumentException("Document ID must be on the form " +
                                           "'id:<namespace>:<document-type>:[n=number|g=group]:<user-specific>', " +
                                           "but was '" + serialized + "'");
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
