// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search
{

namespace diskindex
{

class FileHeader
{
private:
    bool _bigEndian;
    bool _hostEndian;
    bool _completed;
    bool _allowNoFileBitSize;
    uint32_t _version;
    uint32_t _headerLen;
    uint64_t _fileBitSize;
    std::vector<vespalib::string> _formats;

public:
    FileHeader();

    ~FileHeader();

    bool
    taste(const vespalib::string &name,
          const TuneFileSeqRead &tuneFileRead);

    bool
    taste(const vespalib::string &name,
          const TuneFileSeqWrite &tuneFileWrite);

    bool
    taste(const vespalib::string &name,
          const TuneFileRandRead &tuneFileSearch);

    bool
    getBigEndian() const
    {
        return _bigEndian;
    }

    bool
    getHostEndian() const
    {
        return _hostEndian;
    }

    uint32_t
    getVersion() const
    {
        return _version;
    }

    uint32_t
    getHeaderLen() const
    {
        return _headerLen;
    }

    const std::vector<vespalib::string> &
    getFormats() const
    {
        return _formats;
    }

    bool
    getCompleted() const
    {
        return _completed;
    }

    void
    setAllowNoFileBitSize()
    {
        _allowNoFileBitSize = true;
    }
};


} // namespace diskindex

} // namespace search

