// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines formatting options used with special kinds of hits.
 *
 * @author laboisse
 */
public class FormattingOptions {

	public static final String DEFAULT_TYPE_ATTRIBUTE_NAME = "type";

	/**
	 * A structure that defines the tag name and attribute name for a field
	 * that sould be formatted as a field with a subtype.
	 * @author laboisse
	 *
	 */
	static class SubtypeField {
		String tagName;
		String attributeName;
		String attributeValue;
	}

	static class SubtypeFieldWithPrefix extends SubtypeField {

		/* Note: attributeValue should always be null for instances of this class */

		int prefixLength;
	}

	private Map<String, String> fieldsAsAttributes = new LinkedHashMap<>();

	private Map<String, SubtypeField> fieldsWithSubtypes = new LinkedHashMap<>();
	private Map<String, SubtypeFieldWithPrefix> prefixedFieldsWithSubtypes = new LinkedHashMap<>();

	private Set<String> fieldsNotRendered = new LinkedHashSet<>();
	private Set<String> fieldsRendered = new LinkedHashSet<>();

	/**
	 * Tells to format a field as an attribute of the hit's tag.
	 *
	 * For instance, field 'query-latency' could be rendered as an attribute 'latency' by
	 * invoking {@code formatFieldAsAttribute("query-latency", "latency")}.
	 *
	 *  Output would be:
	 *  <pre>
	 *  &lt;hit latency="100"&gt;&lt;/hit&gt;
	 *  </pre>
	 *  instead of:
	 *  <pre>
	 *  &lt;hit&gt;&lt;latency&gt;100&lt;/latency&gt;&lt;/hit&gt;
	 *  </pre>
	 */
	public void formatFieldAsAttribute(String fieldName, String attributeName) {
		fieldsAsAttributes.put(fieldName, attributeName);
	}

	public Set<Map.Entry<String, String>> fieldsAsAttributes() {
		return Collections.unmodifiableSet(this.fieldsAsAttributes.entrySet());
	}

	public String getAttributeName(String fieldName) {
		return this.fieldsAsAttributes.get(fieldName);
	}

	/**
	 * Tells to format a field using a subtype. A subtype is used when there is kind of a grouping
	 * for a set of fields.
	 *
	 * For instance, fields 'latency-connect', 'latency-finish' all belong to the same 'latency' logical group.
	 * So invoking {@code formatFieldWithSubtype("latency-connect", "latency", "type", "connect"},
	 * {@code formatFieldWithSubtype("latency-finish", "latency", "type", "connect"} and so on,
	 * allows to have a common 'latency' tag name for all fields of the same kind.
	 * Note that it does no collapsing on tags.
	 *
	 * Output would be:
	 * <pre>
	 * &lt;latency type="connect"&gt;50&lt;/latency&gt;
	 * &lt;latency type="finish"&gt;250&lt;/latency&gt;
	 * </pre>
	 * Instead of:
	 * <pre>
	 * &lt;hit&gt;
	 *   &lt;latency-connect&gt;50&lt;/latency-connect&gt;
	 *   &lt;latency-finish&gt;50&lt;/latency-finish&gt;
	 * </pre>
	 */
	public void formatFieldWithSubtype(String fieldName, String tagName, String typeAttributeName, String typeAttributeValue) {
		SubtypeField names = new SubtypeField();
		names.attributeName = typeAttributeName;
		names.attributeValue = typeAttributeValue;
		names.tagName = tagName;
		fieldsWithSubtypes.put(fieldName, names);
	}

	public SubtypeField getSubtype(String fieldName) {
		return this.fieldsWithSubtypes.get(fieldName);
	}

	/**
	 * Same as {@link #formatFieldWithSubtype(String, String, String, String)} except that fields
	 * are selected based on the beginning of their name and the type attribute value is deduced
	 * from the rest of their name. So this may select many fields instead of only one.
	 * Invoking {@code formatFieldWithSubtype("latency-", "latency", "type")} only once allows to have a common 'latency'
	 * tag name for all fields that start with 'latency-'. Type attribute value will be 'start' for field 'latency-start'.
	 * Note that it does no collapsing on tags.
	 *
	 * This is mostly used when you don't know all field names ahead.
	 *
	 * Output would be:
	 * <pre>
	 * &lt;latency type="connect"&gt;50&lt;/latency&gt;
	 * &lt;latency type="finish"&gt;250&lt;/latency&gt;
	 * </pre>
	 * Instead of:
	 * <pre>
	 * &lt;hit&gt;
	 *   &lt;latency-connect&gt;50&lt;/latency-connect&gt;
	 *   &lt;latency-finish&gt;50&lt;/latency-finish&gt;
	 * </pre>
	 *
	 * Note: don't use this with prefixes that start with a common substring (e.g. 'http', 'http_proxy'), I can tell you it just won't work.
	 */
	public void formatFieldWithSubtype(String fieldNamePrefix, String tagName, String typeAttributeName) {
		SubtypeFieldWithPrefix names = new SubtypeFieldWithPrefix();
		names.attributeName = typeAttributeName;
		names.tagName = tagName;
		names.prefixLength = fieldNamePrefix.length();
		prefixedFieldsWithSubtypes.put(fieldNamePrefix, names);
	}

	public SubtypeFieldWithPrefix getSubtypeWithPrefix(String fieldName) {
		for(Map.Entry<String, SubtypeFieldWithPrefix> e : this.prefixedFieldsWithSubtypes.entrySet()) {
			if(fieldName.startsWith(e.getKey()))
				return e.getValue();
		}
		return null;
	}

	/**
	 * Tells whether a field should be rendered.
	 *
	 * @see #setFieldNotToRender(String)
	 * @see #setFieldToRender(String)
	 */
	public boolean shouldRenderField(String fieldName) {
		if(fieldName == null)
			return false;
                if (fieldName.startsWith("$")) {
                    return false;
                }
		if(!this.fieldsRendered.isEmpty())
			return this.fieldsRendered.contains(fieldName);
		return !this.fieldsNotRendered.contains(fieldName);
	}

	/**
	 * Tells a field should be rendered.
	 *
         * <p>
	 * Note: if at least one field is set to render, then only
	 * these fields should be rendered. Use {@link #setFieldNotToRender(String)}
	 * to only exclude specific fields.
	 */
	public void setFieldToRender(String fieldName) {
		this.fieldsRendered.add(fieldName);
	}

	/**
	 * Tells a field should not be rendered.
	 *
         * <p>
	 * Note: all other fields should be rendered. Use {@link #setFieldToRender(String)}
	 * to only include specific fields.
	 */
	public void setFieldNotToRender(String fieldName) {
		this.fieldsNotRendered.add(fieldName);
	}

}
