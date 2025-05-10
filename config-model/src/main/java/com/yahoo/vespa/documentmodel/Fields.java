// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.vespa.objects.FieldBase;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author baldersheim
 */
public class Fields<FIELDTYPE extends FieldBase> {

    private final String name;
    private final Map<String, FIELDTYPE> fields = new LinkedHashMap<>();

    /** Creates a view with a name. */
    public Fields(String name) {
        this.name = name;
    }

    public String name()                  { return name; }
    public Collection<FIELDTYPE> values() { return fields.values(); }
    public FIELDTYPE get(String name)     { return fields.get(name); }
    public void remove(String name)       { fields.remove(name); }

    /**
     * Adds a field to this.
     *
     * @param field the field to add
     * @return this for chaining
     */
    public Fields<FIELDTYPE> add(FIELDTYPE field) {
        if (fields.containsKey(field.getName())) {
            if ( ! fields.get(field.getName()).equals(field)) {
                throw new IllegalArgumentException(
                        "View '" + name + "' already contains a field with name '" +
                        field.getName() + "' and definition : " +
                        fields.get(field.getName()).toString() + ". Your is : " + field.toString());
            }
        } else {
            fields.put(field.getName(), field);
        }
        return this;
    }

    /**
     * Adds another set of fields to this.
     *
     * @param other the fields to be added to this
     * @return this for chaining
     */
    public Fields<FIELDTYPE> add(Fields<FIELDTYPE> other) {
        for(var field : other.values()) {
            add(field);
        }
        return this;
    }

}
