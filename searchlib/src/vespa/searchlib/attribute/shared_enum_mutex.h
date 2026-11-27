// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <condition_variable>
#include <cstdint>
#include <mutex>

namespace search::attribute {

/*
 * Reader/writer lock protecting the enum values of an attribute vector.
 *
 * Shared ownership is held by stable enum read guards, exclusive ownership by
 * EnumModifier while enum store compaction remaps enum refs.
 *
 * Unlike std::shared_mutex, this lock:
 *
 * - Is not owned by a thread. A shared lock may be released by another thread
 *   than the one that took it, and a thread may take several shared locks.
 *   Stable enum guards are cached per query in the attribute context and shared
 *   between the threads serving the query, so which thread takes and releases
 *   a guard is not under control of the code using it.
 *
 * - Prefers readers. A shared lock is only blocked by a writer holding the
 *   lock, never by one waiting for it. A writer waiting for readers to drain
 *   thus never blocks a reader that (directly or through another thread)
 *   already holds a shared lock, which would deadlock. This is the behaviour
 *   std::shared_mutex already had with libstdc++ on glibc (a reader-preferring
 *   pthread rwlock), now made explicit and platform independent.
 *
 *   The price is that a writer can starve: as long as stable enum guards keep
 *   overlapping (for instance queries sorting or grouping on the attribute at
 *   high rate), enum store compaction waits. While waiting, the compacting
 *   thread is the attribute's write thread, so feeding to attributes served by
 *   that thread stalls too. It also holds the attribute Interlock (see
 *   AttributeVector::getEnumModifier()), which in proton is shared by all
 *   attributes of all document databases, so enum store compaction of every
 *   other attribute, and the write threads doing it, waits for it as well.
 *
 * It meets the SharedMutex requirements, so std::shared_lock and
 * std::unique_lock work with it.
 */
class SharedEnumMutex {
    mutable std::mutex      _mutex;
    std::condition_variable _cond;
    uint32_t                _readers;
    uint32_t                _waiting_writers;
    bool                    _writer;

public:
    SharedEnumMutex() noexcept;
    SharedEnumMutex(const SharedEnumMutex&) = delete;
    SharedEnumMutex& operator=(const SharedEnumMutex&) = delete;
    ~SharedEnumMutex();

    void lock();
    bool try_lock();
    void unlock();

    void lock_shared();
    bool try_lock_shared();
    void unlock_shared();

    // For tests and diagnostics; the values may be stale as soon as they are returned.
    uint32_t readers() const;
    uint32_t waiting_writers() const;
};

} // namespace search::attribute
