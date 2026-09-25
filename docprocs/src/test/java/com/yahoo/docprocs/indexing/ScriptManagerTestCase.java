// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.DataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.language.process.Chunker;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.FieldGenerator;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.indexinglanguage.FieldValuesFactory;
import com.yahoo.vespa.indexinglanguage.expressions.InvalidInputException;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptManagerTestCase {

    @Test
    public void requireThatScriptsAreAppliedToSubType() {
        var typeMgr = DocumentTypeManager.fromFile("src/test/cfg/documentmanager_inherit.cfg");
        DocumentType docType = typeMgr.getDocumentType("newssummary");
        assertNotNull(docType);


        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder().doctype("newssummary")
                                                              .content("input title | index title"));
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(config), null,
                                                    Chunker.throwsOnUse.asMap(),
                                                    Embedder.throwsOnUse.asMap(),
                                                    FieldGenerator.throwsOnUse.asMap(),
                                                    MetricReceiver.nullImplementation);
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newsarticle")));
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatScriptsAreAppliedToSuperType() {
        var typeMgr = DocumentTypeManager.fromFile("src/test/cfg/documentmanager_inherit.cfg");
        DocumentType docType = typeMgr.getDocumentType("newsarticle");
        assertNotNull(docType);

        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder().doctype("newsarticle")
                                                              .content("input title | index title"));
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(config), null,
                                                    Chunker.throwsOnUse.asMap(),
                                                    Embedder.throwsOnUse.asMap(),
                                                    FieldGenerator.throwsOnUse.asMap(),
                                                    MetricReceiver.nullImplementation);
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newssummary")));
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatEmptyConfigurationDoesNotThrow() {
        var typeMgr = DocumentTypeManager.fromFile("src/test/cfg/documentmanager_inherit.cfg");
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(new IlscriptsConfig.Builder()), null,
                                                    Chunker.throwsOnUse.asMap(),
                                                    Embedder.throwsOnUse.asMap(),
                                                    FieldGenerator.throwsOnUse.asMap(),
                                                    MetricReceiver.nullImplementation);
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatUnknownDocumentTypeReturnsNull() {
        var typeMgr = DocumentTypeManager.fromFile("src/test/cfg/documentmanager_inherit.cfg");
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(new IlscriptsConfig.Builder()), null,
                                                    Chunker.throwsOnUse.asMap(),
                                                    Embedder.throwsOnUse.asMap(),
                                                    FieldGenerator.throwsOnUse.asMap(),
                                                    MetricReceiver.nullImplementation);
        for (Iterator<DocumentType> it = typeMgr.documentTypeIterator(); it.hasNext(); ) {
            assertNull(scriptMgr.getScript(it.next()));
        }
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatInputFieldsCanBeOptional() {
        var typeMgr = DocumentTypeManager.fromFile("src/test/cfg/documentmanager_inherit.cfg");
        DocumentType docType = typeMgr.getDocumentType("newsarticle");
        assertNotNull(docType);
        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder().doctype("newsarticle")
                        .content("(input uri || \"\") | attribute city")
                        .content("clear_state | guard { (input uri || \"\") | set_language; input weight | attribute weight; }")
                        .content("input title | index title"));
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(config), null,
                                                    Chunker.throwsOnUse.asMap(),
                                                    Embedder.throwsOnUse.asMap(),
                                                    FieldGenerator.throwsOnUse.asMap(),
                                                    MetricReceiver.nullImplementation);
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newsarticle"), "title"));
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newsarticle"), "weight"));
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newsarticle"), "uri"));
    }

    @Test
    public void requireThatFieldPathUpdateIntoFastSearchMapIsRejected() {
        DocumentUpdate update = newUpdate();
        update.addFieldPathUpdate(new AssignFieldPathUpdate(update.getDocumentType(), "myMap{key}",
                                                            new StringFieldValue("value")));
        try {
            executeWithFastMapSearch(update);
            fail("Expected exception");
        }
        catch (InvalidInputException e) {
            assertEquals("Field 'myMap' has 'map: fast-search', which does not support field path updates " +
                         "into the field. Assign the whole field instead.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatFieldPathUpdateAssigningTheWholeFastSearchMapIsAccepted() {
        MapFieldValue<StringFieldValue, StringFieldValue> map =
                new MapFieldValue<>(DataType.getMap(DataType.STRING, DataType.STRING));
        map.put(new StringFieldValue("key"), new StringFieldValue("value"));

        DocumentUpdate update = newUpdate();
        update.addFieldPathUpdate(new AssignFieldPathUpdate(update.getDocumentType(), "myMap", map));
        executeWithFastMapSearch(update);
    }

    @Test
    public void requireThatFieldPathUpdateToOtherFieldIsAccepted() {
        DocumentUpdate update = newUpdate();
        update.addFieldPathUpdate(new AssignFieldPathUpdate(update.getDocumentType(), "myOtherMap{key}",
                                                            new StringFieldValue("value")));
        executeWithFastMapSearch(update);
    }

    @Test
    public void requireThatElementUpdateOfFastSearchArrayIsRejected() {
        assertArrayUpdateRejected(FieldUpdate.createMap(myArrayField(), new IntegerFieldValue(0),
                                                        ValueUpdate.createAssign(newEntry("key", "value"))));
    }

    @Test
    public void requireThatRemoveFromFastSearchArrayIsRejected() {
        assertArrayUpdateRejected(FieldUpdate.createRemove(myArrayField(), newEntry("key", "value")));
    }

    @Test
    public void requireThatAssignClearAndAddOnFastSearchArrayAreAccepted() {
        Array<Struct> array = new Array<>(myArrayField().getDataType());
        array.add(newEntry("key", "value"));
        executeWithFastMapSearch(newUpdate().addFieldUpdate(FieldUpdate.createAssign(myArrayField(), array)));
        executeWithFastMapSearch(newUpdate().addFieldUpdate(FieldUpdate.createClearField(myArrayField())));
        executeWithFastMapSearch(newUpdate().addFieldUpdate(FieldUpdate.createAdd(myArrayField(), newEntry("key", "value"))));
    }

    private static void assertArrayUpdateRejected(FieldUpdate fieldUpdate) {
        try {
            executeWithFastMapSearch(newUpdate().addFieldUpdate(fieldUpdate));
            fail("Expected exception");
        }
        catch (InvalidInputException e) {
            assertEquals("Field 'myArray' has 'map: fast-search', which does not support updating or removing " +
                         "single array elements. Assign the whole field instead.",
                         e.getMessage());
        }
    }

    private static final StructDataType entryType = newEntryType();

    private static StructDataType newEntryType() {
        var type = new StructDataType("entry");
        type.addField(new com.yahoo.document.Field("mykey", DataType.STRING));
        type.addField(new com.yahoo.document.Field("myvalue", DataType.STRING));
        return type;
    }

    private static Struct newEntry(String key, String value) {
        Struct entry = entryType.createFieldValue();
        entry.setFieldValue("mykey", key);
        entry.setFieldValue("myvalue", value);
        return entry;
    }

    private static com.yahoo.document.Field myArrayField() {
        return newUpdate().getDocumentType().getField("myArray");
    }

    private static DocumentUpdate newUpdate() {
        DocumentType docType = new DocumentType("myDocumentType");
        docType.addField("myMap", DataType.getMap(DataType.STRING, DataType.STRING));
        docType.addField("myOtherMap", DataType.getMap(DataType.STRING, DataType.STRING));
        docType.addField("myArray", DataType.getArray(entryType));
        return new DocumentUpdate(docType, "id:ns:myDocumentType::");
    }

    /** Runs the given update through a script manager configured with 'map: fast-search' on 'myMap' and 'myArray'. */
    private static void executeWithFastMapSearch(DocumentUpdate update) {
        DocumentType docType = update.getDocumentType();
        var typeMgr = new DocumentTypeManager();
        typeMgr.registerDocumentType(docType);

        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder()
                                .doctype(docType.getName())
                                .docfield("myMap")
                                .docfield("myOtherMap")
                                .docfield("myArray")
                                .complexfield(field -> field.name("myMap")
                                                            .why(IlscriptsConfig.Ilscript.Complexfield.Why.FAST_MAP_SEARCH))
                                .complexfield(field -> field.name("myArray")
                                                            .why(IlscriptsConfig.Ilscript.Complexfield.Why.FAST_MAP_SEARCH)));
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(config), null,
                                                    Chunker.throwsOnUse.asMap(),
                                                    Embedder.throwsOnUse.asMap(),
                                                    FieldGenerator.throwsOnUse.asMap(),
                                                    MetricReceiver.nullImplementation);
        scriptMgr.getScript(docType).execute(new FieldValuesFactory(), update, null);
    }

}
