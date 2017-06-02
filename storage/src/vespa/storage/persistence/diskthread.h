// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class DiskThread
 * @ingroup persistence
 *
 * @brief Implements the public API of the disk threads.
 *
 * The disk threads have a tiny interface as they pull messages of the disk
 * queue themselves. Thus it is easy to provide multiple implementations of it.
 * The diskthread implements the common functionality needed above, currently
 * for the filestor manager.
 */
#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/config-stor-filestor.h>
#include <vespa/storageframework/generic/thread/runnable.h>


namespace storage {

namespace framework {
    class Thread;
}

class Directory;
class SlotFileOptions;
struct FileStorThreadMetrics;

class DiskThread : public framework::Runnable
{
public:
    typedef std::shared_ptr<DiskThread> SP;

    DiskThread(const DiskThread &) = delete;
    DiskThread & operator = (const DiskThread &) = delete;
    DiskThread() = default;
    virtual ~DiskThread() {}

    /**
     * Query filestorthread for its operation count.
     *
     * Count is increased for each operation (and rolls around). If you query
     * filestorthread, and count has not changed over a period of time that is
     * longer than a single operation should use, and shorter than
     * filestorthread can manage 2^32 operations, you can detect if the thread
     * is stuck.
     *
     * (No locking is used for this. We assume instance don't manage to get
     * partially updated to look exactly like the last retrieved entry)
     */
    struct OperationCount : public vespalib::Printable {
        uint32_t count;
        bool pending;

        OperationCount() : count(0), pending(false) {}

        void inc() { ++count; pending = true; }
        void done() { pending = false; }

        bool operator==(const OperationCount& c) const
            { return (count == c.count && pending == c.pending); }

        void print(std::ostream& out, bool, const std::string&) const override
        {
            out << "OperationCount(" << count << (pending ? ", pending" : "")
                << ")";
        }
    };

    /** Waits for current operation to be finished. */
    virtual void flush() = 0;

    virtual framework::Thread& getThread() = 0;

};

}

