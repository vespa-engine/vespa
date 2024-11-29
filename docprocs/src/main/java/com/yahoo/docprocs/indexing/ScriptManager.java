// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.language.Linguistics;

import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Generator;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptManager {

    private static final String FULL = "[all]";
    private final Map<String, Map<String, DocumentScript>> documentFieldScripts;
    private final DocumentTypeManager documentTypeManager;

    public ScriptManager(DocumentTypeManager documentTypeManager, IlscriptsConfig config, Linguistics linguistics,
                         Map<String, Embedder> embedders, Map<String, Generator> generators) {
        this.documentTypeManager = documentTypeManager;
        documentFieldScripts = createScriptsMap(documentTypeManager, config, linguistics, embedders, generators);
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
    private static Map<String, Map<String, DocumentScript>>  createScriptsMap(DocumentTypeManager documentTypes,
                                                                              IlscriptsConfig config,
                                                                              Linguistics linguistics,
                                                                              Map<String, Embedder> embedders,
                                                                              Map<String, Generator> generators) {
        Map<String, Map<String, DocumentScript>> documentFieldScripts = new HashMap<>(config.ilscript().size());
        ScriptParserContext parserContext = new ScriptParserContext(linguistics, embedders, generators);
        parserContext.getAnnotatorConfig().setMaxTermOccurrences(config.maxtermoccurrences());
        parserContext.getAnnotatorConfig().setMaxTokenizeLength(config.fieldmatchmaxlength());

        for (IlscriptsConfig.Ilscript ilscript : config.ilscript()) {
            DocumentType documentType = documentTypes.getDocumentType(ilscript.doctype());
            InputExpression.FieldPathOptimizer fieldPathOptimizer = new InputExpression.FieldPathOptimizer(documentType);
            List<StatementExpression> allStatements = new ArrayList<>(ilscript.content().size());
            Map<String, DocumentScript> fieldScripts = new HashMap<>(ilscript.content().size());
            for (String content : ilscript.content()) {
                StatementExpression statement = parse(documentType, parserContext, content);
                allStatements.add(statement);
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
                    ScriptExpression fieldScript;
                    if (fieldScripts.containsKey(fieldName)) {
                        DocumentScript existing = fieldScripts.get(fieldName);
                        List<StatementExpression> appendedList = new ArrayList<>(((ScriptExpression)existing.getExpression()).asList());
                        appendedList.add(statement);
                        fieldScript = new ScriptExpression(appendedList);
                    } else {
                        fieldScript = new ScriptExpression(statement);
                    }
                    fieldScripts.put(fieldName, new DocumentScript(documentType, inputFieldNames, fieldScript));
                }
            }

            var script = new ScriptExpression(allStatements);
            script.select(fieldPathOptimizer, fieldPathOptimizer);
            fieldScripts.put(FULL, new DocumentScript(documentType, ilscript.docfield(), script));
            documentFieldScripts.put(ilscript.doctype(), Collections.unmodifiableMap(fieldScripts));
        }
        return Collections.unmodifiableMap(documentFieldScripts);
    }

    private static StatementExpression parse(DocumentType type, ScriptParserContext parserConfig, String content) {
        try {
            parserConfig.setInputStream(new IndexingInput(content));
            return StatementExpression.newInstance(parserConfig);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Illegal indexing script for " + type, e);
        }
    }

}
