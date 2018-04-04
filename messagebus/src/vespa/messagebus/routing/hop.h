// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ihopdirective.h"
#include <vector>

namespace mbus {

/**
 * A hop is basically an instantiated {@link HopBlueprint}, but it can also be contructed using the factory method
 * {@link this#parse(String)}. It is a set of primitives, either a string primitive that is to be matched verbatim to a
 * service address, or a {@link RoutingPolicy} directive.
 */
class Hop {
private:
    std::vector<IHopDirective::SP> _selector;
    bool                           _ignoreResult;

public:
    /**
     * Convenience typedef for an auto-pointer to a hop.
     */
    typedef std::unique_ptr<Hop> UP;

    /**
     * Constructs an empty hop.
     */
    Hop();

    /**
     * Constructs a new hop based on a selector string.
     *
     * @param selector The selector string for this hop.
     */
    Hop(const string &selector);

    /**
     * Constructs a fully populated hop.
     *
     * @param selector     The selector to copy.
     * @param ignoreResult Whether or not to ignore the result of this hop.
     */
    Hop(std::vector<IHopDirective::SP> selector, bool ignoreResult);

    Hop(const Hop &);
    Hop & operator = (const Hop &);
    Hop(Hop &&) noexcept = default;
    Hop & operator = (Hop &&) noexcept = default;

    ~Hop();

    /**
     * Adds a new directive to this hop.
     *
     * @param directive The directive to add.
     * @return This, to allow chaining.
     */
    Hop &addDirective(IHopDirective::SP dir);

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
     * Sets the directive at a given index.
     *
     * @param i   The index at which to set the directive.
     * @param dir The directive to set.
     * @return This, to allow chaining.
     */
    Hop &setDirective(uint32_t i, IHopDirective::SP dir);

    /**
     * Removes the directive at the given index.
     *
     * @param i The index of the directive to remove.
     * @return The removed directive.
     */
    IHopDirective::SP removeDirective(uint32_t i);

    /**
     * Clears all directives from this hop.
     *
     * @return This, to allow chaining.
     */
    Hop &clearDirectives();

    /**
     * Returns the service name referenced by this hop. This is the concatenation of all selector primitives,
     * but with no ignore-result prefix.
     *
     * @return The service name.
     */
    string getServiceName() const { return toString(0, _selector.size()); }

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
    Hop &setIgnoreResult(bool ignoreResult);

    /**
     * Parses the given string as a single hop. The {@link this#toString()} method is compatible with this parser.
     *
     * @param hop The string to parse.
     * @return A hop that corresponds to the string.
     */
    static Hop parse(const string &hop);

    /**
     * Returns true whether this hop matches another. This respects policy directives matching any other.
     *
     * @param hop The hop to compare to.
     * @return True if this matches the argument, false otherwise.
     */
    bool matches(const Hop &hop) const;

    /**
     * Returns a string representation of this that can be debugged but not parsed.
     *
     * @return The debug string.
     */
    string toDebugString() const;

    /**
     * Returns a string representation of this that can be parsed.
     *
     * @return The parseable string.
     */
    string toString() const;

    /**
     * Returns a string concatenation of a subset of the selector primitives contained in this.
     *
     * @param fromIncluding  The index of the first primitive to include.
     * @param toNotIncluding The index after the last primitive to include.
     * @return The string concatenation.
     */
    string toString(uint32_t fromIncluding, uint32_t toNotIncluding) const;

    /**
     * Returns the prefix of this hop's selector to, but not including, the given index.
     *
     * @param toNotIncluding The index to which to generate prefix.
     * @return The prefix before the index.
     */
    string getPrefix(uint32_t toNotIncluding) const;

    /**
     * Returns the suffix of this hop's selector from, but not including, the given index.
     *
     * @param fromNotIncluding The index from which to generate suffix.
     * @return The suffix after the index.
     */
    string getSuffix(uint32_t fromNotIncluding) const;
};

} // namespace mbus

