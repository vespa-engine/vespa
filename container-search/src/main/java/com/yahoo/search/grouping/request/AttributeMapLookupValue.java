// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a lookup in a map attribute in a {@link GroupingExpression}.
 *
 * It evaluates to the value found using the given key for the lookup in that attribute.
 * The key is either specified explicitly or found via a key source attribute.
 * Two underlying attributes are used to represent the map attribute (the key and value attributes).
 *
 * @author geirst
 */
public class AttributeMapLookupValue extends AttributeValue {

    private final String prefix;
    private final String suffix;
    private final String key;
    private final String keySourceAttribute;

    private AttributeMapLookupValue(String attributeValue, String prefix, String suffix, String key, String keySourceAttribute) {
        super(attributeValue);
        this.prefix = prefix;
        this.suffix = suffix;
        this.key = key;
        this.keySourceAttribute = keySourceAttribute;
    }

    public static AttributeMapLookupValue fromKey(String prefix, String key, String suffix) {
        return new AttributeMapLookupValue(prefix + "{\"" + key + "\"}" + suffix,
                prefix, suffix, key, "");
    }

    public static AttributeMapLookupValue fromKeySourceAttribute(String prefix, String keySourceAttribute, String suffix) {
        return new AttributeMapLookupValue(prefix + "{attribute(" + keySourceAttribute + ")}" + suffix,
                prefix, suffix, "", keySourceAttribute);
    }

    @Override
    public AttributeMapLookupValue copy() {
        return new AttributeMapLookupValue(getAttributeName(), prefix, suffix, key, keySourceAttribute);
    }

    public String getKeyAttribute() {
        return prefix + ".key";
    }

    public String getValueAttribute() {
        return prefix + ".value" + suffix;
    }

    public String getKey() {
        return key;
    }

    public boolean hasKeySourceAttribute() {
        return !keySourceAttribute.isEmpty();
    }

    public String getKeySourceAttribute() {
        return keySourceAttribute;
    }
}
