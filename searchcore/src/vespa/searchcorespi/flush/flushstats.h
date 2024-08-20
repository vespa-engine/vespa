// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace searchcorespi {

/**
 * Class with stats for what have been flushed.
 */
class FlushStats
{
private:
    std::string _path; // path to data flushed
    size_t           _pathElementsToLog;

public:
    FlushStats();

    void setPath(const std::string & path) { _path = path; }
    void setPathElementsToLog(size_t numElems) { _pathElementsToLog = numElems; }

    const std::string & getPath() const { return _path; }
    size_t getPathElementsToLog() const { return _pathElementsToLog; }
};

} // namespace searchcorespi

