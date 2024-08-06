package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class FunctionSignature {

    private List<Argument> argumentList;
    private Set<String> properties;

    public FunctionSignature(List<Argument> arguments, Set<String> properties) {
        this.argumentList = arguments;
        this.properties = properties;
    }

    public FunctionSignature(List<Argument> arguments) {
        this(arguments, new HashSet<>());
    }

    public FunctionSignature(Argument argument, Set<String> properties) {
        this(new ArrayList<>() {{
            add(argument);
        }}, properties);
    }

    public FunctionSignature(Argument argument, String property) {
        this(argument, new HashSet<>() {{
            add(property);
        }});
    }

    public FunctionSignature(Argument argument) {
        this(new ArrayList<>() {{
            add(argument);
        }});
    }

    public FunctionSignature() {
        this(new ArrayList<>());
    }

    int matchScore(List<RankNode> arguments) {
        if (arguments.size() != argumentList.size()) {
            return 0;
        }

        if (argumentList.size() == 0) return 1;

        int score = 0;
        for (int i = 0; i < arguments.size(); i++) {
            boolean valid = argumentList.get(i).validateArgument(arguments.get(i));
            if (valid) {
                score += argumentList.get(i).getStrictness();
            }
        }

        return score;
    }

    public List<Argument> getArgumentList() {
        return List.copyOf(argumentList);
    }

    Set<String> getProperties() {
        return properties;
    }

    List<Diagnostic> handleArgumentList(ParseContext context, List<RankNode> arguments) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        // if (arguments.size() != argumentList.size()) {
        //     String message = "The function '" + node.getText() + "' takes " + argumentList.size() + " arguments, but " + arguments.size() + " were given.";
        //     return diagnostics;
        // }

        for (int i = 0; i < arguments.size(); i++) {
            Optional<Diagnostic> diagnostic = argumentList.get(i).parseArgument(context, arguments.get(i));
            if (diagnostic.isPresent()) {
                diagnostics.add(diagnostic.get());
            }
        }

        return diagnostics;
    }

    public String toString() {
        List<String> argumentListStrings = argumentList.stream()
                                                       .map(arg -> arg.displayString())
                                                       .collect(Collectors.toList());
        String arguments = String.join(", ", argumentListStrings);
        return "(" + arguments + ")";
    }
}
