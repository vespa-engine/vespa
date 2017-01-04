// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "randread.h"
#include <vespa/vespalib/util/ptrholder.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastos/file.h>

namespace search {

class DirectIORandRead : public FileRandRead
{
public:
    DirectIORandRead(const vespalib::string & fileName);
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() override;
private:
    FastOS_File      _file;
    size_t           _alignment;
    size_t           _granularity;
    size_t           _maxChunkSize;
};

class MMapRandRead : public FileRandRead
{
public:
    MMapRandRead(const vespalib::string & fileName, int mmapFlags, int fadviseOptions);
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() override;
    const void * getMapping() { return _file.MemoryMapPtr(0); }
private:
    FastOS_File      _file;
};

class MMapRandReadDynamic : public FileRandRead
{
public:
    MMapRandReadDynamic(const vespalib::string & fileName, int mmapFlags, int fadviseOptions);
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() override;
private:
    void reopen();
    vespalib::string                 _fileName;
    vespalib::PtrHolder<FastOS_File> _holder;
    int                              _mmapFlags;
    int                              _fadviseOptions;
};

class NormalRandRead : public FileRandRead
{
public:
    NormalRandRead(const vespalib::string & fileName);
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() override;
private:
    FastOS_File      _file;
};

}
