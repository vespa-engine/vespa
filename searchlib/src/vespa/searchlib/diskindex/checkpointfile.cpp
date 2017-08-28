// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checkpointfile.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.checkpointfile");

using vespalib::getLastErrorString;

namespace search::diskindex {

using common::FileHeaderContext;

CheckPointFile::CheckPointFile(const vespalib::string &name)
    : _file(),
      _name(name),
      _nameNew(name + ".NEW"),
      _nameNewNew(name + ".NEW.NEW"),
      _writeOpened(false),
      _headerLen(0u)
{ }


CheckPointFile::~CheckPointFile()
{
    close();
}


void
CheckPointFile::writeOpen(const FileHeaderContext &fileHeaderContext)
{
    FastOS_File::Delete(_nameNewNew.c_str());
    _file.OpenWriteOnly(_nameNewNew.c_str());
    _writeOpened = true;
    makeHeader(fileHeaderContext);
}


bool
CheckPointFile::readOpen()
{
    bool openres;

    openres = _file.OpenReadOnly(_name.c_str());
    if (!openres) {
        bool renameres = FastOS_File::Rename(_nameNew.c_str(),
                _name.c_str());
        if (!renameres)
            return false;
        openres = _file.OpenReadOnly(_name.c_str());
        if (!openres)
            return false;
    }
    _headerLen = readHeader();
    return true;
}


void
CheckPointFile::close()
{
    if (_writeOpened) {
        _file.Sync();
    }
    _file.Close();
    if (_writeOpened) {
        updateHeader();
        rename1();
        rename2();
    }
    _writeOpened = false;
}


void
CheckPointFile::rename1()
{
    FastOS_File::Delete(_nameNew.c_str());
    bool renameres = FastOS_File::Rename(_nameNewNew.c_str(),
            _nameNew.c_str());
    if (!renameres) {
        LOG(error, "FATAL: rename %s -> %s failed: %s",
            _nameNewNew.c_str(), _nameNew.c_str(), getLastErrorString().c_str());
        abort();
    }
}


void
CheckPointFile::rename2()
{
    FastOS_File::Delete(_name.c_str());
    bool renameres = FastOS_File::Rename(_nameNew.c_str(), _name.c_str());
    if (!renameres) {
        LOG(error, "FATAL: rename %s -> %s failed: %s",
            _nameNew.c_str(), _name.c_str(), getLastErrorString().c_str());
        abort();
    }
}


void
CheckPointFile::remove()
{
    FastOS_File::Delete(_nameNew.c_str());
    FastOS_File::Delete(_name.c_str());
}



void
CheckPointFile::write(vespalib::nbostream &buf,
                      const FileHeaderContext &fileHeaderContext)
{
    writeOpen(fileHeaderContext);
    _file.WriteBuf(buf.peek(), buf.size());
    close();
}


bool
CheckPointFile::read(vespalib::nbostream &buf)
{
    if (!readOpen())
        return false;
    size_t sz = _file.GetSize() - _headerLen;

    std::vector<char> tmp(sz);
    _file.ReadBuf(&tmp[0], sz);
    buf.clear();
    buf.write(&tmp[0], sz);
    std::vector<char>().swap(tmp);
    close();
    return true;
}


void
CheckPointFile::makeHeader(const FileHeaderContext &fileHeaderContext)
{
    vespalib::FileHeader header;

    typedef vespalib::GenericHeader::Tag Tag;
    fileHeaderContext.addTags(header, _file.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("desc", "Check point file"));
    header.writeFile(_file);
}


void
CheckPointFile::updateHeader()
{
    vespalib::FileHeader h;
    FastOS_File f;
    f.OpenReadWrite(_nameNewNew.c_str());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.rewriteFile(f);
    f.Sync();
    f.Close();
}


uint32_t
CheckPointFile::readHeader()
{
    vespalib::FileHeader h;
    uint32_t headerLen = h.readFile(_file);
    _file.SetPosition(headerLen);
    assert(h.hasTag("frozen"));
    assert(h.getTag("frozen").asInteger() != 0);
    return headerLen;
}


}
