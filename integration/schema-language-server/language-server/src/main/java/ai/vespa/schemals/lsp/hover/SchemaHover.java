package ai.vespa.schemals.lsp.hover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.SchemaLanguageServer;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.completion.SchemaCompletion;
import ai.vespa.schemals.parser.indexinglanguage.ast.ATTRIBUTE;
import ai.vespa.schemals.parser.indexinglanguage.ast.INDEX;
import ai.vespa.schemals.parser.indexinglanguage.ast.SUMMARY;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.SpecificFunction;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * Responsible for LSP textDocument/hover requests.
 *
 * As a TODO it would have been nice with some highlighting on the generated Markdown.
 */
public class SchemaHover {
    private static final String markdownPathRoot = "hover/";

    private static Map<String, Optional<MarkupContent>> markdownContentCache = new HashMap<>();

    /**
     * Helper to check if a comment exists and extract it from a line
     * @param documentContent
     * @param lineEndOffset offset at which the \n following this line occurs
     * @return the string containing the comment, not including leading or trailing whitespace if found
     */
    private static Optional<String> getCommentOnLine(String documentContent, int lineEndOffset) {
        int prevLine = documentContent.lastIndexOf("\n", lineEndOffset - 1);

        // note: if prevLine == -1 (not found), we should correctly extract from offset 0
        String possibleCommentString = documentContent.substring(prevLine + 1, lineEndOffset).trim();

        if (!possibleCommentString.startsWith("#")) return Optional.empty();
        return Optional.of(possibleCommentString);
    }

    /**
     * Some definitions might have useful comments by them.
     * This function takes a node which should point to some node where a symbol is defined,
     * and returns optionally a string containing all contiguous comments above the definition, joined by newline.
     */
    private static Optional<String> getSymbolDocumentationComments(SchemaNode node, String documentContent) {
        int offset = node.getOriginalBeginOffset();
        if (offset == -1) return Optional.empty();

        int linePointer = documentContent.lastIndexOf("\n", offset);

        List<String> comments = new LinkedList<>();
        while (linePointer != -1) {
            Optional<String> comment = getCommentOnLine(documentContent, linePointer);
            if (comment.isEmpty()) break;

            comments.add(0, comment.get());
            linePointer = documentContent.lastIndexOf("\n", linePointer - 1);
        }

        if (comments.isEmpty()) return Optional.empty();

        return Optional.of(String.join("\n", comments));
    }

