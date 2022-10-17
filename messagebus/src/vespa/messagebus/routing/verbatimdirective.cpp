// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "verbatimdirective.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace mbus {

VerbatimDirective::VerbatimDirective(vespalib::stringref image) :
    _image(image)
{
    // empty
}

VerbatimDirective::~VerbatimDirective() = default;

bool
VerbatimDirective::matches(const IHopDirective &dir) const
{
    if (dir.getType() != TYPE_VERBATIM) {
        return false;
    }
    return _image == static_cast<const VerbatimDirective&>(dir)._image;
}

string
VerbatimDirective::toString() const
{
    return _image;
}

string
VerbatimDirective::toDebugString() const
{
    return vespalib::make_string("VerbatimDirective(image = '%s')", _image.c_str());
}

} // mbus
