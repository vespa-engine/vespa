package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.io.PrintStream;
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
    private boolean expandable = false;

    public FunctionSignature(List<Argument> arguments, Set<String> properties, boolean expandable) {
        this.argumentList = arguments;
        this.properties = properties;
        this.expandable = expandable;

        if (expandable && arguments.size() <= 0) {
            throw new IllegalArgumentException("An expandable function takes at least one argument");
        }
    }

    public FunctionSignature(List<Argument> argument, Set<String> properties) {
        this(argument, properties, false);
    }

    public FunctionSignature(List<Argument> arguments, boolean expandable) {
        this(arguments, new HashSet<>(), expandable);
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

    public FunctionSignature(Argument argument, boolean expandable) {
        this(new ArrayList<>() {{
            add(argument);
        }}, new HashSet<>(), expandable);
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
        if (!expandable) {
            if (arguments.size() != argumentList.size()) {
                return 0;
            }
    
        }

        if (argumentList.size() == 0) return 1;

        int score = 0;
        for (int i = 0; i < arguments.size(); i++) {
            int j = Math.min(i, argumentList.size() - 1);
            boolean valid = argumentList.get(j).validateArgument(arguments.get(i));
            if (valid) {
                score += argumentList.get(j).getStrictness();
            }
        }

        return score;
    }

    List<Argument> getArgumentList() {
        return argumentList;
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
            int j = Math.min(i, argumentList.size() - 1);
            Optional<Diagnostic> diagnostic = argumentList.get(j).parseArgument(context, arguments.get(i));
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
        String arguments = String.join(",", argumentListStrings);
        if (expandable) {
            arguments += ",...";
        }
        return "(" + arguments + ")";
    }
}
