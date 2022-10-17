// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ihopdirective.h"

namespace mbus {

/**
 * This class represents a verbatim match within a {@link Hop}'s selector. This is nothing more than a string that will
 * be used as-is when performing service name lookups.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class VerbatimDirective : public IHopDirective {
private:
    string _image;

public:
    /**
     * Constructs a new verbatim selector item for a given image.
     *
     * @param image The image to assign to this.
     */
    VerbatimDirective(vespalib::stringref image);
    ~VerbatimDirective() override;

    /**
     * Returns the image to which this is a verbatim match.
     *
     * @return The image.
     */
    const string &getImage() const { return _image; }

    Type getType() const override { return TYPE_VERBATIM; }
    bool matches(const IHopDirective &dir) const override;
    string toString() const override;
    string toDebugString() const override;
};

} // mbus

