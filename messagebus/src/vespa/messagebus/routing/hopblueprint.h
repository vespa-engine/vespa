// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hop.h"

namespace mbus {

class HopSpec;

/**
 * A hop blueprint is a stored prototype of a hop that has been created from a {@link HopSpec} object. A map of these
 * are stored in a {@link RoutingTable}.
 */
class HopBlueprint {
private:
    std::vector<IHopDirective::SP> _selector;
    std::vector<Hop>               _recipients;
    bool                           _ignoreResult;

public:
    /**
     * Create a new blueprint from a specification object.
     *
     * @param spec The specification to base instantiation on.
     */
    HopBlueprint(const HopSpec &spec);

    /**
     * Creates a hop instance from thie blueprint.
     *
     * @return The created hop.
     */
    Hop::UP create() const { return Hop::UP(new Hop(_selector, _ignoreResult)); }

    /**
     * Returns whether or not there are any directives contained in this hop.
     *
     * @return True if there is at least one directive.
     */
    bool hasDirectives() const { return !_selector.empty(); }

    /**
     * Returns the number of directives contained in this hop.
     *
     * @return The number of directives.
     */
    uint32_t getNumDirectives() const { return _selector.size(); }

    /**
     * Returns the directive at the given index.
     *
     * @param i The index of the directive to return.
     * @return The item.
     */
    IHopDirective::SP getDirective(uint32_t i) const { return _selector[i]; }

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
     * Returns the recipient at the given index.
     *
     * @param i The index of the recipient to return.
     * @return The recipient at the given index.
     */
    const Hop &getRecipient(uint32_t i) const { return _recipients[i]; }

    /**
     * Returns whether or not to ignore the result when routing through this hop.
     *
     * @return True to ignore the result.
     */
    bool getIgnoreResult() const { return _ignoreResult; }

    /**
     * Returns a string representation of this.
     *
     * @return The string.
     */
    string toString() const;
};

} // namespace mbus

