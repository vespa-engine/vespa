// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fileinfo.h"
#include <sstream>

namespace storage {

namespace memfile {

FileInfo::FileInfo()
    : _metaDataListSize(0),
      _headerBlockSize(0),
      _bodyBlockSize(0)
{ }

FileInfo::FileInfo(uint32_t metaDataListSize,
                   uint32_t headerBlockSize,
                   uint32_t bodyBlockSize)
    : _metaDataListSize(metaDataListSize),
      _headerBlockSize(headerBlockSize),
      _bodyBlockSize(bodyBlockSize)
{ }


FileInfo::FileInfo(const Header& header, size_t fileSize)
    : _metaDataListSize(header._metaDataListSize),
      _headerBlockSize(header._headerBlockSize),
      _bodyBlockSize(
              fileSize - header._headerBlockSize
              - sizeof(MetaSlot) * header._metaDataListSize - sizeof(Header))
{ }

uint32_t
FileInfo::getHeaderBlockStartIndex() const
{
    return sizeof(Header) + _metaDataListSize * sizeof(MetaSlot);
}

uint32_t
FileInfo::getBodyBlockStartIndex() const
{
    return getHeaderBlockStartIndex() + _headerBlockSize;
}

uint32_t
FileInfo::getFileSize() const
{
    return getBodyBlockStartIndex() + _bodyBlockSize;
}

std::string
FileInfo::toString() const
{
    vespalib::asciistream ost;
    ost << "FileInfo("
        << "meta_size " << _metaDataListSize
        << " header_start " << getHeaderBlockStartIndex()
        << " body_start " << getBodyBlockStartIndex()
        << ")";
    return ost.str();
}

}

}
