// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigInstance.Builder;
import com.yahoo.document.Field;
import com.yahoo.io.IOUtils;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.text.StringUtilities;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Superclass of all derived configurations
 *
 * @author bratseth
 */
public abstract class Derived implements Exportable {

    private String name;

    public String getName() { return name; }

    protected final void setName(String name) { this.name=name; }

    /**
     * Derives the content of this configuration. This
     * default calls derive(Document) for each document
     * and derive(SDField) for each search definition level field
     * AND sets the name of this to the name of the input search definition
     */
    protected void derive(Search search) {
        setName(search.getName());
        derive(search.getDocument(), search);
        for (Index index : search.getExplicitIndices()) {
            derive(index, search);
        }
        for (SDField field : search.allExtraFields() ) {
            derive(field, search);
        }
        search.allImportedFields()
                .forEach(importedField -> derive(importedField, search));
    }


    /**
     * Derives the content of this configuration. This
     * default calls derive(SDField) for each document field
     */
    protected void derive(SDDocumentType document,Search search) {
        for (Field field : document.fieldSet()) {
            SDField sdField = (SDField) field;
            if (!sdField.isExtraField()) {
                derive(sdField, search);
            }
        }
    }

    /**
     * Derives the content of this configuration. This
     * default does nothing.
     */
    protected void derive(ImmutableSDField field, Search search) {}

    /**
     * Derives the content of this configuration. This
     * default does nothing.
     */
    protected void derive(Index index, Search search) {
    }

    protected abstract String getDerivedName();

    /** Returns the value of getName if true, the given number as a string otherwise */
    protected String getIndex(int number,boolean labels) {
        return labels ? getName() : String.valueOf(number);
    }

    /**
     * Exports this derived configuration to its .cfg file
     * in toDirectory
     *
     * @param toDirectory the directory to export to, or null
     *
     */
    public final void export(String toDirectory)
            throws IOException {
        Writer writer=null;
        try {
            String fileName=getDerivedName() + ".cfg";
            if (toDirectory!=null) writer=IOUtils.createWriter(toDirectory + "/" + fileName,false);
            try {
                exportBuilderConfig(writer);
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
                throw new RuntimeException(e);
            }
        }
        finally {
            if (writer!=null) IOUtils.closeWriter(writer);
        }
    }

    /**
     * Checks what this is a producer of, instantiate that and export to writer
     */
    // TODO move to ReflectionUtil, and move that to unexported pkg
    private void exportBuilderConfig(Writer writer) throws ReflectiveOperationException, SecurityException, IllegalArgumentException, IOException {
        for (Class<?> intf : getClass().getInterfaces()) {
            if (ConfigInstance.Producer.class.isAssignableFrom(intf)) {
                Class<?> configClass = intf.getEnclosingClass();
                String builderClassName = configClass.getCanonicalName()+"$Builder";
                Class<?> builderClass = Class.forName(builderClassName);
                ConfigInstance.Builder builder = (Builder) builderClass.getDeclaredConstructor().newInstance();
                Method getConfig = getClass().getMethod("getConfig", builderClass);
                getConfig.invoke(this, builder);
                ConfigInstance inst = (ConfigInstance) configClass.getConstructor(builderClass).newInstance(builder);
                List<String> payloadL = ConfigInstance.serialize(inst);
                String payload = StringUtilities.implodeMultiline(payloadL);
                writer.write(payload);
            }
        }
    }

    @Override
    public String getFileName() {
        return getDerivedName() + ".cfg";
    }

}
