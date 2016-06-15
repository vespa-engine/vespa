// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace search
{

namespace common
{

class FileHeaderContext;

}

namespace diskindex
{

class CheckPointFile
{
public:
    FastOS_File _file;
    vespalib::string _name;
    vespalib::string _nameNew;
    vespalib::string _nameNewNew;
    bool _writeOpened;
    uint32_t _headerLen;

    void
    writeOpen(const common::FileHeaderContext &fileHeaderContext);

    bool
    readOpen(void);

    void
    close(void);

    void
    rename1(void);

    void
    rename2(void);

    void
    remove(void);

    void
    makeHeader(const common::FileHeaderContext &fileHeaderContext);

    void
    updateHeader(void);

    uint32_t
    readHeader(void);
public:
    CheckPointFile(const vespalib::string &name);

    ~CheckPointFile(void);

    void
    write(vespalib::nbostream &buf,
          const common::FileHeaderContext &fileHeaderContext);

    bool
    read(vespalib::nbostream &buf);
};


} // namespace diskindex

} // namespace search

