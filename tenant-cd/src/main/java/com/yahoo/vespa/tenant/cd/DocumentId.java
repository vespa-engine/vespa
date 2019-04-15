package com.yahoo.vespa.tenant.cd;

import java.util.Arrays;
import java.util.List;

/**
 * Unique, immutable ID of a Vespa document, which contains information pertinent to its storage.
 *
 * @author jonmv
 */
public class DocumentId {

    private final String namespace;
    private final String documentType;
    private final String group;
    private final Long number;
    private final String userDefined;

    private DocumentId(String namespace, String documentType, String group, Long number, String userDefined) {
        this.namespace = namespace;
        this.documentType = documentType;
        this.group = group;
        this.number = number;
        this.userDefined = userDefined;
    }

    public static DocumentId of(String namespace, String documentType, String id) {
        return new DocumentId(requireNonEmpty(namespace), requireNonEmpty(documentType), null, null, requireNonEmpty(id));
    }

    public static DocumentId of(String namespace, String documentType, String group, String id) {
        return new DocumentId(requireNonEmpty(namespace), requireNonEmpty(documentType), requireNonEmpty(group), null, requireNonEmpty(id));
    }

    public static DocumentId of(String namespace, String documentType, long number, String id) {
        return new DocumentId(requireNonEmpty(namespace), requireNonEmpty(documentType), null, number, requireNonEmpty(id));
    }

    public static DocumentId ofValue(String value) {
        List<String> parts = Arrays.asList(value.split(":"));
        String id = String.join(":", parts.subList(4, parts.size()));
        if (     parts.size() < 5
            || ! parts.get(0).equals("id")
            ||   id.isEmpty()
            || ! parts.get(3).matches("((n=\\d+)|(g=\\w+))?"))
            throw new IllegalArgumentException("Document id must be on the form" +
                                               " 'id:<namespace>:<document type>:n=<integer>|g=<name>|<empty>:<user defined id>'," +
                                               " but was '" + value + "'.");

        if (parts.get(3).matches("n=\\d+"))
            return of(parts.get(1), parts.get(2), Long.parseLong(parts.get(3).substring(2)), id);
        if (parts.get(3).matches("g=\\w+"))
            return of(parts.get(1), parts.get(2), parts.get(3).substring(2), id);
        return of(parts.get(1), parts.get(2), id);
    }

    public String asValue() {
        return "id:" + namespace + ":" + documentType + ":" + grouper() + ":" + userDefined;
    }

    private String grouper() {
        return group != null ? group : number != null ? number.toString() : "";
    }

    private static String requireNonEmpty(String string) {
        if (string.isEmpty())
            throw new IllegalArgumentException("The empty string is not allowed.");
        return string;
    }

}
