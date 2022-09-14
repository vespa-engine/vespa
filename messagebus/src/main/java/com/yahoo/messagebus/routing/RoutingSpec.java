// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Along with the {@link RoutingTableSpec}, {@link RouteSpec} and {@link HopSpec}, this holds the routing specifications
 * for all protocols. The only way a client can configure or alter the settings of a message bus instance is through
 * these classes.
 * <p>
 * This class is the root spec class for configuring message bus routing.
 *
 * @author Simon Thoresen Hult
 */
public class RoutingSpec {

    private final List<RoutingTableSpec> tables = new ArrayList<>();
    private final boolean verify;

    /**
     * Creates an empty specification.
     */
    public RoutingSpec() {
        this(true);
    }

    /**
     * Creates an empty specification.
     *
     * @param verify Whether this should be verified.
     */
    public RoutingSpec(boolean verify) {
        this.verify = verify;
    }

    /**
     * Implements the copy constructor.
     *
     * @param spec the spec to copy.
     */
    public RoutingSpec(RoutingSpec spec) {
        verify = spec.verify;
        for (RoutingTableSpec table : spec.tables) {
            tables.add(new RoutingTableSpec(table));
        }
    }

    /**
     * Returns whether or not there are routing table specs contained in this.
     *
     * @return True if there is at least one table.
     */
    public boolean hasTables() {
        return !tables.isEmpty();
    }

    /**
     * Returns the number of routing table specs that are contained in this.
     *
     * @return The number of routing tables.
     */
    public int getNumTables() {
        return tables.size();
    }

    /**
     * Returns the routing table spec at the given index.
     *
     * @param i The index of the routing table to return.
     * @return The routing table at the given index.
     */
    public RoutingTableSpec getTable(int i) {
        return tables.get(i);
    }

    /**
     * Sets the routing table spec at the given index.
     *
     * @param i     The index at which to set the routing table.
     * @param table The routing table to set.
     * @return This, to allow chaining.
     */
    public RoutingSpec setTable(int i, RoutingTableSpec table) {
        tables.set(i, table);
        return this;
    }

    /**
     * Adds a routing table spec to the list of tables.
     *
     * @param table The routing table to add.
     * @return This, to allow chaining.
     */
    public RoutingSpec addTable(RoutingTableSpec table) {
        tables.add(table);
        return this;
    }

    /**
     * Returns the routing table spec at the given index.
     *
     * @param i The index of the routing table to remove.
     * @return The removed routing table.
     */
    public RoutingTableSpec removeTable(int i) {
        return tables.remove(i);
    }

    /**
     * Clears the list of routing table specs contained in this.
     *
     * @return This, to allow chaining.
     */
    public RoutingSpec clearTables() {
        tables.clear();
        return this;
    }

    /**
     * Verifies the content of this against the given application.
     *
     * @param app    The application to verify against.
     * @param errors The list of errors found.
     * @return True if no errors where found.
     */
    public boolean verify(ApplicationSpec app, List<String> errors) {
        if (verify) {
            Map<String, Integer> tableNames = new HashMap<>();
            for (RoutingTableSpec table : tables) {
                String name = table.getProtocol();

                int count = tableNames.containsKey(name) ? tableNames.get(name) : 0;
                tableNames.put(name, count + 1);
                table.verify(app, errors);
            }
            for (Map.Entry<String, Integer> entry : tableNames.entrySet()) {
                int count = entry.getValue();
                if (count > 1) {
                    errors.add("Routing table '" + entry.getKey() + "' is defined " + count + " times.");
                }
            }
        }
        return errors.isEmpty();
    }

    /**
     * Appends the content of this to the given config string builder.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    public void toConfig(StringBuilder cfg, String prefix) {
        int numTables = tables.size();
        if (numTables > 0) {
            cfg.append(prefix).append("routingtable[").append(numTables).append("]\n");
            for (int i = 0; i < numTables; ++i) {
                tables.get(i).toConfig(cfg, prefix + "routingtable[" + i + "].");
            }
        }
    }

    /**
     * Convert a string value to a quoted value suitable for use in a config string.
     * <p>
     * Adds double quotes before and after, and adds backslash-escapes to any double quotes that was contained in the
     * string.  A null pointer will produce the special unquoted string null that the config library will convert back
     * to a null pointer.
     *
     * @param input the String to be escaped
     * @return an escaped String
     */
    static String toConfigString(String input) {
        if (input == null) {
            return "null";
        }
        StringBuilder ret = new StringBuilder(2 + input.length());
        ret.append("\"");
        for (int i = 0, len = input.length(); i < len; ++i) {
            if (input.charAt(i) == '\\') {
                ret.append("\\\\");
            } else if (input.charAt(i) == '"') {
                ret.append("\\\"");
            } else if (input.charAt(i) == '\n') {
                ret.append("\\n");
            } else if (input.charAt(i) == 0) {
                ret.append("\\x00");
            } else {
                ret.append(input.charAt(i));
            }
        }
        ret.append("\"");
        return ret.toString();
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        toConfig(ret, "");
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RoutingSpec)) {
            return false;
        }
        RoutingSpec rhs = (RoutingSpec)obj;
        if (!tables.equals(rhs.tables)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = tables.hashCode();
        result = 31 * result + (verify ? 1 : 0);
        return result;
    }
}
