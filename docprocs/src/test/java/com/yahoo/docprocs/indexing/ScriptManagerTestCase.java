// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptManagerTestCase {

    @Test
    public void requireThatScriptsAreAppliedToSubType() throws ParseException {
        DocumentTypeManager typeMgr = new DocumentTypeManager();
        typeMgr.configure("file:src/test/cfg/documentmanager_inherit.cfg");
        DocumentType docType = typeMgr.getDocumentType("newssummary");
        assertNotNull(docType);


        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder().doctype("newssummary")
                                                              .content("index"));
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(config), null);
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newsarticle")));
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatScriptsAreAppliedToSuperType() throws ParseException {
        DocumentTypeManager typeMgr = new DocumentTypeManager();
        typeMgr.configure("file:src/test/cfg/documentmanager_inherit.cfg");
        DocumentType docType = typeMgr.getDocumentType("newsarticle");
        assertNotNull(docType);

        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder().doctype("newsarticle")
                                                              .content("index"));
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(config), null);
        assertNotNull(scriptMgr.getScript(typeMgr.getDocumentType("newssummary")));
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatEmptyConfigurationDoesNotThrow() {
        DocumentTypeManager typeMgr = new DocumentTypeManager();
        typeMgr.configure("file:src/test/cfg/documentmanager_inherit.cfg");
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(new IlscriptsConfig.Builder()), null);
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }

    @Test
    public void requireThatUnknownDocumentTypeReturnsNull() {
        DocumentTypeManager typeMgr = new DocumentTypeManager();
        typeMgr.configure("file:src/test/cfg/documentmanager_inherit.cfg");
        ScriptManager scriptMgr = new ScriptManager(typeMgr, new IlscriptsConfig(new IlscriptsConfig.Builder()), null);
        for (Iterator<DocumentType> it = typeMgr.documentTypeIterator(); it.hasNext(); ) {
            assertNull(scriptMgr.getScript(it.next()));
        }
        assertNull(scriptMgr.getScript(new DocumentType("unknown")));
    }
}
