// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fileinfo.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {

namespace memfile {

void
MetaSlot::print(std::ostream & out) const {
    vespalib::asciistream tmp;
    print(tmp);
    out << tmp.str();
}

void
MetaSlot::print(vespalib::asciistream & out) const {
    out << "Slot(" << std::dec << _timestamp << ", " << _gid << ", "
        << _headerPos << " - " << _headerSize << ", " << _bodyPos
        << " - " << _bodySize << ", 0x" << std::hex << _flags << ", 0x"
        << _checksum << ")" << std::dec;
}

std::ostream& operator<<(std::ostream& out, const MetaSlot& slot) {
    vespalib::asciistream tmp;
    slot.print(tmp);
    return out << tmp.str();
}
vespalib::asciistream& operator<<(vespalib::asciistream & out, const MetaSlot& slot) {
    slot.print(out); return out;
}

void
Header::print(std::ostream& out, const std::string& indent) const {
    out << indent << "SlotFileHeader(\n"
        << indent << "  version: " << std::hex << _version << std::dec << "\n"
        << indent << "  meta data list size: " << _metaDataListSize << "\n"
        << indent << "  header block size: " << _headerBlockSize << "b\n"
        << indent << "  checksum: " << std::hex << _checksum
        << indent << (verify() ? " (OK)\n" : " (MISMATCH)\n")
        << indent << "  file checksum: " << _fileChecksum << "\n"
        << indent << ")";
}

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

FileInfo::~FileInfo() { }

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
