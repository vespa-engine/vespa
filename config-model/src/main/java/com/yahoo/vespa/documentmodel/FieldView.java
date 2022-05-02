// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.Field;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author    baldersheim
 */
public class FieldView implements Serializable {

    private final String name;
    private final Map<String, Field> fields = new LinkedHashMap<>();

    /**
     * Creates a view with a name
     * @param name Name of the view.
     */
    public FieldView(String name) {
        this.name = name;
    }
    public String getName()              { return name; }
    public Collection<Field> getFields() { return fields.values(); }
    public Field get(String name)        { return fields.get(name); }
    public void remove(String name)      { fields.remove(name); }

    /**
     * This method will add a field to a view. All fields must come from the same document type. Not enforced here.
     * @param field The field to add.
     * @return Itself for chaining purposes.
     */
    public FieldView add(Field field) {
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
     * This method will join the two views.
     * @param view The view to be joined in to this.
     * @return Itself for chaining.
     */
    public FieldView add(FieldView view) {
        for(Field field : view.getFields()) {
            add(field);
        }
        return this;
    }
}
