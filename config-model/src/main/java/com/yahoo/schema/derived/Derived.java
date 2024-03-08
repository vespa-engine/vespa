// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.ConfigInstance;
import com.yahoo.document.Field;
import com.yahoo.io.IOUtils;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.text.StringUtilities;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Superclass of all derived configurations
 *
 * @author bratseth
 */
public abstract class Derived {

    private String name;

    public Derived() {
        this("");
    }

    public Derived(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    protected final void setName(String name) { this.name = name; }

    /**
     * Derives the content of this configuration. This
     * default calls derive(Document) for each document
     * and derive(SDField) for each search definition level field
     * AND sets the name of this to the name of the input search definition
     */
    protected void derive(Schema schema) {
        setName(schema.getName());
        derive(schema.getDocument(), schema);
        for (Index index : schema.getExplicitIndices())
            derive(index, schema);
        for (SDField field : schema.allExtraFields())
            derive(field, schema);
        schema.allImportedFields().forEach(importedField -> derive(importedField, schema));
    }


    /**
     * Derives the content of this configuration. This
     * default calls derive(SDField) for each document field
     */
    protected void derive(SDDocumentType document, Schema schema) {
        for (Field field : document.fieldSet()) {
            SDField sdField = (SDField) field;
            if ( ! sdField.isExtraField()) {
                derive(sdField, schema);
            }
        }
    }

    /**
     * Derives the content of this configuration. This
     * default does nothing.
     */
    protected void derive(ImmutableSDField field, Schema schema) {}

    /**
     * Derives the content of this configuration. This
     * default does nothing.
     */
    protected void derive(Index index, Schema schema) {
    }

    protected abstract String getDerivedName();

    /** Returns the value of getName if true, the given number as a string otherwise */
    protected String getIndex(int number, boolean labels) {
        return labels ? getName() : String.valueOf(number);
    }

    protected void export(String toDirectory, ConfigInstance cfg) throws IOException {
        Writer writer = null;
        try {
            String fileName = getDerivedName() + ".cfg";
            if (toDirectory != null) {
                writer = IOUtils.createWriter(toDirectory + "/" + fileName, false);
                exportConfig(writer, cfg);
            }
        }
        finally {
            if (writer != null) IOUtils.closeWriter(writer);
        }
    }

    private void exportConfig(Writer writer, ConfigInstance cfg) throws IOException {
        List<String> payloadL = ConfigInstance.serialize(cfg);
        String payload = StringUtilities.implodeMultiline(payloadL);
        writer.write(payload);
    }

}
