package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.identifierStr;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class GenericFunction {

    private String name;
    List<FunctionSignature> signatures;
    Set<String> properties;

    public GenericFunction(String name, List<FunctionSignature> signatures) {
        this.name = name;
        this.signatures = signatures;
        this.properties = new HashSet<>();

        for (FunctionSignature signature : signatures) {
            Set<String> addProps = signature.getProperties();
            if (addProps.size() == 0 && properties.size() > 0) {
                 properties.add("");
            } else {
                properties.addAll(addProps);
            }
            
        }
    }

    public GenericFunction(String name, FunctionSignature signature) {
        this(name, new ArrayList<>() {{
            add(signature);
        }});
    }

    public GenericFunction(String name, Argument argument, Set<String> properties) {
        this(name, new FunctionSignature(argument, properties));
    }

    public GenericFunction(String name, List<Argument> arguments, Set<String> proerties) {
        this(name, new FunctionSignature(arguments, proerties));
    }

    public GenericFunction(String name) {
        this(name, new FunctionSignature());
    }

    public String getName() { return name; }

    public List<Diagnostic> handleArgumentList(ParseContext context, RankNode node) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        Optional<SchemaNode> property = node.getProperty();
        Optional<String> propertyString = Optional.empty();
        if (property.isPresent()) {
            propertyString = Optional.of(property.get().getText());
        }

        Optional<FunctionSignature> signature = findFunctionSignature(node.getChildren(), propertyString, context.logger());

        if (signature.isEmpty()) {
            List<String> signatureStrings = signatures.stream()
                                                      .map(func -> func.toString())
                                                      .collect(Collectors.toList());
            String availableSignatures = String.join("\n", signatureStrings);
            String message = "No function matched for that sinature. Available signatures are:\n" + availableSignatures;
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage(message)
                .setSeverity(DiagnosticSeverity.Error)
                .build());
            return diagnostics;
        }

        diagnostics.addAll(signature.get().handleArgumentList(context, node.getChildren()));

        Set<String> signatureProps = signature.get().getProperties();

        if (property.isEmpty() && (signatureProps.contains("") || signatureProps.size() == 0)) {
            // This is valid
            node.setBuiltInFunctionSignature(name + signature.get().toString());
            return diagnostics;
        }
        
        String availableProps = (signatureProps.size() == 0) ? "No one" : String.join(", ", signatureProps);
        if (!property.isPresent()) {
            String message = "The function '" + node.getSchemaNode().getText() + "' must be used with a property. Available properties are: " + availableProps;
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage(message)
                .setSeverity(DiagnosticSeverity.Error)
                .build());
            return diagnostics;
        }

        if (!properties.contains(property.get().getText())) {
            String message = "Invalid property '" + property.get().getText() + "'. Available properties are: " + availableProps;
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(property.get().getRange())
                .setMessage(message)
                .setSeverity(DiagnosticSeverity.Error)
                .build());
            return diagnostics;
        }

        if (!signature.get().getProperties().contains(property.get().getText())) {
            String message = "This property is not available with with this signature. Available properties are: " + availableProps;
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(property.get().getRange())
                .setMessage(message)
                .setSeverity(DiagnosticSeverity.Warning)
                .build());
        }

        SchemaNode symbolNode = property.get();
        while (!symbolNode.isASTInstance(identifierStr.class) && symbolNode.size() > 0) {
            symbolNode = symbolNode.get(0);
        }

        if (symbolNode.isASTInstance(identifierStr.class)) {
            symbolNode.setSymbol(SymbolType.PROPERTY, context.fileURI());
            symbolNode.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
        }

        node.setBuiltInFunctionSignature(name + signature.get().toString() + (property.isPresent() ? "." + propertyString.get() : ""));

        return diagnostics;
    }

    private static boolean propertyInSet(Optional<String> string, Set<String> propertiySet) {
        if (string.isEmpty() && (
            propertiySet.size() == 0 ||
            propertiySet.contains("")
        )) {
            return true;
        }

        if (string.isEmpty()) return false;

        return propertiySet.contains(string.get());
    }

    private Optional<FunctionSignature> findFunctionSignature(List<RankNode> arguments, Optional<String> property, PrintStream logger) {

        List<FunctionSignature> bestMatches = new ArrayList<>();
        int maxScore = 0;

        for (FunctionSignature signature : signatures) {
            int score = signature.matchScore(arguments);
            if (score == maxScore) {
                bestMatches.add(signature);
            } else if (score > maxScore) {
                maxScore = score;
                bestMatches = new ArrayList<>() {{
                    add(signature);
                }};
            }
        }

        if (bestMatches.size() == 1) {
            return Optional.of(bestMatches.get(0));
        }

        // Filter by the property first
        List<FunctionSignature> possibleSignatures = new ArrayList<>(bestMatches);

        for (int i = possibleSignatures.size() - 1; i >= 0; i--) {
            if (!propertyInSet(property, possibleSignatures.get(i).getProperties())) {
                possibleSignatures.remove(i);
            }
        }

        if (possibleSignatures.size() == 1) return Optional.of(possibleSignatures.get(0));

        return Optional.empty();
    }
}
