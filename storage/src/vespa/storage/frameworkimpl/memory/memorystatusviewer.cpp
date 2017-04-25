// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memorystatusviewer.h"
#include <vespa/storage/storageutil/graph.h>
#include <vespa/storage/storageutil/palette.h>
#include <vespa/storage/storageutil/piechart.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/storageapi/messageapi/storagemessage.h>

#include <vespa/log/log.h>

LOG_SETUP(".memory.status.viewer");

using storage::framework::defaultimplementation::MemoryState;

namespace storage {

MemoryStatusViewer::Entry::Entry(
        const std::string& name, framework::Clock& clock,
        framework::SecondTime maxAge)
    : _name(name),
      _maxAge(maxAge),
      _timeTaken(clock.getTimeInSeconds()),
      _data(),
      _maxMemory(0)
{
}

MemoryStatusViewer::MemoryStatusViewer(
        framework::defaultimplementation::MemoryManager& mm,
        const metrics::MetricManager& metricMan,
        StorageComponentRegister& compReg)
    : framework::HtmlStatusReporter("memorymanager", "Memory Manager"),
      _component(compReg, "memorystatusviewer"),
      _manager(mm),
      _metricManager(metricMan),
      _workerMonitor(),
      _states(),
      _memoryHistory(),
      _memoryHistorySize(24 * 31),
      _memoryHistoryPeriod(60),
      _allowedSlackPeriod(6),
      _lastHistoryUpdate(_component.getClock().getTimeInSeconds()),
      _processedTime(0)
{
    addEntry("Current",                       0);
    addEntry("Last hour",               60 * 60);
    addEntry("Last day",           24 * 60 * 60);
    addEntry("Last month", 4 * 7 * 24 * 60 * 60);
    addEntry("Last ever", std::numeric_limits<uint32_t>::max());

    framework::MilliSecTime maxProcessingTime(60 * 1000);
    _thread = _component.startThread(*this, maxProcessingTime,
                                     framework::MilliSecTime(1000));
    _component.registerStatusPage(*this);
}

MemoryStatusViewer::~MemoryStatusViewer()
{
    if (_thread.get() != 0) {
        _thread->interrupt();
        {
            vespalib::MonitorGuard monitor(_workerMonitor);
            monitor.signal();
        }
        _thread->join();
    }
}

namespace {
    struct Group {
        std::set<const framework::MemoryAllocationType*> types;
        api::StorageMessage::Priority minPri;
        api::StorageMessage::Priority maxPri;
        MemoryState::Entry entry;

        Group(const framework::MemoryAllocationType& type,
              api::StorageMessage::Priority pri,
              const MemoryState::Entry& e)
            : types(),
              minPri(pri),
              maxPri(pri),
              entry(e)
        {
            types.insert(&type);
        }
    };

    struct GroupSizeOrder {
        bool operator()(const Group& g1, const Group& g2) {
            return (g1.entry._currentUsedSize > g2.entry._currentUsedSize);
        }
    };
    struct GroupAllocsOrder {
        bool operator()(const Group& g1, const Group& g2) {
            return (g1.entry._totalUserCount > g2.entry._totalUserCount);
        }
    };
    struct GroupMinAllocsOrder {
        bool operator()(const Group& g1, const Group& g2) {
            return (g1.entry._minimumCount > g2.entry._minimumCount);
        }
    };
    struct GroupDeniedAllocsOrder {
        bool operator()(const Group& g1, const Group& g2) {
            return (g1.entry._deniedCount > g2.entry._deniedCount);
        }
    };

    std::vector<Group> collapsePriorities(MemoryStatusViewer::Entry& entry) {
        std::vector<Group> groups;
        const MemoryState::SnapShot& ss(entry._data);
        for (MemoryState::AllocationMap::const_iterator it
                = ss.getAllocations().begin();
             it != ss.getAllocations().end(); ++it)
        {
            std::unique_ptr<Group> group;
            for (MemoryState::PriorityMap::const_iterator it2
                    = it->second.begin(); it2 != it->second.end(); ++it2)
            {
                if (group.get() == 0) {
                    group.reset(new Group(
                                        *it->first, it2->first, it2->second));
                } else {
                    group->entry += it2->second;
                    group->minPri = std::min(group->minPri, it2->first);
                    group->maxPri = std::max(group->maxPri, it2->first);
                }
            }
            if (group.get() != 0) {
                groups.push_back(*group);
            }
        }
        return groups;
    }

