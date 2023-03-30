// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute { class Status; }
namespace vespalib { class MemoryUsage; }
namespace vespalib::slime { struct Cursor; }

namespace search {

/**
 * Utility functions for state explorers to convert objects to slime.
 */
class StateExplorerUtils {
public:
    static void memory_usage_to_slime(const vespalib::MemoryUsage& usage, vespalib::slime::Cursor& object);
    static void status_to_slime(const search::attribute::Status &status, vespalib::slime::Cursor &object);
};

}

