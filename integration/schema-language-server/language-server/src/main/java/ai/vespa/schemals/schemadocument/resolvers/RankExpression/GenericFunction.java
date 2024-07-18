package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.identifierStr;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.tree.SchemaNode;

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
    public List<Diagnostic> handleArgumentList(ParseContext context, SchemaNode node, List<SchemaNode> arguments, Optional<SchemaNode> property) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        Optional<FunctionSignature> signature = findFunctionSignature(arguments);

        if (signature.isEmpty()) {
            List<String> signatureStrings = signatures.stream()
                                                      .map(func -> func.toString())
                                                      .collect(Collectors.toList());
            String availableSignatures = String.join("\n", signatureStrings);
            String message = "No function matched for that sinature. Available signatures are:\n" + availableSignatures;
            diagnostics.add(new Diagnostic(node.getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        diagnostics.addAll(signature.get().handleArgumentList(context, arguments));

        if (property.isEmpty() && (properties.contains("") || properties.size() == 0)) {
            // This is valid
            return diagnostics;
        }

        if (property.isPresent() && properties.size() == 0) {
            String message = "The function '" + node.getText() + "' doesn't have any properties.";
            diagnostics.add(new Diagnostic(property.get().getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }
        
        String propertyString = String.join(", ", properties);
        if (!property.isPresent()) {
            String message = "The function '" + node.getText() + "' must be used with a property. Available properties are: " + propertyString;
            diagnostics.add(new Diagnostic(node.getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        if (!properties.contains(property.get().getText())) {
            String message = "Invalid property '" + property.get().getText() + "', available properties are: " + propertyString;
            diagnostics.add(new Diagnostic(property.get().getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        SchemaNode identifierStrNode = property.get();
        while (!identifierStrNode.isASTInstance(identifierStr.class) && identifierStrNode.size() > 0) {
            identifierStrNode = identifierStrNode.get(0);
        }

        if (identifierStrNode.isASTInstance(identifierStr.class)) {
            identifierStrNode.setSymbol(SymbolType.PROPERTY, context.fileURI());
            identifierStrNode.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
        }

        return diagnostics;
    }

    private Optional<FunctionSignature> findFunctionSignature(List<SchemaNode> arguments) {
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
