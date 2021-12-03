// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ichunk.h"
#include <vespa/vespalib/util/time.h>
#include <map>

namespace search::transactionlog {

class DomainConfig {
public:
    using duration = vespalib::duration;
    DomainConfig();
    DomainConfig & setEncoding(Encoding v);
    DomainConfig & setPartSizeLimit(size_t v)       { _partSizeLimit = v; return *this; }
    DomainConfig & setChunkSizeLimit(size_t v)      { _chunkSizeLimit = v; return *this; }
    DomainConfig & setCompressionLevel(uint8_t v)   { _compressionLevel = v; return *this; }
    DomainConfig & setFSyncOnCommit(bool v)         { _fSyncOnCommit = v; return *this; }
    Encoding          getEncoding() const { return _encoding; }
    size_t       getPartSizeLimit() const { return _partSizeLimit; }
    size_t      getChunkSizeLimit() const { return _chunkSizeLimit; }
    uint8_t   getCompressionlevel() const { return _compressionLevel; }
    bool         getFSyncOnCommit() const { return _fSyncOnCommit; }
private:
    Encoding     _encoding;
    uint8_t      _compressionLevel;
    bool         _fSyncOnCommit;
    size_t       _partSizeLimit;
    size_t       _chunkSizeLimit;
};

struct PartInfo {
    SerialNumRange range;
    size_t numEntries;
    size_t byteSize;
    vespalib::string file;
    PartInfo(SerialNumRange range_in, size_t numEntries_in, size_t byteSize_in, vespalib::stringref file_in)
            : range(range_in),
              numEntries(numEntries_in),
              byteSize(byteSize_in),
              file(file_in)
    {}
};

struct DomainInfo {
    using DurationSeconds = std::chrono::duration<double>;
    SerialNumRange range;
    size_t numEntries;
    size_t byteSize;
    DurationSeconds maxSessionRunTime;
    std::vector<PartInfo> parts;
    DomainInfo(SerialNumRange range_in, size_t numEntries_in, size_t byteSize_in, DurationSeconds maxSessionRunTime_in)
            : range(range_in), numEntries(numEntries_in), byteSize(byteSize_in), maxSessionRunTime(maxSessionRunTime_in), parts() {}
    DomainInfo()
            : range(), numEntries(0), byteSize(0), maxSessionRunTime(), parts() {}
};

using DomainStats = std::map<vespalib::string, DomainInfo>;

}
