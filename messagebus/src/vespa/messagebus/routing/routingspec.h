// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include "routingtablespec.h"

namespace mbus {

/**
 * Along with the {@link RoutingTableSpec}, {@link RouteSpec} and {@link HopSpec}, this holds the routing specifications
 * for all protocols. The only way a client can configure or alter the settings of a message bus instance is through
 * these classes.
 *
 * This class is the root spec class for configuring message bus routing.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class RoutingSpec {
private:
    std::vector<RoutingTableSpec> _tables;

public:
    RoutingSpec() noexcept;
    RoutingSpec(const RoutingSpec &);
    RoutingSpec & operator=(const RoutingSpec &) = delete;
    RoutingSpec(RoutingSpec &&) noexcept;
    RoutingSpec & operator=(RoutingSpec &&) noexcept;
    ~RoutingSpec();

    /**
     * Returns the number of routing table specs that are contained in this.
     *
     * @return The number of routing tables.
     */
    [[nodiscard]] uint32_t getNumTables() const { return _tables.size(); }

    /**
     * Returns the routing table spec at the given index.
     *
     * @param i The index of the routing table to return.
     * @return The routing table at the given index.
     */
    RoutingTableSpec &getTable(uint32_t i) { return _tables[i]; }

    /**
     * Returns a const reference to the routing table spec at the given index.
     *
     * @param i The index of the routing table to return.
     * @return The routing table at the given index.
     */
    [[nodiscard]] const RoutingTableSpec &getTable(uint32_t i) const { return _tables[i]; }

    /**
     * Adds a routing table spec to the list of tables.
     *
     * @param table The routing table to add.
     * @return This, to allow chaining.
     */
    RoutingSpec & addTable(RoutingTableSpec && table) &;
    RoutingSpec && addTable(RoutingTableSpec && table) &&;
    /**
     * Appends the content of this to the given config string.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    void toConfig(string &cfg, const string &prefix) const;

    /**
     * Convert a string value to a quoted value suitable for use in a config string.
     * <p/>
     * Adds double quotes before and after, and adds backslash-escapes to any double quotes that was contained in the
     * string.  A null pointer will produce the special unquoted string null that the config library will convert back
     * to a null pointer.
     *
     * @param input the String to be escaped
     * @return an escaped String
     */
    static string toConfigString(const string &input);

    /**
     * Returns a string representation of this.
     *
     * @return The string.
     */
    [[nodiscard]] string toString() const;

    /**
     * Implements the equality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this equals the other.
     */
    bool operator==(const RoutingSpec &rhs) const;

    /**
     * Implements the inequality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this does not equals the other.
     */
    bool operator!=(const RoutingSpec &rhs) const { return !(*this == rhs); }
};

} // namespace mbus

