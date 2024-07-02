package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;


import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.parser.*;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.ParsedType.Variant;

public class SchemaDocumentParser {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;

    private String fileURI = "";
    private String content = "";
    
    private SchemaNode CST;
    private boolean faultySchema = true;

    public SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI) {
        this(logger, diagnosticsHandler, schemaIndex, fileURI, null);
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI, String content) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;
        if (content != null) {
            this.content = content;
            parseContent();
        };
    }

    public void updateFileContent(String content) {
        this.content = content;
        parseContent();
    }

    public String getFileURI() {
        return fileURI;
    }

    public String getFileName() {
        Integer splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(splitPos + 1);
    }


    public boolean isFaulty() {
        return faultySchema;
    }

    public Position getPreviousStartOfWord(Position pos) {
        int offset = positionToOffset(pos);

        // Skip whitespace
        while (offset >= 0 && Character.isWhitespace(content.charAt(offset)))offset--;

        for (int i = offset; i >= 0; i--) {
            if (Character.isWhitespace(content.charAt(i)))return offsetToPosition(i + 1);
        }

        return null;
    }

    public SchemaNode getRootNode() {
        return CST;
    }

    public SchemaNode getNodeAtOrBeforePosition(Position pos) {
        return getNodeAtPosition(CST, pos, false, true);
    }

    public SchemaNode getLeafNodeAtPosition(Position pos) {
        return getNodeAtPosition(CST, pos, true, false);
    }

    public SchemaNode getNodeAtPosition(Position pos) {
        return getNodeAtPosition(CST, pos, false, false);
    }


    private SchemaNode getNodeAtPosition(SchemaNode node, Position pos, boolean onlyLeaf, boolean findNearest) {
        if (node.isLeaf() && CSTUtils.positionInRange(node.getRange(), pos)) {
            return node;
        }

        if (!CSTUtils.positionInRange(node.getRange(), pos)) {
            if (findNearest && !onlyLeaf)return node;
            return null;
        }

        for (int i = 0; i < node.size(); i++) {
            SchemaNode child = node.get(i);

            if (CSTUtils.positionInRange(child.getRange(), pos)) {
                return getNodeAtPosition(child, pos, onlyLeaf, findNearest);
            }
        }

        if (onlyLeaf)return null;

        return node;

        /*
        Integer lowerLimit = 0;
        Integer upperLimit = node.size() - 1;

        Integer currentSearch = (upperLimit + lowerLimit) / 2;

        while (lowerLimit <= upperLimit) {
            SchemaNode search = node.get(currentSearch);

            if (CSTUtils.positionLT(pos, search.getRange().getEnd())) {

                if (CSTUtils.positionInRange(search.getRange(), pos)) {
                    return getNodeAtPosition(search, pos, onlyLeaf);
                }

                upperLimit = currentSearch - 1;
            } else {
                lowerLimit = currentSearch + 1;
            }

            currentSearch = (upperLimit + lowerLimit) / 2;
        }

        if (CSTUtils.positionInRange(node.getRange(), pos) && !onlyLeaf) {
            return node;
        }

        return null;
        */
    }

    private void parseContent() {
        CharSequence sequence = content;

        logger.println("Parsing document: " + fileURI);

        ParsedBlock.setCanIgnore(true);

        SchemaParser parserStrict = new SchemaParser(getFileName(), sequence);
        parserStrict.setParserTolerant(false);

        ArrayList<Diagnostic> errors = new ArrayList<Diagnostic>();

        try {

            ParsedSchema root = parserStrict.Root();
            faultySchema = false;
        } catch (ParseException e) {
            faultySchema = true;

            Node.TerminalNode node = e.getToken();

            Range range = CSTUtils.getNodeRange(node);

            errors.add(new Diagnostic(range, e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Complex error, invalidate the whole document

            errors.add(
                new Diagnostic(
                    new Range(
                        new Position(0, 0),
                        new Position((int)content.lines().count() - 1, 0)
                    ),
                    e.getMessage())
                );

            diagnosticsHandler.publishDiagnostics(fileURI, errors);
            
            return;
        }

        SchemaParser parserFaultTolerant = new SchemaParser(getFileName(), sequence);
        try {
            parserFaultTolerant.Root();
        } catch (ParseException e) {
            // Ignore
        } catch (IllegalArgumentException e) {
            // Ignore
        }

        Node node = parserFaultTolerant.rootNode();
        errors.addAll(parseCST(node));

        errors.addAll(findDirtyNode(node));

        //CSTUtils.printTree(logger, CST);

        diagnosticsHandler.publishDiagnostics(fileURI, errors);

        lexer.setCST(CST);
    }

    private static final HashMap<Token.TokenType, HashSet<String>> tokenParentClassPairs = new HashMap<Token.TokenType, HashSet<String>>() {{
        put(Token.TokenType.SCHEMA, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.rootSchema");
        }});
        put(Token.TokenType.DOCUMENT, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.documentElm");
        }});
        put(Token.TokenType.FIELD, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.fieldElm");
            add("ai.vespa.schemals.parser.ast.structFieldDefinition");
        }});
        put(Token.TokenType.FIELDSET, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.fieldSetElm");
        }});
        put(Token.TokenType.STRUCT, new HashSet<String>() {{
            add("ai.vespa.schemals.parser.ast.structDefinitionElm");
        }});
    }};

    private static final HashSet<Token.TokenType> depricatedTokens = new HashSet<Token.TokenType>() {{
        add(Token.TokenType.ENABLE_BIT_VECTORS);
    }};

    private ArrayList<Diagnostic> validateType(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        ParsedType type = ParsedType.fromName(node.getText());
        if (type.getVariant() == Variant.UNKNOWN) {
            ret.add(new Diagnostic(node.getRange(), "Invalid type"));
        } else {
            node.setSchemaType();
        }

        return ret;
    }

    private ArrayList<Diagnostic> traverseCST(SchemaNode node) {

        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();
        
        // Override if we think there will be an identifier next token
        SchemaNode parent = node.getParent();
        Token.TokenType nodeType = node.getType();
        HashSet<String> parentCNComp = tokenParentClassPairs.get(nodeType);
        if (
            parent != null &&
            parent.get(0) == node &&
            parentCNComp != null &&
            parentCNComp.contains(parent.getIdentifierString()) &&
            parent.size() > 1
        ) {
            SchemaNode child = parent.get(1);
            child.setUserDefinedIdentifier();
            if (schemaIndex.findSymbol(fileURI, nodeType, child.getText()) == null) {
                schemaIndex.insert(fileURI, nodeType, child.getText(), child);
            } else {
                ret.add(new Diagnostic(child.getRange(), "Duplicate identifier"));
            }

        }

        // Check if we will parse a type
        if (
            node.getType() == Token.TokenType.TYPE &&
            parent != null &&
            parent.size() > parent.indexOf(node)
        ) {
            SchemaNode child = parent.get(parent.indexOf(node) + 1);

            // Check if it uses deprecated array
            if (
                child.getClassLeafIdentifierString().equals("dataType") &&
                child.size() > 1 &&
                child.get(1).getText().equals("[]")
            ) {
                Range range = child.getRange();

                child = child.get(0);

                ret.add(new Diagnostic(range, "Data type syntax '" + child.getText() + "[]' is deprecated, use 'array<" + child.getText() + ">' instead.", DiagnosticSeverity.Warning,""));
            }

            // Check if type is valid
            if (
                child.size() > 2 &&
                child.get(1).getType() == Token.TokenType.LESSTHAN
            ) {
                Token.TokenType firstChildType = child.get(0).getType();
                if (
                    firstChildType != Token.TokenType.ANNOTATIONREFERENCE &&
                    firstChildType != Token.TokenType.REFERENCE
                ) {
                    for (int i = 2; i < child.size(); i += 2) {
                        ret.addAll(validateType(child.get(i)));
                    }
                }
            } else if (child.getType() != Token.TokenType.TENSOR_TYPE) {
                ret.addAll(validateType(child));
                child.setType(null);
            }

        }

        // Check for symbol references
        if (
            node.getType() == Token.TokenType.FIELDS &&
            parent != null &&
            parent.getIdentifierString() == "ai.vespa.schemals.parser.ast.fieldsElm"
        ) {
            for (int i = 2; i < parent.size(); i += 2) {
                SchemaNode child = parent.get(i);

                if (child.getType() == Token.TokenType.COMMA) {
                    ret.add(new Diagnostic(child.getRange(), "Unexcpeted ',', expected an identifier."));
                    break;
                }

                if (child.getText() != "") {
                    if (schemaIndex.findSymbol(fileURI, Token.TokenType.FIELD, child.getText()) == null) {
                        ret.add(new Diagnostic(child.getRange(), "Cannot find symbol: " + child.getText()));
                    } else {
                        child.setUserDefinedIdentifier();
                    }
                }

                if (i + 1 < parent.size()) {
                    if (parent.get(i + 1).getType() != Token.TokenType.COMMA) {
                        ret.add(new Diagnostic(parent.get(i + 1).getRange(), "Unexpected token, expected ','"));
                        break;
                    }
                }
            }
        }

        // Check if token is depricated
        if (depricatedTokens.contains(node.getType())) {
            ret.add(
                new Diagnostic(node.getRange(), "Depricated", DiagnosticSeverity.Warning, "")
            );
        }

        for (int i = 0; i < node.size(); i++) {
            ret.addAll(traverseCST(node.get(i)));
        }

        return ret;
    }

    private ArrayList<Diagnostic> parseCST(Node node) {
        schemaIndex.clearDocument(fileURI);
        CST = new SchemaNode(node);
        if (node == null) {
            return new ArrayList<Diagnostic>();
        }
        return traverseCST(CST);
    }


    private ArrayList<Diagnostic> findDirtyNode(Node node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        for (Node child : node) {
            ret.addAll(findDirtyNode(child));
        }

        if (node.isDirty() && ret.size() == 0) {
            Range range = CSTUtils.getNodeRange(node);

            ret.add(new Diagnostic(range, "Dirty Node"));
        }


        return ret;
    }

    /*
     * If necessary, the following methods can be sped up by
     * selecting an appropriate data structure.
     * */
    private int positionToOffset(Position pos) {
        List<String> lines = content.lines().toList();
        if (pos.getLine() >= lines.size())throw new IllegalArgumentException("Line " + pos.getLine() + " out of range for document " + fileURI);

        int lineCounter = 0;
        int offset = 0;
        for (String line : lines) {
            if (lineCounter == pos.getLine())break;
            offset += line.length() + 1; // +1 for line terminator
            lineCounter += 1;
        }

        if (pos.getCharacter() > lines.get(pos.getLine()).length())throw new IllegalArgumentException("Character " + pos.getCharacter() + " out of range for line " + pos.getLine());

        offset += pos.getCharacter();

        return offset;
    }

    private Position offsetToPosition(int offset) {
        List<String> lines = content.lines().toList();
        int lineCounter = 0;
        for (String line : lines) {
            int lengthIncludingTerminator = line.length() + 1;
            if (offset < lengthIncludingTerminator) {
                return new Position(lineCounter, offset);
            }
            offset -= lengthIncludingTerminator;
            lineCounter += 1;
        }
        return null;
    }
}
