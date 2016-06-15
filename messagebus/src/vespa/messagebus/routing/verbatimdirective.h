// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ihopdirective.h"

namespace mbus {

/**
 * This class represents a verbatim match within a {@link Hop}'s selector. This is nothing more than a string that will
 * be used as-is when performing service name lookups.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
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
    VerbatimDirective(const vespalib::stringref &image);

    /**
     * Returns the image to which this is a verbatim match.
     *
     * @return The image.
     */
    const string &getImage() const { return _image; }

    virtual Type getType() const { return TYPE_VERBATIM; }
    virtual bool matches(const IHopDirective &dir) const;
    virtual string toString() const;
    virtual string toDebugString() const;
};

} // mbus

