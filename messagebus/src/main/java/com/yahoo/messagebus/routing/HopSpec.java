// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Along with the {@link RoutingSpec}, {@link RoutingTableSpec} and {@link RouteSpec}, this holds the routing
 * specifications for all protocols. The only way a client can configure or alter the settings of a message bus instance
 * is through these classes.
 * <p>
 * This class contains the spec for a single hop.
 *
 * @author Simon Thoresen Hult
 */
public class HopSpec {

    private final String name;
    private final String selector;
    private final List<String> recipients = new ArrayList<>();
    private final boolean verify;
    private boolean ignoreResult = false;

    /**
     * Creates a new named hop specification.
     *
     * @param name     A protocol-unique name for this hop.
     * @param selector A string that represents the selector for this hop.
     */
    public HopSpec(String name, String selector) {
        this(name, selector, true);
    }

    /**
     * Creates a new named hop specification.
     *
     * @param name     A protocol-unique name for this hop.
     * @param selector A string that represents the selector for this hop.
     * @param verify   Whether or not this should be verified.
     */
    public HopSpec(String name, String selector, boolean verify) {
        this.name = name;
        this.selector = selector;
        this.verify = verify;
    }

    /**
     * Implements the copy constructor.
     *
     * @param obj The object to copy.
     */
    public HopSpec(HopSpec obj) {
        this.name = obj.name;
        this.selector = obj.selector;
        this.verify = obj.verify;
        for (String recipient : obj.recipients) {
            recipients.add(recipient);
        }
        this.ignoreResult = obj.ignoreResult;
    }

    /**
     * Returns the protocol-unique name of this hop.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the string selector that resolves the recipients of this hop.
     *
     * @return The selector.
     */
    public String getSelector() {
        return selector;
    }

    /**
     * Returns whether there are any recipients that the selector can choose from.
     *
     * @return True if there is at least one recipient.
     */
    public boolean hasRecipients() {
        return !recipients.isEmpty();
    }

    /**
     * Returns the number of recipients that the selector can choose from.
     *
     * @return The number of recipients.
     */
    public int getNumRecipients() {
        return recipients.size();
    }

    /**
     * Returns the recipients at the given index.
     *
     * @param i The index of the recipient to return.
     * @return The recipient at the given index.
     */
    public String getRecipient(int i) {
        return recipients.get(i);
    }

    /**
     * Adds the given recipient to this.
     *
     * @param recipient The recipient to add.
     * @return This, to allow chaining.
     */
    public HopSpec addRecipient(String recipient) {
        recipients.add(recipient);
        return this;
    }

    /**
     * Adds the given recipients to this.
     *
     * @param recipients The recipients to add.
     * @return This, to allow chaining.
     */
    public HopSpec addRecipients(List<String> recipients) {
        this.recipients.addAll(recipients);
        return this;
    }

    /**
     * Sets the recipient at the given index.
     *
     * @param i         The index at which to set the recipient.
     * @param recipient The recipient to set.
     * @return This, to allow chaining.
     */
    public HopSpec setRecipient(int i, String recipient) {
        recipients.set(i, recipient);
        return this;
    }

    /**
     * Removes the recipient at the given index.
     *
     * @param i The index of the recipient to remove.
     * @return The removed recipient.
     */
    public String removeRecipient(int i) {
        return recipients.remove(i);
    }

    /**
     * Clears the list of recipients that the selector may choose from.
     *
     * @return This, to allow chaining.
     */
    public HopSpec clearRecipients() {
        recipients.clear();
        return this;
    }

    /**
     * Returns whether to ignore the result when routing through this hop.
     *
     * @return True to ignore the result.
     */
    public boolean getIgnoreResult() {
        return ignoreResult;
    }

    /**
     * Sets whether to ignore the result when routing through this hop.
     *
     * @param ignoreResult Whether to ignore the result.
     * @return This, to allow chaining.
     */
    public HopSpec setIgnoreResult(boolean ignoreResult) {
        this.ignoreResult = ignoreResult;
        return this;
    }

    /**
     * Verifies the content of this against the given application.
     *
     * @param app    The application to verify against.
     * @param table  The routing table to verify against.
     * @param errors The list of errors found.
     * @return True if no errors where found.
     */
    public boolean verify(ApplicationSpec app, RoutingTableSpec table, List<String> errors) {
        if (verify) {
            verify(app, table, null, recipients, selector, errors,
                   "hop '" + name + "' in routing table '" + table.getProtocol() + "'");
        }
        return errors.isEmpty();
    }

