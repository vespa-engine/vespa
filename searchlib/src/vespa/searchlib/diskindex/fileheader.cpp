// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheader.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/fastos/file.h>
#include <arpa/inet.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.fileheader");

namespace search::diskindex {

using bitcompression::FeatureDecodeContextBE;

FileHeader::FileHeader()
    : _bigEndian(false),
      _hostEndian(false),
      _completed(false),
      _allowNoFileBitSize(false),
      _version(0),
      _headerLen(0),
      _fileBitSize(0),
      _formats()
{
}

FileHeader::~FileHeader() {}

bool
FileHeader::taste(const vespalib::string &name,
                  const TuneFileSeqRead &tuneFileRead)
{
    vespalib::FileHeader header;
    FastOS_File file;

    if (tuneFileRead.getWantDirectIO())
        file.EnableDirectIO();
    bool res = file.OpenReadOnly(name.c_str());
    if (!res) {
        return false;
    }

    uint32_t headerLen = 0u;
    uint64_t fileSize = file.GetSize();
    try {
        headerLen = header.readFile(file);
        assert(headerLen >= header.getSize());
        (void) headerLen;
    } catch (vespalib::IllegalHeaderException &e) {
        if (e.getMessage() != "Failed to read header info." &&
            e.getMessage() != "Failed to verify magic bits.") {
            LOG(error, "FileHeader::tastGeneric(\"%s\") exception: %s",
                name.c_str(), e.getMessage().c_str());
        }
        file.Close();
        return false;
    }
    file.Close();

    _version = 1;
    _headerLen = headerLen;
    _bigEndian = htonl(1) == 1;
    if (header.hasTag("endian")) {
        vespalib::string endian(header.getTag("endian").asString());
        if (endian == "big") {
            _bigEndian = true;
        } else if (endian == "little") {
            _bigEndian = false;
        } else {
            LOG(error, "Bad endian: %s", endian.c_str());
            return false;
        }
    }
    _hostEndian = _bigEndian == (htonl(1) == 1);
    if (header.hasTag("frozen")) {
        _completed = header.getTag("frozen").asInteger() != 0;
    } else {
        LOG(error, "FileHeader::taste(\"%s\"): Missing frozen tag", name.c_str());
        return false;
    }
    if (header.hasTag("fileBitSize")) {
        _fileBitSize = header.getTag("fileBitSize").asInteger();
        if (_completed && _fileBitSize < 8 * _headerLen) {
            LOG(error, "FileHeader::taste(\"%s\"): fleBitSize(%" PRIu64 ") < 8 * headerLen(%u)",
                name.c_str(), _fileBitSize, _headerLen);
            return false;
        }
        if (_completed && _fileBitSize > 8 * fileSize) {
            LOG(error, "FileHeader::taste(\"%s\"): fleBitSize(%" PRIu64 ") > 8 * fileSize(%" PRIu64 ")",
                name.c_str(), _fileBitSize, fileSize);
            LOG_ABORT("should not be reached");
        }
    } else if (!_allowNoFileBitSize) {
        LOG(error, "FileHeader::taste(\"%s\"): Missing fileBitSize tag", name.c_str());
        return false;
    }
    for (uint32_t i = 0; ;++i) {
        vespalib::asciistream as;
        as << "format." << i;
        vespalib::stringref key(as.str());
        if (!header.hasTag(key))
            break;
        _formats.push_back(header.getTag(key).asString());
    }
    return true;
}

bool
FileHeader::taste(const vespalib::string &name, const TuneFileSeqWrite &tuneFileWrite)
{
    TuneFileSeqRead tuneFileRead;
    if (tuneFileWrite.getWantDirectIO())
        tuneFileRead.setWantDirectIO();
    return taste(name, tuneFileRead);
}

bool
FileHeader::taste(const vespalib::string &name, const TuneFileRandRead &tuneFileSearch)
{
    TuneFileSeqRead tuneFileRead;
    if (tuneFileSearch.getWantDirectIO())
        tuneFileRead.setWantDirectIO();
    return taste(name, tuneFileRead);
}

}
