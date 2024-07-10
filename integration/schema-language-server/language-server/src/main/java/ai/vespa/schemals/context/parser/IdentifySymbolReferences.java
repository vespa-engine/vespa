package ai.vespa.schemals.context.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.tensor.functions.Diag;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifySymbolReferences extends Identifier {

    public IdentifySymbolReferences(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<? extends Node>, SymbolType> identifierTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(inheritsDocument.class, SymbolType.DOCUMENT);
        put(fieldsElm.class, SymbolType.FIELD);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(inheritsStruct.class, SymbolType.STRUCT);
    }};

    private static final HashMap<Class<? extends Node>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(inheritsRankProfile.class, SymbolType.RANK_PROFILE);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (node.hasSymbol()) return ret;

        boolean isIdentifier = node.isASTInstance(identifierStr.class);
        boolean isIdentifierWithDash = node.isASTInstance(identifierWithDashStr.class);

        if (!isIdentifier && !isIdentifierWithDash) return ret;

        SchemaNode parent = node.getParent();
        if (parent == null) return ret;

        HashMap<Class<? extends Node>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType == null) return ret;

        if (parent.isASTInstance(fieldsElm.class)) {
            return handleFields(node);
        }

        node.setSymbol(symbolType, context.fileURI());
        node.setSymbolStatus(SymbolStatus.UNRESOLVED);

        return ret;
    }

    private ArrayList<Diagnostic> handleFields(SchemaNode identifierNode) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        SchemaNode parent = identifierNode.getParent();

        String fieldIdentifier = identifierNode.getText();

        String[] subfields = fieldIdentifier.split("[.]");

        int newStart = identifierNode.getRange().getStart().getCharacter();

        // First item in the list should be of type field
        int newEnd = newStart + subfields[0].length();

        identifierNode.setNewEndCharacter(newEnd);

        if (identifierNode.size() != 0) {
            identifierNode.get(0).setNewEndCharacter(newEnd);
        }

        identifierNode.setSymbol(SymbolType.FIELD, context.fileURI());
        identifierNode.setSymbolStatus(SymbolStatus.UNRESOLVED);

        int myIndex = parent.indexOf(identifierNode);
        for (int i = 1; i < subfields.length; ++i) {
            newStart += subfields[i-1].length() + 1; // +1 for the dot
            newEnd += subfields[i].length() + 1;

            identifierStr newASTNode = new identifierStr();
            newASTNode.setTokenSource(identifierNode.getTokenSource());
            newASTNode.setBeginOffset(identifierNode.getOriginalNode().getBeginOffset());
            newASTNode.setEndOffset(identifierNode.getOriginalNode().getEndOffset());

            SchemaNode newNode = new SchemaNode(newASTNode, parent);
            newNode.setNewStartCharacter(newStart);
            newNode.setNewEndCharacter(newEnd);

            newNode.setSymbol(SymbolType.SUBFIELD, context.fileURI());
            newNode.setSymbolStatus(SymbolStatus.UNRESOLVED);

            parent.insertChildAfter(myIndex, newNode);
            myIndex++;
        }

        return ret;
    }
}
