// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_flags.h"

namespace search::queryeval {

bool SameElementFlags::_expose_descendants = true;

SameElementFlags::ExposeDescendantsTweak::ExposeDescendantsTweak(bool expose_descendants_in)
    : _old_expose_descendants(_expose_descendants)
{
    _expose_descendants = expose_descendants_in;
}

SameElementFlags::ExposeDescendantsTweak::~ExposeDescendantsTweak()
{
    _expose_descendants = _old_expose_descendants;
}

}
