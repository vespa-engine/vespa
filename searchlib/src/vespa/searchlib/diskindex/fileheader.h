// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/tunefileinfo.h>
#include <string>
#include <vector>

namespace search::diskindex {

class FileHeader
{
private:
    bool _bigEndian;
    bool _completed;
    uint32_t _version;
    uint32_t _headerLen;
    uint64_t _fileBitSize;
    std::vector<std::string> _formats;

public:
    FileHeader();
    ~FileHeader();

    bool taste(const std::string &name, const TuneFileSeqRead &tuneFileRead);
    bool taste(const std::string &name, const TuneFileSeqWrite &tuneFileWrite);
    bool taste(const std::string &name, const TuneFileRandRead &tuneFileSearch);
    bool getBigEndian() const { return _bigEndian; }
    uint32_t getVersion() const { return _version; }
    const std::vector<std::string> &getFormats() const { return _formats; }
};

}
