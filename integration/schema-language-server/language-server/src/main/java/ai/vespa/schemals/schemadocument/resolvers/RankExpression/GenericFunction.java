package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bouncycastle.pqc.jcajce.provider.lms.LMSSignatureSpi.generic;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.referenceType;
import ai.vespa.schemals.parser.rankingexpression.ast.identifierStr;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.tree.SchemaNode;

public class GenericFunction implements FunctionHandler {
    
    List<Argument> argumentList;
    Set<String> properties;

    public GenericFunction(List<Argument> arguments, Set<String> properties) {
        this.argumentList = arguments;
        this.properties = properties;
    }

    public GenericFunction(List<Argument> arguments) {
        this(arguments, new HashSet<>());
    }

    public GenericFunction(Argument argument) {
        this(new ArrayList<>() {{
            add(argument);
        }});
    }

    public GenericFunction(Argument argument, Set<String> properties) {
        this(new ArrayList<>() {{
            add(argument);
        }}, properties);
    }

    public List<Diagnostic> handleArgumentList(ParseContext context, SchemaNode node, List<SchemaNode> arguments, Optional<SchemaNode> property) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (arguments.size() != argumentList.size()) {
            String message = "The function '" + node.getText() + "' takes " + argumentList.size() + " arguments, but " + arguments.size() + " were given.";
            diagnostics.add(new Diagnostic(node.getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        for (int i = 0; i < arguments.size(); i++) {
            diagnostics.addAll(argumentList.get(i).verifyArgument(context, arguments.get(i)));
        }

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

    public static GenericFunction singleSymbolArugmnet(SymbolType symbolType) {
        return new GenericFunction(new SymbolArgument(symbolType));
    }
}
