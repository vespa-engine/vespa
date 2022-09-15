// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_dfw.h"

namespace search::docsummary {

EmptyDFW::EmptyDFW() = default;

EmptyDFW::~EmptyDFW() = default;

void
EmptyDFW::insertField(uint32_t, GetDocsumsState&, vespalib::slime::Inserter &target) const
{
    // insert explicitly-empty field?
    // target.insertNix();
    (void)target;
}

}
