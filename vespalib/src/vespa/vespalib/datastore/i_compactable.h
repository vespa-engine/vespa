// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::datastore {

/**
 * Interface for moving an entry as part of compaction of data in old
 * buffers into new buffers.
 *
 * A copy of the old entry is created and a reference to the new copy is
 * returned. The old entry is unchanged and not placed on any hold
 * lists since we expect the old buffers to be freed soon anyway.
 */
struct ICompactable {
    virtual ~ICompactable() = default;
    virtual EntryRef move_on_compact(EntryRef ref) = 0;
};

}
