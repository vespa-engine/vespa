// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/threadstackexecutorbase.h>

namespace proton {

class ExecutorThreadingServiceStats {
public:
    using Stats = vespalib::ThreadStackExecutorBase::Stats;

private:
    Stats _masterExecutorStats;
    Stats _indexExecutorStats;
    Stats _summaryExecutorStats;
    Stats _indexFieldInverterExecutorStats;
    Stats _indexFieldWriterExecutorStats;
    Stats _attributeFieldWriterExecutorStats;
public:
    ExecutorThreadingServiceStats(Stats masterExecutorStats,
                                  Stats indexExecutorStats,
                                  Stats summaryExecutorStats,
                                  Stats indexFieldInverterExecutorStats,
                                  Stats indexFieldWriterExecutorStats,
                                  Stats attributeFieldWriterExecutorStats);
    ~ExecutorThreadingServiceStats();

    const Stats &getMasterExecutorStats() const { return _masterExecutorStats; }
    const Stats &getIndexExecutorStats() const { return _indexExecutorStats; }
    const Stats &getSummaryExecutorStats() const { return _summaryExecutorStats; }
    const Stats &getIndexFieldInverterExecutorStats() const { return _indexFieldInverterExecutorStats; }
    const Stats &getIndexFieldWriterExecutorStats() const { return _indexFieldWriterExecutorStats; }
    const Stats &getAttributeFieldWriterExecutorStats() const { return _attributeFieldWriterExecutorStats; }
};

}
