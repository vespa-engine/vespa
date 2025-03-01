// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::slime { struct Cursor; }

namespace vespalib {

class MemoryUsage;

/**
 * Utility functions for state explorers to convert objects to slime.
 */
class StateExplorerUtils {
public:
    static void memory_usage_to_slime(const MemoryUsage& usage, slime::Cursor& object);
};

}
