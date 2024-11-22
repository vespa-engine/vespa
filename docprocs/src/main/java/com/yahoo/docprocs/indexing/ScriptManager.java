// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.language.Linguistics;
import java.util.logging.Level;

import com.yahoo.language.process.Embedder;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.*;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptManager {

    private static final FastLogger log = FastLogger.getLogger(ScriptManager.class.getName());
    private static final String FULL = "[all]";
    private final Map<String, Map<String, DocumentScript>> documentFieldScripts;
    private final DocumentTypeManager documentTypeManager;

    public ScriptManager(DocumentTypeManager documentTypeManager, IlscriptsConfig config, Linguistics linguistics,
                         Map<String, Embedder> embedders) {
        this.documentTypeManager = documentTypeManager;
        documentFieldScripts = createScriptsMap(documentTypeManager, config, linguistics, embedders);
    }

    private Map<String, DocumentScript> getScripts(DocumentType inputType) {
        Map<String, DocumentScript> scripts = documentFieldScripts.get(inputType.getName());
        if (scripts != null) return scripts;
        for (Map.Entry<String, Map<String, DocumentScript>> entry : documentFieldScripts.entrySet()) {
            if (inputType.inherits(documentTypeManager.getDocumentType(entry.getKey())))
                return entry.getValue();
        }
        for (Map.Entry<String, Map<String, DocumentScript>> entry : documentFieldScripts.entrySet()) {
            if (documentTypeManager.getDocumentType(entry.getKey()).inherits(inputType))
                return entry.getValue();
        }
        return null;
    }

    public DocumentScript getScript(DocumentType inputType) {
        return getScript(inputType, FULL);
    }

    public DocumentScript getScript(DocumentType inputType, String inputFieldName) {
        Map<String, DocumentScript> fieldScripts = getScripts(inputType);
        if (fieldScripts != null) {
            DocumentScript script = fieldScripts.get(inputFieldName);
            if (script != null) return script;
        }
        return null;
    }

    /**
     * Returns an unmodifiable map from document type name to a map of the subset of indexing statements
     * to run for each input field which *only* depend on that field.
     */
    private static Map<String, Map<String, DocumentScript>>  createScriptsMap(DocumentTypeManager docTypeMgr,
                                                                              IlscriptsConfig config,
                                                                              Linguistics linguistics,
                                                                              Map<String, Embedder> embedders) {
        Map<String, Map<String, DocumentScript>> documentFieldScripts = new HashMap<>(config.ilscript().size());
        ScriptParserContext parserContext = new ScriptParserContext(linguistics, embedders);
        parserContext.getAnnotatorConfig().setMaxTermOccurrences(config.maxtermoccurrences());
        parserContext.getAnnotatorConfig().setMaxTokenizeLength(config.fieldmatchmaxlength());

        for (IlscriptsConfig.Ilscript ilscript : config.ilscript()) {
            DocumentType documentType = docTypeMgr.getDocumentType(ilscript.doctype());
            InputExpression.FieldPathOptimizer fieldPathOptimizer = new InputExpression.FieldPathOptimizer(documentType);
            List<StatementExpression> expressions = new ArrayList<>(ilscript.content().size());
            Map<String, DocumentScript> fieldScripts = new HashMap<>(ilscript.content().size());
            for (String content : ilscript.content()) {
                StatementExpression statement = parse(ilscript.doctype(), parserContext, content);
                expressions.add(statement);
                List<String> inputFieldNames = InputExpression.InputFieldNameExtractor.runOn(statement);
                OutputExpression.OutputFieldNameExtractor outputFieldNameExtractor = new OutputExpression.OutputFieldNameExtractor();
                statement.select(outputFieldNameExtractor, outputFieldNameExtractor);
                statement.select(fieldPathOptimizer, fieldPathOptimizer);
                if ( ! outputFieldNameExtractor.getOutputFieldNames().isEmpty()) {
                    String outputFieldName = outputFieldNameExtractor.getOutputFieldNames().get(0);
                    statement.setStatementOutput(documentType, documentType.getField(outputFieldName));
                }
                if (inputFieldNames.size() == 1) {
                    String fieldName = inputFieldNames.get(0);
                    ScriptExpression script;
                    if (fieldScripts.containsKey(fieldName)) {
                        DocumentScript prev = fieldScripts.get(fieldName);
                        List<StatementExpression> appendedList = new ArrayList<>(((ScriptExpression)prev.getExpression()).asList());
                        appendedList.add(statement);
                        script = new ScriptExpression(appendedList);
                    } else {
                        script = new ScriptExpression(statement);
                    }
                    DocumentScript documentScript = new DocumentScript(ilscript.doctype(), inputFieldNames, script);
                    fieldScripts.put(fieldName, documentScript);
                } else {
                    log.log(Level.FINE, "Non single(" + inputFieldNames.size() +
                                        ") inputs = " + inputFieldNames + ". Script = " + statement);
                }
            }

            ScriptExpression script = new ScriptExpression(expressions);
            script.select(fieldPathOptimizer, fieldPathOptimizer);
            fieldScripts.put(FULL, new DocumentScript(ilscript.doctype(), ilscript.docfield(), script));
            documentFieldScripts.put(ilscript.doctype(), Collections.unmodifiableMap(fieldScripts));
        }
        return Collections.unmodifiableMap(documentFieldScripts);
    }

    private static StatementExpression parse(String docType, ScriptParserContext parserConfig, String content) {
        parserConfig.setInputStream(new IndexingInput(content));
        try {
            return StatementExpression.newInstance(parserConfig);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Illegal indexing script for document type '" +
                                               docType + "'; " + content, e);
        }
    }
}
