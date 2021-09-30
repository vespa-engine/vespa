// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.idstring;

import com.yahoo.collections.MD5;
import com.yahoo.text.Utf8;

/**
 * @author Magnar Nedland
 */
public class IdIdString extends IdString {
    private final String type;
    private String group;
    private long location;
    private boolean hasGroup;
    private boolean hasNumber;
    private static final int SIZE_OF_ID_AND_3_COLONS = 2 + 3; // "id:::"
    private static final int MAX_LENGTH = IdString.MAX_LENGTH_EXCEPT_NAMESPACE_SPECIFIC - SIZE_OF_ID_AND_3_COLONS;

    public static long makeLocation(String s) {
        long result = 0;
        byte[] md5sum = MD5.md5.get().digest(Utf8.toBytes(s));
        for (int i = 0; i < 8; ++i) {
            result |= (md5sum[i] & 0xFFL) << (8 * i);
        }
        return result;
    }

    /**
     * Create an id scheme object.
     * <code>doc:&lt;namespace&gt;:&lt;documentType&gt;:&lt;key-value-pairs&gt;:&lt;namespaceSpecific&gt;</code>
     *
     * @param namespace The namespace of this document id.
     * @param type The type of this document id.
     * @param keyValues The key/value pairs of this document id.
     * @param localId The namespace specific part.
     */
    public IdIdString(String namespace, String type, String keyValues, String localId) {
        super(Scheme.id, namespace, localId);
        this.type = type;
        boolean hasSetLocation = false;
        if (namespace.length() + type.length() + keyValues.length() >= MAX_LENGTH) {
            throw new IllegalArgumentException("Length of namespace(" + namespace.length() +  ") + doctype(" + type.length() +
                    ") + key/values(" + keyValues.length() +"), is longer than " + MAX_LENGTH);
        }
        for(String pair : keyValues.split(",")) {
            int pos = pair.indexOf('=');
            if (pos == -1) {
                if (pair.equals("")) {  // empty pair is ok
                    continue;
                }
                throw new IllegalArgumentException("Illegal key-value pair '" + pair + "'");
            }
            String key = pair.substring(0, pos);
            String value = pair.substring(pos + 1);
            switch(key) {
                case "n":
                    if (hasSetLocation) {
                        throw new IllegalArgumentException("Illegal key combination in " + keyValues);
                    }
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("ID location value for 'n=' key is empty");
                    }
                    location = Long.parseLong(value);
                    hasSetLocation = true;
                    hasNumber = true;
                    break;
                case "g":
                    if (hasSetLocation) {
                        throw new IllegalArgumentException("Illegal key combination in " + keyValues);
                    }
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("ID location value for 'g=' key is empty");
                    }
                    location = makeLocation(value);
                    hasSetLocation = true;
                    hasGroup = true;
                    group = value;
                    break;
                default:
                    throw new IllegalArgumentException("Illegal key '" + key + "'");
            }
        }
        if (!hasSetLocation) {
            location = makeLocation(localId);
        }
    }

    @Override
    public long getLocation() {
        return location;
    }

    @Override
    public String getSchemeSpecific() {
        if (hasGroup) {
            return type + ":g=" + group + ":";
        } else if (hasNumber) {
            return type + ":n=" + location + ":";
        } else {
          return type + "::";
        }
    }

    @Override
    public boolean hasDocType() {
        return true;
    }

    @Override
    public String getDocType() {
        return type;
    }

    @Override
    public boolean hasGroup() {
        return hasGroup;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public boolean hasNumber() {
        return hasNumber;
    }

    @Override
    public long getNumber() {
        return location;
    }
}