    std::vector<Group> groupLoad(uint32_t groupCount, uint64_t minSize,
                                 uint32_t minEntries,
                                 MemoryStatusViewer::Entry& entry)
    {
        assert(groupCount > 1);
        std::vector<Group> groups(collapsePriorities(entry));
        if (groups.size() == 0) return groups;
        std::sort(groups.begin(), groups.end(), GroupSizeOrder());
        assert(groups.front().entry._currentUsedSize
                    >= groups.back().entry._currentUsedSize);
        while (groups.size() > minEntries
               && (groups.size() > groupCount
                   || groups[groups.size() - 2].entry._currentUsedSize
                        < minSize))
        {
            Group& nextButLast(groups[groups.size() - 2]);
            Group& last(groups.back());
            if (last.entry._currentUsedSize > 0) {
                nextButLast.entry += last.entry;
                nextButLast.minPri = std::min(nextButLast.minPri, last.minPri);
                nextButLast.maxPri = std::max(nextButLast.maxPri, last.maxPri);
                nextButLast.types.insert(*last.types.begin());
            }
            groups.pop_back();
        }
        return groups;
    }

    std::vector<Group> groupAllocs(uint32_t groupCount, uint64_t minSize,
                                   uint32_t minEntries,
                                   MemoryStatusViewer::Entry& entry)
    {
        assert(groupCount > 1);
        std::vector<Group> groups(collapsePriorities(entry));
        if (groups.size() == 0) return groups;
        std::sort(groups.begin(), groups.end(), GroupAllocsOrder());
        assert(groups.front().entry._totalUserCount
                    >= groups.back().entry._totalUserCount);
        while (groups.size() > minEntries
               && (groups.size() > groupCount
                   || groups[groups.size() - 2].entry._totalUserCount
                        < minSize))
        {
            Group& nextButLast(groups[groups.size() - 2]);
            Group& last(groups.back());
            nextButLast.entry += last.entry;
            nextButLast.minPri = std::min(nextButLast.minPri, last.minPri);
            nextButLast.maxPri = std::max(nextButLast.maxPri, last.maxPri);
            nextButLast.types.insert(*last.types.begin());
            groups.pop_back();
        }
        return groups;
    }

    std::vector<Group> groupMinAllocs(uint32_t groupCount, uint64_t minSize,
                                      uint32_t minEntries,
                                      MemoryStatusViewer::Entry& entry)
    {
        assert(groupCount > 1);
        std::vector<Group> groups(collapsePriorities(entry));
        if (groups.size() == 0) return groups;
        std::sort(groups.begin(), groups.end(), GroupMinAllocsOrder());
        assert(groups.front().entry._minimumCount
                    >= groups.back().entry._minimumCount);
        while (groups.size() > minEntries
               && (groups.size() > groupCount
                   || groups[groups.size() - 2].entry._minimumCount
                        < minSize))
        {
            Group& nextButLast(groups[groups.size() - 2]);
            Group& last(groups.back());
            nextButLast.entry += last.entry;
            nextButLast.minPri = std::min(nextButLast.minPri, last.minPri);
            nextButLast.maxPri = std::max(nextButLast.maxPri, last.maxPri);
            nextButLast.types.insert(*last.types.begin());
            groups.pop_back();
        }
        return groups;
    }

