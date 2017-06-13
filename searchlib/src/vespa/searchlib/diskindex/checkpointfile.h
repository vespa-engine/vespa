// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/fastos/file.h>

namespace search {

namespace common { class FileHeaderContext; }

namespace diskindex {

class CheckPointFile
{
public:
    FastOS_File _file;
    vespalib::string _name;
    vespalib::string _nameNew;
    vespalib::string _nameNewNew;
    bool _writeOpened;
    uint32_t _headerLen;

    void writeOpen(const common::FileHeaderContext &fileHeaderContext);
    bool readOpen();
    void close();
    void rename1();
    void rename2();
    void remove();
    void makeHeader(const common::FileHeaderContext &fileHeaderContext);
    void updateHeader();
    uint32_t readHeader();
public:
    CheckPointFile(const CheckPointFile &) = delete;
    CheckPointFile & operator = (const CheckPointFile &) = delete;
    CheckPointFile(const vespalib::string &name);
    ~CheckPointFile();

    void write(vespalib::nbostream &buf, const common::FileHeaderContext &fileHeaderContext);
    bool read(vespalib::nbostream &buf);
};


} // namespace diskindex

} // namespace search

