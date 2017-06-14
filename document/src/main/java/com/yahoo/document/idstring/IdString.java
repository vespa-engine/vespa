// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.idstring;

import com.yahoo.text.Utf8String;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * To be used with DocumentId constructor.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
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

    public class GidModifier {
        public int usedBits;
        public long value;
    }

    public enum Scheme { doc, userdoc, groupdoc, orderdoc, id }
    final Scheme scheme;
    final String namespace;
    final String namespaceSpecific;
    Utf8String cache;

    public static int[] generateOrderDocParams(String scheme) {
        int parenPos = scheme.indexOf("(");
        int endParenPos = scheme.indexOf(")");

        if (parenPos == -1 || endParenPos == -1) {
            throw new IllegalArgumentException("Unparseable scheme " + scheme + ": Must be on the form orderdoc(width, division)");
        }

        String params = scheme.substring(parenPos + 1, endParenPos);
        String[] vals = params.split(",");

        if (vals.length != 2) {
            throw new IllegalArgumentException("Unparseable scheme " + scheme + ": Must be on the form orderdoc(width, division)");
        }

        int[] retVal = new int[2];

        try {
            retVal[0] = Integer.parseInt(vals[0]);
            retVal[1] = Integer.parseInt(vals[1]);
            return retVal;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable scheme " + scheme + ": Must be on the form orderdoc(width, division)");
        }
    }

    public static IdString createIdString(String id) {
        String namespace;
        long userId;
        String group;
        long ordering;

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
            }
            String keyValues = id.substring(currPos, colonPos);

            currPos = colonPos + 1;
            return new IdIdString(namespace, type, keyValues, id.substring(currPos));

        } if (schemeStr.equals("doc")) {
            return new DocIdString(namespace, id.substring(currPos));
        } else if (schemeStr.equals("userdoc")) {
            colonPos = id.indexOf(":", currPos);
            if (colonPos < 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': User id missing");
            }

            try {
                userId = new BigInteger(id.substring(currPos, colonPos)).longValue();
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': " + iae.getMessage(), iae.getCause());
            }

            currPos = colonPos + 1;
            return new UserDocIdString(namespace, userId, id.substring(currPos));
        } else if (schemeStr.equals("groupdoc")) {
            colonPos = id.indexOf(":", currPos);

            if (colonPos < 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Group id missing");
            }

            group = id.substring(currPos, colonPos);
            currPos = colonPos + 1;
            return new GroupDocIdString(namespace, group, id.substring(currPos));
        } else if (schemeStr.indexOf("orderdoc") == 0) {
            int[] params = generateOrderDocParams(schemeStr);

            colonPos = id.indexOf(":", currPos);

            if (colonPos < 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Group id missing");
            }

            group = id.substring(currPos, colonPos);

            currPos = colonPos + 1;

            colonPos = id.indexOf(":", currPos);
            if (colonPos < 0) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': Ordering missing");
            }

            try {
                ordering = Long.parseLong(id.substring(currPos, colonPos));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unparseable id '" + id + "': " + e.getMessage(), e.getCause());
            }

            currPos = colonPos + 1;
            return new OrderDocIdString(namespace, group, params[0], params[1], ordering, id.substring(currPos));
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
    public GidModifier getGidModifier() { return null; }

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
