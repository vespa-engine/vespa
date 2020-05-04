// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::datastore {

/**
 * Interface for moving an entry as part of compaction of data in old
 * buffers into new buffers.
 *
 * Old entry is unchanged and not placed on any hold lists since we
 * expect the old buffers to be freed soon anyway.
 */
struct ICompactable {
    virtual ~ICompactable() = default;
    virtual EntryRef move(EntryRef ref) = 0;
};

}
