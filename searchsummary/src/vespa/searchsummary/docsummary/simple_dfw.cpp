// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_dfw.h"

namespace search::docsummary {

void
SimpleDFW::insertField(uint32_t docid, GeneralResult *, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target)
{
    insertField(docid, state, type, target);
}

}