    /**
     * Verifies that the hop given by the given selector and children is valid.
     *
     * @param app      The application to verify against.
     * @param table    The routing table to verify against.
     * @param parent   The parent hop that the selector must match.
     * @param selector The selector to verify.
     * @param children The children to verify, may be null or empty.
     * @param errors   The list of errors found.
     * @param context  The context to use if adding an error.
     * @return True if no errors where found.
     */
    static boolean verify(ApplicationSpec app, RoutingTableSpec table, Hop parent, List<String> children,
                          String selector, List<String> errors, String context) {
        // Verify that the selector can be parsed.
        Hop hop = Hop.parse(selector);
        for (int i = 0, len = hop.getNumDirectives(); i < len; ++i) {
            HopDirective dir = hop.getDirective(i);
            if (dir instanceof ErrorDirective) {
                errors.add("For " + context + "; " + ((ErrorDirective)dir).getMessage());
                return false;
            }
        }

        // Verify that the parent matches this, if any.
        if (parent != null) {
            if (parent.getNumDirectives() == 1) {
                // hops that contain a single policy directive will typically be magic
            } else if (!parent.matches(hop)) {
                errors.add("Selector '" + parent.getServiceName() + "' does not match " + context + ".");
                return false;
            }
        }

        // Traverse and verify the directives of the hop.
        boolean verifyServiceName = true;
        boolean allowRecipients = false;
        for (int i = 0, len = hop.getNumDirectives(); i < len; ++i) {
            HopDirective dir = hop.getDirective(i);
            if (dir instanceof ErrorDirective) {
                // caught above
            } else if (dir instanceof PolicyDirective) {
                allowRecipients = true;
                verifyServiceName = false;
            } else if (dir instanceof RouteDirective) {
                String routeName = ((RouteDirective)dir).getName();
                if (!table.hasRoute(routeName)) {
                    errors.add(capitalize(context) + " references route '" + routeName + "' which does not exist.");
                    return false;
                }
                verifyServiceName = false;
            } else if (dir instanceof TcpDirective) {
                verifyServiceName = false;
            } else if (dir instanceof VerbatimDirective) {
                // will be verified below
            }
        }

        // Verify that the service referenced by the hop exists, if required.
        if (verifyServiceName) {
            String serviceName = hop.getServiceName();
            if (table.hasRoute(serviceName)) {
                // all good
            } else if (table.hasHop(serviceName)) {
                // also good
            } else if (!app.isService(table.getProtocol(), serviceName)) {
                errors.add(capitalize(context) + " references '" + serviceName + "' which is neither a service," +
                           " a route nor another hop.");
                return false;
            }
        }

        // Verify that recipients are allowed and that they are valid themselves.
        if (children != null && children.size() > 0) {
            if (!allowRecipients) {
                errors.add(capitalize(context) + " has recipients but no policy directive.");
                return false;
            }
            for (String child : children) {
                verify(app, table, hop, null, child, errors, "recipient '" + child + "' in " + context);
            }
        }

        // All ok.
        return true;
    }

    /**
     * Appends the content of this to the given config string builder.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    public void toConfig(StringBuilder cfg, String prefix) {
        cfg.append(prefix).append("name ").append(RoutingSpec.toConfigString(name)).append("\n");
        cfg.append(prefix).append("selector ").append(RoutingSpec.toConfigString(selector)).append("\n");
        if (ignoreResult) {
            cfg.append(prefix).append("ignoreresult true\n");
        }
        int numRecipients = recipients.size();
        if (numRecipients > 0) {
            cfg.append(prefix).append("recipient[").append(numRecipients).append("]\n");
            for (int i = 0; i < numRecipients; ++i) {
                cfg.append(prefix).append("recipient[").append(i).append("] ");
                cfg.append(RoutingSpec.toConfigString(recipients.get(i))).append("\n");
            }
        }
    }

    /**
     * Capitalizes the given string by upper-casing its first letter.
     *
     * @param str The string to capitalize.
     * @return The capitalized string.
     */
    private static String capitalize(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    // Overrides Object.
    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        toConfig(ret, "");
        return ret.toString();
    }

    // Overrides Object.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HopSpec)) {
            return false;
        }
        HopSpec rhs = (HopSpec)obj;
        if (!name.equals(rhs.name)) {
            return false;
        }
        if (!selector.equals(rhs.selector)) {
            return false;
        }
        if (!recipients.equals(rhs.recipients)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (selector != null ? selector.hashCode() : 0);
        result = 31 * result + (recipients != null ? recipients.hashCode() : 0);
        result = 31 * result + (verify ? 1 : 0);
        result = 31 * result + (ignoreResult ? 1 : 0);
        return result;
    }
}