    /**
     * Special handling of struct over to list the fields inside and where they are inherited from.
     */
    private static Hover getStructHover(SchemaNode node, EventPositionContext context) {

        Optional<Symbol> structDefinitionSymbol = context.schemaIndex.getSymbolDefinition(node.getSymbol());
        
        if (structDefinitionSymbol.isEmpty()) {
            return null;
        }
        List<Symbol> fieldDefs = context.schemaIndex.listSymbolsInScope(structDefinitionSymbol.get(), SymbolType.FIELD);

        String result = "### struct " + structDefinitionSymbol.get().getShortIdentifier() + "\n";
        for (Symbol fieldDef : fieldDefs) {
            result += "    field " + fieldDef.getShortIdentifier() + " type " +  
                fieldDef.getNode().getNextSibling().getNextSibling().getText();

            if (!fieldDef.isInScope(structDefinitionSymbol.get())) {
                result += " (inherited from " + fieldDef.getScope().getPrettyIdentifier() + ")";
            }

            result += "\n";
        }

        if (result.isEmpty())result = "no fields found";

        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, result));
    }

    /**
     * Special handling of field hover to also list which indexing settings are set (index | attribute | summary)
     * as well as whether the field is inside or outside its document.
     */
    private static Hover getFieldHover(SchemaNode node, EventPositionContext context) {
        Optional<Symbol> fieldDefinitionSymbol = context.schemaIndex.getSymbolDefinition(node.getSymbol());

        if (fieldDefinitionSymbol.isEmpty()) return null;

        Optional<SchemaNode> dataTypeNode = context.schemaIndex.fieldIndex().getFieldDataTypeNode(fieldDefinitionSymbol.get());
        
        String typeText = "unknown";
        if (dataTypeNode.isPresent()) {
            typeText = dataTypeNode.get().getText().trim();
        }

        // Create a nice long identifier for nested fields (e.g. field of type struct)
        String fieldIdentifier = fieldDefinitionSymbol.get().getShortIdentifier();
        Symbol scopeIterator = fieldDefinitionSymbol.get().getScope();
        while (scopeIterator != null && scopeIterator.getType() == SymbolType.FIELD) {
            fieldIdentifier = scopeIterator.getShortIdentifier() + "." + fieldIdentifier;
            scopeIterator = scopeIterator.getScope();
        }

        String hoverText = "field " + fieldIdentifier + " type " + typeText;

        if (context.schemaIndex.fieldIndex().getIsInsideDoc(fieldDefinitionSymbol.get())) {
            if (fieldDefinitionSymbol.get().getScope() != null && fieldDefinitionSymbol.get().getScope().getType() == SymbolType.STRUCT) {
                hoverText += " (in struct " + fieldDefinitionSymbol.get().getScope().getShortIdentifier() + ")";
            } else {
                hoverText += " (in document)";
            }
        } else {
            hoverText += " (outside document)";
        }

        var indexingTypes = context.schemaIndex.fieldIndex().getFieldIndexingTypes(fieldDefinitionSymbol.get()).toArray();
        // We simulate some kind of indexing statement
        for (int i = 0; i < indexingTypes.length; ++i) {
            if (i == 0) hoverText += "\n\t";
            else hoverText += " | ";
            hoverText += indexingTypes[i].toString().toLowerCase();
        }

        String fieldDefinitionDocumentContent = context.scheduler.getDocument(fieldDefinitionSymbol.get().getFileURI()).getCurrentContent();
        Optional<String> documentationComments = getSymbolDocumentationComments(fieldDefinitionSymbol.get().getNode(), fieldDefinitionDocumentContent);

        if (documentationComments.isPresent()) {
            hoverText = documentationComments.get() + "\n" + hoverText;
        }

        // Render as code
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, "```\n" + hoverText + "\n```"));
    }

    private static Hover getSymbolHover(SchemaNode node, EventPositionContext context) {
        switch(node.getSymbol().getType()) {
            case STRUCT:
                return getStructHover(node, context);
            case FIELD:
                return getFieldHover(node, context);
            default:
                break;
        }

        if (node.getSymbol().getType() == SymbolType.LABEL) {
            return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, "label"));
        }

        if (node.getSymbol().getStatus() == SymbolStatus.BUILTIN_REFERENCE) {
            return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, "builtin"));
        }

        if (node.getSymbol().getStatus() == SymbolStatus.REFERENCE || node.getSymbol().getStatus() == SymbolStatus.DEFINITION) {
            Optional<Symbol> definition = context.schemaIndex.getSymbolDefinition(node.getSymbol());

            if (definition.isPresent()) {
                SchemaNode definitionNode = definition.get().getNode();

                if (definitionNode.getParent() != null) {
                    String text = definitionNode.getParent().getText();
                    String[] lines = text.split(System.lineSeparator());
                    String hoverContent = lines[0].trim();
                    while (hoverContent.length() > 0 && hoverContent.endsWith("{")) {
                        hoverContent = hoverContent.substring(0, hoverContent.length() - 1);
                    }
                    hoverContent = hoverContent.trim();
                    String documentContent = context.scheduler.getDocument(definition.get().getFileURI()).getCurrentContent();
                    Optional<String> documentationComments = getSymbolDocumentationComments(definitionNode, documentContent);

                    if (documentationComments.isPresent()) {
                        hoverContent = documentationComments.get() + "\n" + hoverContent;
                    }
                    if (hoverContent.length() > 0) {
                        // Render as code
                        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, "```\n" + hoverContent + "\n```"));
                    }
                }
            }
        }

        return null;
    }

    private static Hover getIndexingHover(SchemaNode hoverNode, EventPositionContext context) {
        // Taken from:
        // https://docs.vespa.ai/en/reference/schema-reference.html#indexing
        // Too specific to include in buildDocs.py
        
        // Note: these classes belong to the indexinglanguage parser
        if (hoverNode.isASTInstance(ATTRIBUTE.class)) {
            return new Hover(new MarkupContent(MarkupKind.MARKDOWN, 
                "## Attribute\n"
            +   "Attribute is used to make a field available for sorting, grouping, ranking and searching using match mode `word`.\n"
            ));
        } else if (hoverNode.isASTInstance(INDEX.class)) {
            return new Hover(new MarkupContent(MarkupKind.MARKDOWN, 
                "## Index\n"
            +   "Creates a searchable index for the values of this field using match mode `text`. "
            +   "All strings are lower-cased before stored in the index. "
            +   "By default, the index name will be the same as the name of the schema field. "
            +   "Use a fieldset to combine fields in the same set for searching.\n"
            ));
        } else if (hoverNode.isASTInstance(SUMMARY.class)) {
            return new Hover(new MarkupContent(MarkupKind.MARKDOWN, 
                "## Summary\n"
            +   "Includes the value of this field in a summary field. "
            +   "Summary fields of type string are limited to 64 kB. "
            +   "If a larger string is stored, the indexer will issue a warning and truncate the value to 64 kB. "
            +   "Modify summary output by using `summary:` in the field body (e.g. to generate dynamic teasers).\n"
            ));
        }

        return null;
    }

    private static Optional<Hover> rankFeatureHover(SchemaNode node, EventPositionContext context) {

        // Search for rankNode connection
        SchemaNode currentNode = node;
        while (currentNode.getLanguageType() == LanguageType.RANK_EXPRESSION && currentNode.getRankNode().isEmpty()) {
            currentNode = currentNode.getParent();
        }

        if (currentNode.getRankNode().isEmpty()) {
            return Optional.empty();
        }

        RankNode rankNode = currentNode.getRankNode().get();

        Optional<SpecificFunction> rankNodeSignature = rankNode.getFunctionSignature();

        if (rankNodeSignature.isEmpty()) {
            return Optional.empty();
        }

        SpecificFunction functionSignature = rankNodeSignature.get().clone();

        // Clear property from the signature if the hover was not explicitly on the property node.
        if (node.hasSymbol() && node.getSymbol().getType() != SymbolType.PROPERTY) {
            functionSignature.clearProperty();
        }

        Optional<Hover> result = getRankFeatureHover(functionSignature);
        result.ifPresent(hover -> hover.setRange(node.getRange()));
        return result;
    }

    /**
     * Public interface to be used by {@link SchemaCompletion}
     * @param function
     * @return Hover with empty range
     */
    public static Optional<Hover> getRankFeatureHover(SpecificFunction function) {
        Optional<Hover> result = getFileHoverInformation("rankExpression/" + function.getSignatureString(), new Range());

        if (result.isPresent()) {
            return result;
        }

        return getFileHoverInformation("rankExpression/" + function.getSignatureString(true), new Range());
    }

    public static Hover getHover(EventPositionContext context) {
        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);

        if (node != null) {
            Hover symbolHover = getSymbolHover(node, context);

            if (node.getLanguageType() == LanguageType.RANK_EXPRESSION) {
                var content = symbolHover.getContents();
                if (content.isRight()) {
                    MarkupContent markupContent = content.getRight();
                    if (markupContent.getValue() == "builtin") {

                        Optional<Hover> builtinHover = rankFeatureHover(node, context);
                        if (builtinHover.isPresent()) {
                            return builtinHover.get();
                        }
                    }
                }
            }

            return symbolHover;
        }

        node = CSTUtils.getLeafNodeAtPosition(context.document.getRootNode(), context.position);
        if (node == null) return null;

        if (node.getLanguageType() == LanguageType.INDEXING) {
            return getIndexingHover(node, context);
        }

        Optional<Hover> hoverInfo = getFileHoverInformation("schema/" + node.getClassLeafIdentifierString(), node.getRange());
        if (hoverInfo.isEmpty()) {
            return null;
        }
        return hoverInfo.get();
    }

    public static Optional<Hover> getFileHoverInformation(String markdownKey, Range range) {
        // avoid doing unnecessary IO operations
        if (markdownContentCache.containsKey(markdownKey)) {
            Optional<MarkupContent> mdContent = markdownContentCache.get(markdownKey);
            if (mdContent != null) {
                if (mdContent.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new Hover(mdContent.get(), range));
            }
        }

        if (SchemaLanguageServer.serverPath == null)return Optional.empty();
        String fileName = markdownKey + ".md";

        Path markdownPath = SchemaLanguageServer.serverPath.resolve("hover").resolve(fileName);

        try {
            String markdown = Files.readString(markdownPath);

            MarkupContent mdContent = new MarkupContent(MarkupKind.MARKDOWN, markdown);
            Hover hover = new Hover(mdContent, range);
            markdownContentCache.put(markdownKey, Optional.of(mdContent));
            return Optional.of(hover);

        } catch(IOException ex) {
            return Optional.empty();
        }
    }
}
