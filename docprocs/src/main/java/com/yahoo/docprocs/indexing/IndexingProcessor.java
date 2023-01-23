// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import java.util.ArrayList;
import java.util.List;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.provider.DefaultEmbedderProvider;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.indexinglanguage.AdapterFactory;
import com.yahoo.vespa.indexinglanguage.SimpleAdapterFactory;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import java.util.Map;
import java.util.stream.Collectors;

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
    public IndexingProcessor(DocumentTypeManager documentTypeManager,
                             IlscriptsConfig ilscriptsConfig,
                             Linguistics linguistics,
                             ComponentRegistry<Embedder> embedders) {
        docTypeMgr = documentTypeManager;
        scriptMgr = new ScriptManager(docTypeMgr, ilscriptsConfig, linguistics, toMap(embedders));
        adapterFactory = new SimpleAdapterFactory(new ExpressionSelector());
    }

    @Override
    public Progress process(Processing proc) {
        if (proc.getDocumentOperations().isEmpty()) return Progress.DONE;

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

    private void processDocument(DocumentPut input, List<DocumentOperation> out) {
        DocumentType hadType = input.getDocument().getDataType();
        DocumentScript script = scriptMgr.getScript(hadType);
        if (script == null) {
            out.add(input);
            return;
        }
        DocumentType wantType = docTypeMgr.getDocumentType(hadType.getName());
        Document inputDocument = input.getDocument();
        if (hadType != wantType) {
            // this happens when you have a concrete document; we need to
            // convert back to a "normal" Document for indexing of complex structures
            // to work properly.
            GrowableByteBuffer buffer = new GrowableByteBuffer(64 * 1024, 2.0f);
            DocumentSerializer serializer = DocumentSerializerFactory.createHead(buffer);
            serializer.write(inputDocument);
            buffer.flip();
            inputDocument = docTypeMgr.createDocument(buffer);
        }
        Document output = script.execute(adapterFactory, inputDocument);
        if (output == null) return;

        out.add(new DocumentPut(input, output));
    }

    private void processUpdate(DocumentUpdate input, List<DocumentOperation> out) {
        DocumentScript script = scriptMgr.getScript(input.getType());
        if (script == null) {
            out.add(input);
            return;
        }
        DocumentUpdate output = script.execute(adapterFactory, input);
        if (output == null) return;
        output.setCondition(input.getCondition());
        out.add(output);
    }

    private void processRemove(DocumentRemove input, List<DocumentOperation> out) {
        out.add(input);
    }

    private Map<String, Embedder> toMap(ComponentRegistry<Embedder> embedders) {
        var map = embedders.allComponentsById().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().stringValue(), Map.Entry::getValue));
        if (map.size() > 1) {
            map.remove(DefaultEmbedderProvider.class.getName());
            // Ideally, this should be handled by dependency injection, however for now this workaround is necessary.
        }
        return map;
    }

}
