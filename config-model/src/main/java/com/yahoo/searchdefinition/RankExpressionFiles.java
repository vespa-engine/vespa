package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.AbstractService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class RankExpressionFiles {
    private final Map<String, RankExpressionFile> expressions = new HashMap<>();

    //TODO Deploy logger should not be necessary, as redefinition is illegal, but legacy prevents enforcement starting now.
    public void add(RankExpressionFile expression, DeployLogger deployLogger) {
        expression.validate();
        String name = expression.getName();
        if (expressions.containsKey(name)) {
            if ( expressions.get(name).getFileName().equals(expression.getFileName()) ) {
                //TODO Throw instead, No later than Vespa 8
                deployLogger.logApplicationPackage(Level.WARNING, "Rank expression file '" + name +
                        "' defined twice with identical expression (illegal and will be enforced soon) '" + expression.getFileName() + "'.");
            } else {
                throw new IllegalArgumentException("Rank expression file '" + name +
                        "' defined twice (illegal but not enforced), but redefinition is not matching (illegal and enforced), " +
                        "previous = '" + expressions.get(name).getFileName() + "', new = '" + expression.getFileName() + "'.");
            }
        }
        expressions.put(name, expression);
    }

    /** Returns the ranking constant with the given name, or null if not present */
    public RankExpressionFile get(String name) {
        return expressions.get(name);
    }

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankExpressionFile> asMap() {
        return Collections.unmodifiableMap(expressions);
    }

    /** Initiate sending of these constants to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        expressions.values().forEach(constant -> constant.sendTo(services));
    }
}
