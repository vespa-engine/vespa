// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search::diskindex {

class FileHeader
{
private:
    bool _bigEndian;
    bool _completed;
    uint32_t _version;
    uint32_t _headerLen;
    uint64_t _fileBitSize;
    std::vector<vespalib::string> _formats;

public:
    FileHeader();
    ~FileHeader();

    bool taste(const vespalib::string &name, const TuneFileSeqRead &tuneFileRead);
    bool taste(const vespalib::string &name, const TuneFileSeqWrite &tuneFileWrite);
    bool taste(const vespalib::string &name, const TuneFileRandRead &tuneFileSearch);
    bool getBigEndian() const { return _bigEndian; }
    uint32_t getVersion() const { return _version; }
    const std::vector<vespalib::string> &getFormats() const { return _formats; }
};

}
