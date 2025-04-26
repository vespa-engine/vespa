package com.yahoo.vespa.indexinglanguage.expressions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A collection of components of a single type, of which one is selected.
 *
 * @author bratseth
 */
public class SelectedComponent<TYPE> {

    private final String id;
    private final TYPE component;
    private final List<String> arguments;

    public SelectedComponent(String name, Map<String, TYPE> components, String selectedId, List<String> arguments,
                             Function<String, TYPE> failingComponentFactory) {
        this.id = selectedId;
        this.arguments = List.copyOf(arguments);

        boolean selectedIdProvided = selectedId != null && !selectedId.isEmpty();

        if (components.isEmpty()) {
            throw new IllegalStateException("No " + name + "s provided");  // should never happen
        }
        else if (components.size() == 1 && ! selectedIdProvided) {
            this.component = components.entrySet().stream().findFirst().get().getValue();
        }
        else if (components.size() > 1 && ! selectedIdProvided) {
            this.component = failingComponentFactory.apply("Multiple " + name + "s are provided but no " + name +
                                                           " id is given. " + "Valid " + name + "s are " +
                                                           validComponents(components));
        }
        else if ( ! components.containsKey(selectedId)) {
            this.component = failingComponentFactory.apply("Can't find " + name + " '" + selectedId + "'. " +
                                                           "Valid " + name + "s are " + validComponents(components));
        } else  {
            this.component = components.get(selectedId);
        }
    }

    public String id() { return id; }
    public TYPE component() { return component; }
    public List<String> arguments() { return arguments; }

    public String argumentsString() {
        var sb = new StringBuilder();
        if (id != null && !id.isEmpty())
            sb.append(" ").append(id);

        arguments.forEach(arg -> sb.append(" ").append(arg));
        return sb.toString();
    }

    @Override
    public String toString() {
        return "selected " + component;
    }

    private String validComponents(Map<String, TYPE> components) {
        List<String> componentIds = new ArrayList<>();
        components.forEach((key, value) -> componentIds.add(key));
        componentIds.sort(null);
        return String.join(", ", componentIds);
    }

}
