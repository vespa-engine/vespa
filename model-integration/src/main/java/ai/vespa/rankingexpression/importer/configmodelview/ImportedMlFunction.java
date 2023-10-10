// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An imported function of an imported machine-learned model
 *
 * @author bratseth
 */
public class ImportedMlFunction {

    private final String name;
    private final List<String> arguments;
    private final Map<String, String> argumentTypes;
    private final String  expression;
    private final Optional<String> returnType;

    public ImportedMlFunction(String name, List<String> arguments, String expression,
                              Map<String, String> argumentTypes, Optional<String> returnType) {
        this.name = name;
        this.arguments = Collections.unmodifiableList(arguments);
        this.expression = expression;
        this.argumentTypes = Collections.unmodifiableMap(argumentTypes);
        this.returnType = returnType;
    }

    public String name() { return name; }
    public List<String> arguments() { return arguments; }
    public Map<String, String> argumentTypes() { return argumentTypes; }
    public String expression() { return expression; }
    public Optional<String> returnType() { return returnType; }

}
