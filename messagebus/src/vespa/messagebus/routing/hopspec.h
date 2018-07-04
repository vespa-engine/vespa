// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/common.h>
#include <vector>

namespace mbus {

/**
 * Along with the {@link RoutingSpec}, {@link RoutingTableSpec} and {@link RouteSpec}, this holds the routing
 * specifications for all protocols. The only way a client can configure or alter the settings of a message bus instance
 * is through these classes.
 *
 * This class contains the spec for a single hop.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class HopSpec {
private:
    string              _name;
    string              _selector;
    std::vector<string> _recipients;
    bool                _ignoreResult;

public:
    /**
     * The default constructor requires both the name and the selector.
     *
     * @param name     A protocol unique name for this hop.
     * @param selector A string that represents the selector for this hop.
     */
    HopSpec(const string &name, const string &selector);
    ~HopSpec();

    /**
     * Returns the protocol-unique name of this hop.
     *
     * @return The name.
     */
    const string &getName() const { return _name; }

    /**
     * Returns the string selector that resolves the recipients of this hop.
     *
     * @return The selector.
     */
    const string &getSelector() const { return _selector; }

    /**
     * Returns whether or not there are any recipients that the selector can choose from.
     *
     * @return True if there is at least one recipient.
     */
    bool hasRecipients() const { return !_recipients.empty(); }

    /**
     * Returns the number of recipients that the selector can choose from.
     *
     * @return The number of recipients.
     */
    uint32_t getNumRecipients() const { return _recipients.size(); }

    /**
     * Returns the recipients at the given index.
     *
     * @param i The index of the recipient to return.
     * @return The recipient at the given index.
     */
    const string &getRecipient(uint32_t i) const { return _recipients[i]; }

    /**
     * Adds the given recipient to this.
     *
     * @param recipient The recipient to add.
     * @return This, to allow chaining.
     */
    HopSpec &addRecipient(const string &recipient) { _recipients.push_back(recipient); return *this; }

    /**
     * Adds the given recipients to this.
     *
     * @param recipients The recipients to add.
     * @return This, to allow chaining.
     */
    HopSpec &addRecipients(const std::vector<string> &recipients);

    /**
     * Sets the recipient at the given index.
     *
     * @param i The index at which to set the recipient.
     * @param recipient The recipient to set.
     * @return This, to allow chaining.
     */
    HopSpec &setRecipient(uint32_t i, const string &recipient) { _recipients[i] = recipient; return *this; }

    /**
     * Removes the recipient at the given index.
     *
     * @param i The index of the recipient to remove.
     * @return The removed recipient.
     */
    string removeRecipient(uint32_t i);

    /**
     * Clears the list of recipients that the selector may choose from.
     *
     * @return This, to allow chaining.
     */
    HopSpec &clearRecipients() { _recipients.clear(); return *this; }

    /**
     * Returns whether or not to ignore the result when routing through this hop.
     *
     * @return True to ignore the result.
     */
    bool getIgnoreResult() const { return _ignoreResult; }

    /**
     * Sets whether or not to ignore the result when routing through this hop.
     *
     * @param ignoreResult Whether or not to ignore the result.
     * @return This, to allow chaining.
     */
    HopSpec &setIgnoreResult(bool ignoreResult);

    /**
     * Appends the content of this to the given config string.
     *
     * @param cfg    The config to add to.
     * @param prefix The prefix to use for each add.
     */
    void toConfig(string &cfg, const string &prefix) const;

    /**
     * Returns a string representation of this.
     *
     * @return The string.
     */
    string toString() const;

    /**
     * Implements the equality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this equals the other.
     */
    bool operator==(const HopSpec &rhs) const;

    /**
     * Implements the inequality operator.
     *
     * @param rhs The object to compare to.
     * @return True if this does not equals the other.
     */
    bool operator!=(const HopSpec &rhs) const { return !(*this == rhs); }
};

} // namespace mbus

