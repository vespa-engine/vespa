// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "inserter.h"
#include "inspector.h"

namespace vespalib::slime {

/**
 * Inject a slime sub-structure described by an Inspector into a slime
 * structure where the insertion point is described by an
 * Inserter. This will copy all the values represented by the
 * Inspector into the position described by the Inserter. Note that
 * this can be used to either copy data from one Slime structure to
 * another, or to copy data internally within a single slime
 * structure. If the Inspector contains the insertion point it will
 * only be expanded once to avoid infinite recursion.
 *
 * @param inspector what to inject
 * @param inserter where to inject
 **/
void inject(const Inspector &inspector, const Inserter &inserter);

} // namespace vespalib::slime

