// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "randread.h"
#include <vespa/vespalib/util/ptrholder.h>
#include <string>

class FastOS_FileInterface;

namespace search {

class DirectIORandRead : public FileRandRead
{
public:
    DirectIORandRead(const std::string & fileName);
    ~DirectIORandRead() override;
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() const override;
private:
    std::unique_ptr<FastOS_FileInterface>  _file;
    size_t                                 _alignment;
    size_t                                 _granularity;
    size_t                                 _maxChunkSize;
};

class MMapRandRead : public FileRandRead
{
public:
    MMapRandRead(const std::string & fileName, int mmapFlags, int fadviseOptions);
    ~MMapRandRead() override;
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() const override;
    const void * getMapping();
private:
    std::unique_ptr<FastOS_FileInterface>  _file;
};

class MMapRandReadDynamic : public FileRandRead
{
public:
    MMapRandReadDynamic(const std::string & fileName, int mmapFlags, int fadviseOptions);
    ~MMapRandReadDynamic() override;
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() const override;
private:
    static bool contains(const FastOS_FileInterface & file, size_t sz);
    void remap(size_t end);
    std::string                          _fileName;
    vespalib::PtrHolder<FastOS_FileInterface> _holder;
    int                                       _mmapFlags;
    int                                       _fadviseOptions;
    std::mutex                                _lock;
};

class NormalRandRead : public FileRandRead
{
public:
    NormalRandRead(const std::string & fileName);
    ~NormalRandRead() override;
    FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) override;
    int64_t getSize() const override;
private:
    std::unique_ptr<FastOS_FileInterface>  _file;
};

}
