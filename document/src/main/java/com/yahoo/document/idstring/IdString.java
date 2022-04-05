// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.idstring;

import com.yahoo.api.annotations.Beta;
import com.yahoo.text.Text;
import com.yahoo.text.Utf8String;

/**
 * To be used with DocumentId constructor.
 *
 * @author Einar M R Rosenvinge
 */
public abstract class IdString {

    public boolean hasDocType() {
        return false;
    }

    public String getDocType() {
        return "";
    }

    public boolean hasGroup() {
        return false;
    }

    public boolean hasNumber() {
        return false;
    }

    public long getNumber() {
        return 0;
    }

    public String getGroup() {
        return "";
    }

    public enum Scheme { id }
    private final Scheme scheme;
    private final String namespace;
    private final String namespaceSpecific;
    private Utf8String cache;
    // This max unsigned 16 bit integer - 1 as the offset will be length + 1
    static final int MAX_LENGTH_EXCEPT_NAMESPACE_SPECIFIC = 0xff00;
    public static final int MAX_LENGTH = 0x10000;

    /**
     * Creates a IdString based on the given document id string.
     *
     * The document id string can only contain text characters.
     */
    public static IdString createIdString(String id) {
        if (id.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Document id length " + id.length() + " is longer than max length of " + MAX_LENGTH);
        }
        validateTextString(id);
        return parseAndCreate(id);
    }

    /**
     * Creates a IdString based on the given document id string. This is a less strict variant
     * for creating 'illegal' document ids for documents already fed. Only use when strictly needed.
     */
    @Beta
    public static IdString createIdStringLessStrict(String id) {
        validateTextString(id);
        return parseAndCreate(id);
    }

    /**
     * Creates a IdString based on the given serialized document id string.
     *
     * The document id string can not contain 0x0 byte characters.
     */
    public static IdString createFromSerialized(String id) {
        validateNoZeroBytes(id);
        return parseAndCreate(id);
    }

    private static void validateTextString(String id) {
        if ( ! Text.isValidTextString(id)) {
            throw new IllegalArgumentException("Unparseable id '" + id + "': Contains illegal code point 0x" +
                    Integer.toHexString(Text.validateTextString(id).getAsInt()).toUpperCase());
        }
    }

    private static void validateNoZeroBytes(String id) {
        for (int i = 0; i < id.length(); i++) {
            if (id.codePointAt(i) == 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Contains illegal zero byte code point");
            }
        }
    }

    private static IdString parseAndCreate(String id) {
        String namespace;

        int schemePos = id.indexOf(":");
        if (schemePos < 0) {
            throw new IllegalArgumentException("Unparseable id '" + id + "': Scheme missing");
        }

        //Find scheme
        String schemeStr = id.substring(0, schemePos);
        int currPos = schemePos + 1;

        //Find namespace
        int colonPos = id.indexOf(":", currPos);
        if (colonPos < 0) {
            throw new IllegalArgumentException("Unparseable id '" + id + "': Namespace missing");
        } else {
            namespace = id.substring(currPos, colonPos);

            if (namespace.length() == 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Namespace must be non-empty");
            }

            currPos = colonPos + 1;
        }

        if (schemeStr.equals("id")) {
            colonPos = id.indexOf(":", currPos);
            if (colonPos < 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Document type missing");
            }
            String type = id.substring(currPos, colonPos);
            currPos = colonPos + 1;
            colonPos = id.indexOf(":", currPos);
            if (colonPos < 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Key/value section missing");
            } else if (colonPos >= MAX_LENGTH_EXCEPT_NAMESPACE_SPECIFIC) {
                throw new IllegalArgumentException("Document id prior to the namespace specific part, " + colonPos + ", is longer than " + MAX_LENGTH_EXCEPT_NAMESPACE_SPECIFIC + " id: " + id);
            }
            String keyValues = id.substring(currPos, colonPos);

            currPos = colonPos + 1;
            return new IdIdString(namespace, type, keyValues, id.substring(currPos));
        } else {
            throw new IllegalArgumentException("Unknown id scheme '" + schemeStr + "'");
        }
    }

    protected IdString(Scheme scheme, String namespace, String namespaceSpecific) {
        this.scheme = scheme;
        this.namespace = namespace;
        this.namespaceSpecific = namespaceSpecific;
    }

    public Scheme getType() { return scheme; }

    public String getNamespace() { return namespace; }
    public String getNamespaceSpecific() { return namespaceSpecific; }
    public abstract long getLocation();
    public String getSchemeParameters() { return ""; }
    public abstract String getSchemeSpecific();

    public boolean equals(Object o) {
        return (o instanceof IdString && o.toString().equals(toString()));
    }

    public int hashCode() {
        return toString().hashCode();
    }

    private Utf8String createToString() {
        return new Utf8String(scheme.toString() + getSchemeParameters() + ':' + namespace + ':' + getSchemeSpecific() + namespaceSpecific);
    }
    public String toString() {
        if (cache == null) {
            cache = createToString();
        }
        return cache.toString();
    }
    public Utf8String toUtf8() {
        if (cache == null) {
            cache = createToString();
        }
        return cache;
    }

}
