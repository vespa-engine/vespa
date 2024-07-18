package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.SymbolTag;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.identifierStr;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class GenericFunction implements FunctionHandler {
    
    List<FunctionSignature> signatures;
    Set<String> properties;

    public GenericFunction(List<FunctionSignature> signatures) {
        this.signatures = signatures;
        this.properties = new HashSet<>();

        for (FunctionSignature signature : signatures) {
            properties.addAll(signature.getProperties());
        }
    }

    public GenericFunction(FunctionSignature signature) {
        this(new ArrayList<>() {{
            add(signature);
        }});
    }

    public GenericFunction(Argument argument, Set<String> properties) {
        this(new FunctionSignature(argument, properties));
    }

    public GenericFunction(List<Argument> arguments, Set<String> proerties) {
        this(new FunctionSignature(arguments, proerties));
    }

    public GenericFunction() {
        this(new ArrayList<>());
    }

    public static GenericFunction singleSymbolArugmnet(SymbolType symbolType) {
        return new GenericFunction(new FunctionSignature(new SymbolArgument(symbolType)));
    }

    @Override
    public List<Diagnostic> handleArgumentList(ParseContext context, RankNode node) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        Optional<FunctionSignature> signature = findFunctionSignature(node.getChildren());

        if (signature.isEmpty()) {
            List<String> signatureStrings = signatures.stream()
                                                      .map(func -> func.toString())
                                                      .collect(Collectors.toList());
            String availableSignatures = String.join("\n", signatureStrings);
            String message = "No function matched for that sinature. Available signatures are:\n" + availableSignatures;
            diagnostics.add(new Diagnostic(node.getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        diagnostics.addAll(signature.get().handleArgumentList(context, node.getChildren()));

        Optional<SchemaNode> property = node.getProperty();

        if (property.isEmpty() && (properties.contains("") || properties.size() == 0)) {
            // This is valid
            return diagnostics;
        }

        if (property.isPresent() && properties.size() == 0) {
            String message = "The function '" + node.getSchemaNode().getText() + "' doesn't have any properties.";
            diagnostics.add(new Diagnostic(property.get().getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }
        
        String propertyString = String.join(", ", properties);
        if (!property.isPresent()) {
            String message = "The function '" + node.getSchemaNode().getText() + "' must be used with a property. Available properties are: " + propertyString;
            diagnostics.add(new Diagnostic(node.getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        if (!properties.contains(property.get().getText())) {
            String message = "Invalid property '" + property.get().getText() + "', available properties are: " + propertyString;
            diagnostics.add(new Diagnostic(property.get().getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        SchemaNode symbolNode = property.get();
        while (!symbolNode.hasSymbol() && symbolNode.size() > 0) {
            symbolNode = symbolNode.get(0);
        }

        if (symbolNode.hasSymbol()) {
            Symbol symbol = symbolNode.getSymbol();
            symbol.setType(SymbolType.PROPERTY);
            symbol.setStatus(SymbolStatus.BUILTIN_REFERENCE);
        }

        return diagnostics;
    }

    private Optional<FunctionSignature> findFunctionSignature(List<RankNode> arguments) {
        FunctionSignature bestMatch = null;
        int maxScore = 0;

        for (FunctionSignature signature : signatures) {
            int score = signature.matchScore(arguments);
            if (score == maxScore) {
                bestMatch = null;
            } else if (score > maxScore) {
                maxScore = score;
                bestMatch = signature;
            }
        }

        if (bestMatch == null) {
            return Optional.empty();
        }
        return Optional.of(bestMatch);
    }
}
