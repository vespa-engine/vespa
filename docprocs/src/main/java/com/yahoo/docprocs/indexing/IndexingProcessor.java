// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import java.util.ArrayList;
import java.util.List;
import com.google.inject.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.*;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.language.Linguistics;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.indexinglanguage.AdapterFactory;
import com.yahoo.vespa.indexinglanguage.SimpleAdapterFactory;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;

/**
 * @author Simon Thoresen Hult
 */
@Provides({ IndexingProcessor.PROVIDED_NAME })
@Before({ IndexingProcessor.INDEXING_END })
@After({ IndexingProcessor.INDEXING_START, "*" })
public class IndexingProcessor extends DocumentProcessor {

    public final static String PROVIDED_NAME = "indexedDocument";
    public final static String INDEXING_START = "indexingStart";
    public final static String INDEXING_END = "indexingEnd";

    private final static FastLogger log = FastLogger.getLogger(IndexingProcessor.class.getName());
    private final DocumentTypeManager docTypeMgr;
    private final ScriptManager scriptMgr;
    private final AdapterFactory adapterFactory;

    private class ExpressionSelector extends SimpleAdapterFactory.SelectExpression {
        @Override
        public Expression selectExpression(DocumentType documentType, String fieldName) {
            return scriptMgr.getScript(documentType, fieldName).getExpression();
        }
    }

    @Inject
    public IndexingProcessor(DocumentmanagerConfig documentmanagerConfig,
                             IlscriptsConfig ilscriptsConfig,
                             Linguistics linguistics) {
        docTypeMgr = DocumentTypeManagerConfigurer.configureNewManager(documentmanagerConfig);
        scriptMgr = new ScriptManager(docTypeMgr, ilscriptsConfig, linguistics);
        adapterFactory = new SimpleAdapterFactory(new ExpressionSelector());
    }

    @Override
    public Progress process(Processing proc) {
        if (proc.getDocumentOperations().isEmpty()) {
            return Progress.DONE;
        }
        List<DocumentOperation> out = new ArrayList<>(proc.getDocumentOperations().size());
        for (DocumentOperation documentOperation : proc.getDocumentOperations()) {
            if (documentOperation instanceof DocumentPut) {
                processDocument((DocumentPut)documentOperation, out);
            } else if (documentOperation instanceof DocumentUpdate) {
                processUpdate((DocumentUpdate)documentOperation, out);
            } else if (documentOperation instanceof DocumentRemove) {
                processRemove((DocumentRemove)documentOperation, out);
            } else if (documentOperation != null) {
                throw new IllegalArgumentException("Document class " + documentOperation.getClass().getName() + " not supported.");
            } else {
                throw new IllegalArgumentException("Expected document, got null.");
            }
        }
        proc.getDocumentOperations().clear();
        proc.getDocumentOperations().addAll(out);
        return Progress.DONE;
    }

    DocumentTypeManager getDocumentTypeManager() {
        return docTypeMgr;
    }

    private void processDocument(DocumentPut prev, List<DocumentOperation> out) {
        DocumentScript script = scriptMgr.getScript(prev.getDocument().getDataType());
        if (script == null) {
            log.log(LogLevel.DEBUG, "No indexing script for document '%s'.", prev.getId());
            out.add(prev);
            return;
        }
        log.log(LogLevel.DEBUG, "Processing document '%s'.", prev.getId());
        Document next = script.execute(adapterFactory, prev.getDocument());
        if (next == null) {
            log.log(LogLevel.DEBUG, "Document '" + prev.getId() + "' produced no output.");
            return;
        }

        out.add(new DocumentPut(prev, next));
    }

    private void processUpdate(DocumentUpdate prev, List<DocumentOperation> out) {
        DocumentScript script = scriptMgr.getScript(prev.getType());
        if (script == null) {
            log.log(LogLevel.DEBUG, "No indexing script for update '%s'.", prev.getId());
            out.add(prev);
            return;
        }
        log.log(LogLevel.DEBUG, "Processing update '%s'.", prev.getId());
        DocumentUpdate next = script.execute(adapterFactory, prev);
        if (next == null) {
            log.log(LogLevel.DEBUG, "Update '" + prev.getId() + "' produced no output.");
            return;
        }
        next.setCondition(prev.getCondition());
        out.add(next);
    }

    private void processRemove(DocumentRemove prev, List<DocumentOperation> out) {
        log.log(LogLevel.DEBUG, "Not processing remove '%s'.", prev.getId());
        out.add(prev);
    }

}
