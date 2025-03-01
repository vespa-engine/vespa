// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/state_explorer_utils.h>

namespace search::attribute { class Status; }

namespace search {

/**
 * Utility functions for state explorers to convert objects to slime.
 */
class StateExplorerUtils : public vespalib::StateExplorerUtils {
public:
    static void status_to_slime(const search::attribute::Status &status, vespalib::slime::Cursor &object);
};

}

