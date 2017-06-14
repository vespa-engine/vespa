// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MemoryStatusViewer
 *
 * \brief Generates status to access through status pages.
 *
 * Keeps a history of the largest memory inprints seen historically. This is
 * done be defining periods, where a period is always a multiplum of the length
 * of the period shorter than it. The last entry will store the biggest memory
 * imprint ever seen, and the earlier entries will show biggest for their time
 * period.
 *
 * To avoid having all periods cleared once the biggest period resets, the
 * periods keep data for each of the periods one size below it. Thus, a year
 * keeps data for 12 months, a month for 30 days, and so on.
 *
 * The memory state objects are divided in 3 parts. Current memory data, max
 * memory data since since reset and counts for how often various events have
 * happened.
 *
 * The counts will have their total count values stored in the current entry.
 * When the next period is updated getting a copy of these counts, we can see
 * how many counts have happened recently, by taking the current entry and
 * subtract those accounted for earlier.
 *
 * The current memory data will not be interesting for anything than to show the
 * actual now values in the current entry.
 *
 * The max since reset values will be the values used for the various periods.
 * When a period is updated with new data for a subpart of their period, the
 * max seen data is reset in the period in front, such that a lower maximum
 * can be found.
 */

#pragma once

#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storageframework/defaultimplementation/memory/memorystate.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/vespalib/util/sync.h>
#include <deque>
#include <vector>


namespace metrics {
    class MetricManager;
}

namespace storage {

class StorageServerInterface;

class MemoryStatusViewer : public framework::HtmlStatusReporter,
                           private framework::Runnable
{
public:
    typedef framework::defaultimplementation::MemoryState::SnapShot SnapShot;
    struct Entry {
        typedef std::shared_ptr<Entry> SP;

        std::string _name;
        framework::SecondTime _maxAge;
        framework::SecondTime _timeTaken;
        SnapShot _data;
        uint64_t _maxMemory;

        Entry(const std::string& name, framework::Clock&,
              framework::SecondTime maxAge);
        bool containsData() const { return (_maxMemory != 0); }

        void assign(const SnapShot& snapshot, uint64_t maxMemory,
                    framework::SecondTime time)
        {
            _data = snapshot;
            _maxMemory = maxMemory;
            _timeTaken = time;
        }
    };

    struct MemoryTimeEntry {
        uint64_t used;
        uint64_t usedWithoutCache;

        MemoryTimeEntry(uint64_t u, uint64_t wo)
            : used(u), usedWithoutCache(wo) {}

        void keepMax(const MemoryTimeEntry& e) {
            used = (used > e.used ? used : e.used);
            usedWithoutCache = (usedWithoutCache > e.usedWithoutCache
                    ? usedWithoutCache : e.usedWithoutCache);
        }
    };

private:
    framework::Component _component;
    framework::defaultimplementation::MemoryManager& _manager;
    const metrics::MetricManager& _metricManager;
    vespalib::Monitor _workerMonitor;

    std::vector<Entry::SP> _states;
    std::deque<MemoryTimeEntry> _memoryHistory;
    uint32_t _memoryHistorySize;
    framework::SecondTime _memoryHistoryPeriod;
    framework::SecondTime _allowedSlackPeriod;
    framework::SecondTime _lastHistoryUpdate;
    framework::Thread::UP _thread;
    framework::SecondTime _processedTime;

    void addEntry(const std::string& name, uint32_t maxAge) {
        _states.push_back(Entry::SP(new Entry(name, _component.getClock(),
                                              framework::SecondTime(maxAge))));
    }
    void run(framework::ThreadHandle&) override;
    void grabMemoryUsage();
    void printSnapshot(std::ostream& out, Entry& entry,
                       std::map<const framework::MemoryAllocationType*,
                                uint32_t>& colors) const;

public:
    MemoryStatusViewer(
            framework::defaultimplementation::MemoryManager&,
            const metrics::MetricManager&,
            StorageComponentRegister&);
    ~MemoryStatusViewer();

    void reportHtmlHeaderAdditions(std::ostream&, const framework::HttpUrlPath&) const override;
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    /** Useful for testing. */
    framework::SecondTime getProcessedTime() const { return _processedTime; }
    void notifyThread() const;
    void printDebugOutput(std::ostream&) const;

};

}
