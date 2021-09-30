// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/common.h>
#include <memory>

namespace mbus {

/**
 * This class is the base class for the primitives that make up a {@link Hop}'s selector.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class IHopDirective {
public:
    /**
     * Defines the various polymorphic variants of a hop directive.
     */
    enum Type {
        TYPE_ERROR,
        TYPE_POLICY,
        TYPE_ROUTE,
        TYPE_TCP,
        TYPE_VERBATIM
    };

    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IHopDirective> UP;
    typedef std::shared_ptr<IHopDirective> SP;

    /**
     * Implements a virtual destructor to allow virtual methods.
     */
    virtual ~IHopDirective() {
        // empty
    }

    /**
     * Returns the type of directive that this is.
     *
     * @return The type.
     */
    virtual Type getType() const = 0;

    /**
     * Returns true if this directive matches another.
     *
     * @param dir The directive to compare this to.
     * @return True if this matches the argument.
     */
    virtual bool matches(const IHopDirective &dir) const = 0;

    /**
     * Returns a string representation of this that can be parsed.
     *
     * @return The string.
     */
    virtual string toString() const = 0;

    /**
     * Returns a string representation of this that can be debugged but not parsed.
     *
     * @return The debug string.
     */
    virtual string toDebugString() const = 0;
};

} // mbus