    std::vector<Group> groupDeniedAllocs(uint32_t groupCount, uint64_t minSize,
                                         uint32_t minEntries,
                                         MemoryStatusViewer::Entry& entry)
    {
        assert(groupCount > 1);
        std::vector<Group> groups(collapsePriorities(entry));
        if (groups.size() == 0) return groups;
        std::sort(groups.begin(), groups.end(), GroupDeniedAllocsOrder());
        assert(groups.front().entry._deniedCount
                    >= groups.back().entry._deniedCount);
        while (groups.size() > minEntries
               && (groups.size() > groupCount
                   || groups[groups.size() - 2].entry._deniedCount
                        < minSize))
        {
            Group& nextButLast(groups[groups.size() - 2]);
            Group& last(groups.back());
            nextButLast.entry += last.entry;
            nextButLast.minPri = std::min(nextButLast.minPri, last.minPri);
            nextButLast.maxPri = std::max(nextButLast.maxPri, last.maxPri);
            nextButLast.types.insert(*last.types.begin());
            groups.pop_back();
        }
        return groups;
    }
}

void
MemoryStatusViewer::printSnapshot(
        std::ostream& out, Entry& entry,
        std::map<const framework::MemoryAllocationType*,
                 uint32_t>& colors) const
{
    out << "<h4>" << entry._name << " - Taken at "
        << entry._timeTaken.toString() << "</h4>\n"
        << "<table><tr><td>\n"
        << "<b>Memory usage";
    if (entry._name != "Current") {
        out << ", maxed at " << framework::SecondTime(entry._timeTaken);
    }
    out << " with "
        << (entry._data.getUsedSizeIgnoringCache() / (1024 * 1024))
        << " MB.</b><br>\n";
    std::string piename = entry._name;
    std::replace(piename.begin(), piename.end(), ' ', '_');
    uint64_t freeSize = entry._maxMemory - entry._data.getUsedSize();
        // Memory usage pie
    uint64_t minSize = freeSize / 20;
    std::vector<Group> groups(groupLoad(20, minSize, 5, entry));
    PieChart chart(piename, PieChart::SCHEME_CUSTOM);
    chart.printLabels(false);
    for (uint32_t i=0; i<groups.size(); ++i) {
        std::string name = "Other";
        if (groups[i].types.size() == 1) {
            name = (*groups[i].types.begin())->getName();
        }
        uint32_t mbytes = groups[i].entry._currentUsedSize / (1024 * 1024);
        std::ostringstream ost;
        ost << name << ", pri " << static_cast<uint16_t>(groups[i].minPri);
        if (groups[i].minPri != groups[i].maxPri) {
            ost << " - " << static_cast<uint16_t>(groups[i].maxPri);
        }
        ost << " (" << mbytes << " MB)";
        name = ost.str();
        if (groups[i].entry._currentUsedSize > 0) {
            chart.add(groups[i].entry._currentUsedSize, name,
                      colors[*groups[i].types.begin()]);
        }
    }
    {
        std::ostringstream ost;
        ost << "Free (" << (freeSize / (1024 * 1024)) << " MB)";
        chart.add(freeSize, ost.str(), colors[0]);
    }
    chart.printCanvas(out, 750, 300);
    out << "\n\n";
    chart.printScript(out, "");
    out << "\n\n";
        // Total allocations pie
    out << "</td><td>\n";
    PieChart allocChart(piename + "Alloc", PieChart::SCHEME_CUSTOM);
    allocChart.printLabels(false);
    groups = groupAllocs(20, 100, 5, entry);
    uint64_t totalAllocs = 0;
    for (uint32_t i=0; i<groups.size(); ++i) {
        std::string name = "Other";
        if (groups[i].types.size() == 1) {
            name = (*groups[i].types.begin())->getName();
        }
        uint32_t allocs = groups[i].entry._totalUserCount;
        totalAllocs += allocs;
        std::ostringstream ost;
        ost << name << ", pri " << static_cast<uint16_t>(groups[i].minPri);
        if (groups[i].minPri != groups[i].maxPri) {
            ost << " - " << static_cast<uint16_t>(groups[i].maxPri);
        }
        ost << " (" << allocs << " allocations)";
        name = ost.str();
        if (groups[i].entry._totalUserCount > 0) {
            allocChart.add(groups[i].entry._totalUserCount, name,
                           colors[*groups[i].types.begin()]);
        }
    }
    out << "<b>Allocations, totalling " << totalAllocs << "</b><br>\n";
    allocChart.printCanvas(out, 750, 300);
    out << "\n\n";
    allocChart.printScript(out, "");
    out << "\n\n";
    out << "</td></tr><tr><td>\n";
    PieChart minChart(piename + "Min", PieChart::SCHEME_CUSTOM);
    minChart.printLabels(false);
    groups = groupMinAllocs(20, 100, 5, entry);
    uint64_t totalMinAllocs = 0;
    for (uint32_t i=0; i<groups.size(); ++i) {
        std::string name = "Other";
        if (groups[i].types.size() == 1) {
            name = (*groups[i].types.begin())->getName();
        }
        uint32_t allocs = groups[i].entry._minimumCount;
        totalMinAllocs += allocs;
        std::ostringstream ost;
        ost << name << ", pri " << static_cast<uint16_t>(groups[i].minPri);
        if (groups[i].minPri != groups[i].maxPri) {
            ost << " - " << static_cast<uint16_t>(groups[i].maxPri);
        }
        ost << " (" << allocs << " min allocations)";
        name = ost.str();
        if (groups[i].entry._minimumCount > 0) {
            minChart.add(groups[i].entry._minimumCount, name,
                         colors[*groups[i].types.begin()]);
        }
    }
    out << "<b>Minimum allocations, totalling " << totalMinAllocs
        << "</b><br>\n";
    if (totalMinAllocs > 0) {
        minChart.printCanvas(out, 750, 300);
        out << "\n\n";
        minChart.printScript(out, "");
        out << "\n\n";
    }
    out << "</td><td>\n";
    PieChart deniedChart(piename + "Denied", PieChart::SCHEME_CUSTOM);
    deniedChart.printLabels(false);
    groups = groupDeniedAllocs(20, 100, 5, entry);
    uint64_t totalDeniedAllocs = 0;
    for (uint32_t i=0; i<groups.size(); ++i) {
        std::string name = "Other";
        if (groups[i].types.size() == 1) {
            name = (*groups[i].types.begin())->getName();
        }
        uint32_t allocs = groups[i].entry._deniedCount;
        totalDeniedAllocs += allocs;
        std::ostringstream ost;
        ost << name << ", pri " << static_cast<uint16_t>(groups[i].minPri);
        if (groups[i].minPri != groups[i].maxPri) {
            ost << " - " << static_cast<uint16_t>(groups[i].maxPri);
        }
        ost << " (" << allocs << " denied allocations)";
        name = ost.str();
        if (groups[i].entry._deniedCount > 0) {
            deniedChart.add(groups[i].entry._deniedCount, name,
                            colors[*groups[i].types.begin()]);
        }
    }
    out << "<b>Denied allocations, totalling " << totalDeniedAllocs
        << "</b><br>\n";
    if (totalDeniedAllocs > 0) {
        deniedChart.printCanvas(out, 750, 300);
        out << "\n\n";
        deniedChart.printScript(out, "");
        out << "\n\n";
    }
    out << "</td></tr></table>\n";
}

void
MemoryStatusViewer::reportHtmlHeaderAdditions(
        std::ostream& out, const framework::HttpUrlPath&) const
{
    (void) out;
    // FIXME this function used to emit Yahoo-internal links to graph plotting
    // JS files. Obviously, this won't work for external users. Either way, the
    // memory manager/status reporter is deprecated.
}

namespace {
    std::map<const framework::MemoryAllocationType*, uint32_t> assignColors(
            const std::vector<const framework::MemoryAllocationType*>& types)
    {
        Palette palette(types.size() + 1);
        std::map<const framework::MemoryAllocationType*, uint32_t> colors;
        uint32_t nextCol = 0;
        colors[0] = palette[nextCol++];
        for (std::vector<const framework::MemoryAllocationType*>
                ::const_iterator it = types.begin(); it != types.end(); ++it)
        {
            colors[*it] = palette[nextCol++];
        }
        return colors;
    }
}

void
MemoryStatusViewer::reportHtmlStatus(std::ostream& out,
                                     const framework::HttpUrlPath& path) const
{
    vespalib::MonitorGuard monitor(_workerMonitor);

    if (path.getAttribute("page") == "reset") {
    }
    if (path.getAttribute("interval") == "current") {
        Entry& e(*_states[0]);
        out << "<pre>" << e._name << ": ";
        if (e.containsData()) {
            e._data.print(out, true, "  ");
        } else {
            out << "na";
        }
        out << "\n</pre>";
        return;
    }
    const_cast<MemoryStatusViewer*>(this)->grabMemoryUsage();
    framework::SecondTime currentTime(_component.getClock().getTimeInSeconds());
    std::vector<const framework::MemoryAllocationType*> allocTypes(
            _manager.getAllocationTypes());
    std::map<const framework::MemoryAllocationType*, uint32_t> colors(
            assignColors(allocTypes));
        // Print memory usage graph
    {
        uint32_t mb = 1024 * 1024;
        Graph memoryHistory("memhistory", Graph::SCHEME_CUSTOM);
        std::vector<Graph::Point> total;
        std::vector<Graph::Point> used;
        std::vector<Graph::Point> usedWoCache;
        uint32_t xval = 0;
        for (std::deque<MemoryTimeEntry>::const_iterator it
                = _memoryHistory.begin(); it != _memoryHistory.end();
             ++it, ++xval)
        {
            used.push_back(Graph::Point(xval, it->used));
            usedWoCache.push_back(Graph::Point(xval, it->usedWithoutCache));
        }
        used.push_back(Graph::Point(
                    xval, _states[0]->_data.getUsedSize() / mb));
        usedWoCache.push_back(Graph::Point(
                    xval, _states[0]->_data.getUsedSizeIgnoringCache() / mb));
        uint32_t totalSize = _states[0]->_maxMemory / mb;
        total.push_back(Graph::Point(0, totalSize));
        total.push_back(Graph::Point(xval, totalSize));
        memoryHistory.add(total, "Total memory", Graph::GREEN);
        memoryHistory.add(used, "Used memory", Graph::YELLOW);
        memoryHistory.add(usedWoCache, "Used memory excluding freeable cache",
                          Graph::RED);
        out << "<p>Memory available for lowest priority (255): "
            << _manager.getMemorySizeFreeForPriority(255) << " byte(s).</p>\n";
        out << "<h3>Historic memory usage</h3>\n";
        uint32_t yAxisUnit = ((totalSize / 4) / 256) * 256;
        if (yAxisUnit == 0) yAxisUnit = (totalSize / 4);
        if (yAxisUnit == 0) yAxisUnit = 1;
        uint32_t size = yAxisUnit;
        memoryHistory.addYAxisLabel(0, "0 B");
        while (size <= totalSize) {
            std::ostringstream label;
            if (size % 1024 == 0) {
                label << (size / 1024) << " GB";
            } else {
                label << size << " MB";
            }
            memoryHistory.addYAxisLabel(size, label.str());
            size += yAxisUnit;
        }
        uint32_t xAxisUnit = ((_memoryHistory.size() / 4) / 24) * 24;
        if (xAxisUnit == 0) xAxisUnit = _memoryHistoryPeriod.getTime();
        uint32_t startTime = ((currentTime.getTime()
                                / _memoryHistoryPeriod.getTime())
                              / 24) * 24;
        uint32_t stopTime = (currentTime.getTime()
                                / _memoryHistoryPeriod.getTime())
                          - _memoryHistory.size() + 1;
        memoryHistory.addXAxisLabel(xval, currentTime.toString());
        bool addedMiddlePoints = false;
        while (startTime >= stopTime) {
            if (currentTime.getTime() / _memoryHistoryPeriod.getTime()
                    - startTime > 48)
            {
                memoryHistory.addXAxisLabel(
                        (startTime - stopTime),
                        framework::SecondTime(
                                startTime * _memoryHistoryPeriod.getTime())
                            .toString());
                addedMiddlePoints = true;
            }
            startTime -= xAxisUnit;
        }
        if (!addedMiddlePoints && _memoryHistory.size() > 2) {
            memoryHistory.addXAxisLabel(
                    1,
                    framework::SecondTime(
                            stopTime * _memoryHistoryPeriod.getTime())
                        .toString());
        }
        memoryHistory.setBorders(50, 0, 0, 30);
        memoryHistory.setLegendPos(80, 20);
        memoryHistory.printCanvas(out, 1000, 250);
        memoryHistory.printScript(out, "");
    }
    uint32_t maxUsedWithoutCache = 0;
    for (uint32_t i=0; i<_states.size(); ++i) {
        Entry& e(*_states[i]);
        if (!e.containsData()
            || e._data.getUsedSizeIgnoringCache() == maxUsedWithoutCache)
        {
            continue;
        }
        printSnapshot(out, e, colors);
        maxUsedWithoutCache = e._data.getUsedSizeIgnoringCache();
    }
    out << "<h3>Raw output of stored data</h3>\n"
        << "<pre>\n";
    monitor.unlock();
    printDebugOutput(out);
    out << "</pre>\n";
    out << "<h2>Memory used for metrics. (Not tracked in memory manager)</h2>\n"
        << "<pre>\n"
        << _metricManager.getMemoryConsumption(_metricManager.getMetricLock())->toString()
        << "\n</pre>\n";
}

void
MemoryStatusViewer::run(framework::ThreadHandle& thread)
{
    while (!thread.interrupted()) {
        vespalib::MonitorGuard monitor(_workerMonitor);
        framework::SecondTime currentTime(
                _component.getClock().getTimeInSeconds());
        if (_lastHistoryUpdate + _memoryHistoryPeriod <= currentTime
            || _states[0]->_timeTaken + _memoryHistoryPeriod <= currentTime)
        {
            grabMemoryUsage();
            _processedTime = currentTime;
            LOG(spam, "Done processing time %" PRIu64, currentTime.getTime());
            thread.registerTick(framework::PROCESS_CYCLE);
        } else {
            monitor.wait(thread.getWaitTime());
            thread.registerTick(framework::WAIT_CYCLE);
        }
    }
}

// You should have worker monitor when calling this function
void
MemoryStatusViewer::grabMemoryUsage()
{
    framework::SecondTime currentTime(_component.getClock().getTimeInSeconds());
    MemoryState state(_component.getClock(), 0);
    _manager.getState(state, true);

    if (_lastHistoryUpdate + _memoryHistoryPeriod <= currentTime) {
        LOG(spam, "Adding another %" PRIu64 " sec entry to memory history.",
            _memoryHistoryPeriod.getTime());
        // Add history once an hour
        uint32_t mb = 1024 * 1024;
        _memoryHistory.push_back(MemoryTimeEntry(
                state.getMaxSnapshot().getUsedSize() / mb,
                state.getMaxSnapshot().getUsedSizeIgnoringCache() / mb));
        if (_memoryHistory.size() > _memoryHistorySize) {
            if (_memoryHistoryPeriod != framework::SecondTime(60 * 60)) {
                uint32_t periodDiff = 60 * 60 / _memoryHistoryPeriod.getTime();
                std::deque<MemoryTimeEntry> newHistory;
                uint32_t count = 0;
                MemoryTimeEntry entry(0, 0);
                for (std::deque<MemoryTimeEntry>::const_iterator it
                        = _memoryHistory.begin();
                        it != _memoryHistory.end(); ++it)
                {
                    entry.keepMax(*it);
                    if (++count == periodDiff) {
                        newHistory.push_back(entry);
                        entry = MemoryTimeEntry(0, 0);
                        count = 0;
                    }
                }
                if (entry.used != 0) {
                    newHistory.push_back(entry);
                }
                _memoryHistory.swap(newHistory);
                _memoryHistoryPeriod = framework::SecondTime(60 * 60);
            }
        }
        _lastHistoryUpdate += _memoryHistoryPeriod;
        if (_lastHistoryUpdate + _allowedSlackPeriod < currentTime) {
            LOGBP(warning, "Memory history is supposed to be tracked every %"
                           PRIu64 " seconds, but %" PRIu64" seconds have passed "
                           "since last update. Memory history graph will be "
                           "incorrect.",
                _memoryHistoryPeriod.getTime(),
                (currentTime - _lastHistoryUpdate + _memoryHistoryPeriod)
                        .getTime());
            _lastHistoryUpdate = currentTime;
        }
    }
    LOG(spam, "Overwriting current with snapshot using %" PRIu64 " bytes.",
        state.getCurrentSnapshot().getUsedSize());
    _states[0]->assign(state.getCurrentSnapshot(),
                       state.getTotalSize(), currentTime);
    for (uint32_t i=1, n=_states.size(); i<n; ++i) {
        if (currentTime - _states[i]->_timeTaken >= _states[i]->_maxAge
            || state.getMaxSnapshot().getUsedSize()
                > _states[i]->_data.getUsedSize())
        {
            LOG(spam, "Updating period %s usage. Old usage was %" PRIu64 ". "
                      "Last set at %" PRIu64,
                _states[i]->_name.c_str(), _states[i]->_data.getUsedSize(),
                _states[i]->_timeTaken.getTime());
            _states[i]->assign(state.getMaxSnapshot(),
                               state.getTotalSize(), currentTime);
        }
    }
}

void
MemoryStatusViewer::notifyThread() const
{
    vespalib::MonitorGuard monitor(_workerMonitor);
    monitor.broadcast();
}

void
MemoryStatusViewer::printDebugOutput(std::ostream& out) const
{
    vespalib::MonitorGuard monitor(_workerMonitor);
    for (uint32_t i=0; i<_states.size(); ++i) {
        Entry& e(*_states[i]);
        out << e._name << ": ";
        if (e.containsData()) {
            out << e._timeTaken.toString() << " Max memory " << e._maxMemory << " ";
            e._data.print(out, true, "  ");
        } else {
            out << "na";
        }
        out << "\n\n";
    }
}

} // storage
