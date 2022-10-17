// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ihopdirective.h"

namespace mbus {

/**
 * This class represents an error directive within a {@link Hop}'s selector. This means to stop whatever is being
 * resolved, and instead return a reply containing a specified error.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class ErrorDirective : public IHopDirective {
private:
    string _msg;

public:
    /**
     * Constructs a new error directive.
     *
     * @param msg The error message.
     */
    ErrorDirective(vespalib::stringref msg);
    ~ErrorDirective() override;

    /**
     * Returns the error string that is to be assigned to the reply.
     *
     * @return The error string.
     */
    const string &getMessage() const { return _msg; }

    Type getType() const override { return TYPE_ERROR; }
    bool matches(const IHopDirective &) const override { return false; }
    string toString() const override;
    string toDebugString() const override;
};

} // mbus

