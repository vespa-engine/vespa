package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.tree.SchemaNode;

public class GenericFunction implements FunctionHandler {
    
    List<Argument> argumentList;

    public GenericFunction(List<Argument> arguments) {
        this.argumentList = arguments;
    }

    public GenericFunction(Argument argument) {
        this(new ArrayList<>() {{
            add(argument);
        }});
    }

    public List<Diagnostic> handleArgumentList(ParseContext context, SchemaNode node, List<SchemaNode> arguments) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (arguments.size() != argumentList.size()) {
            String message = "The function '" + node.getText() + "' takes " + argumentList.size() + " arguments, but " + arguments.size() + " were given.";
            diagnostics.add(new Diagnostic(node.getRange(), message, DiagnosticSeverity.Error, ""));
            return diagnostics;
        }

        for (int i = 0; i < arguments.size(); i++) {
            diagnostics.addAll(argumentList.get(i).verifyArgument(context, arguments.get(i)));
        }

        return diagnostics;
    }

    public static GenericFunction singleSymbolArugmnet(SymbolType symbolType) {
        return new GenericFunction(new SymbolArgument(symbolType));
    }
}
