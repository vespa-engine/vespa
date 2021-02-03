// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketinfo.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <ostream>

namespace storage::api {

static_assert(sizeof(BucketInfo) == 32, "BucketInfo should be 32 bytes");

BucketInfo::BucketInfo() noexcept
    : _lastModified(0),
      _checksum(0),
      _docCount(0),
      _totDocSize(1),
      _metaCount(0),
      _usedFileSize(1),
      _ready(false),
      _active(false)
{}

BucketInfo::BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize) noexcept
    : _lastModified(0),
      _checksum(checksum),
      _docCount(docCount),
      _totDocSize(totDocSize),
      _metaCount(docCount),
      _usedFileSize(totDocSize),
      _ready(false),
      _active(false)
{}

BucketInfo::BucketInfo(uint32_t checksum, uint32_t docCount,
                       uint32_t totDocSize, uint32_t metaCount,
                       uint32_t usedFileSize) noexcept
    : _lastModified(0),
      _checksum(checksum),
      _docCount(docCount),
      _totDocSize(totDocSize),
      _metaCount(metaCount),
      _usedFileSize(usedFileSize),
      _ready(false),
      _active(false)
{}

BucketInfo::BucketInfo(uint32_t checksum, uint32_t docCount,
                       uint32_t totDocSize, uint32_t metaCount,
                       uint32_t usedFileSize,
                       bool ready, bool active) noexcept
    : _lastModified(0),
      _checksum(checksum),
      _docCount(docCount),
      _totDocSize(totDocSize),
      _metaCount(metaCount),
      _usedFileSize(usedFileSize),
      _ready(ready),
      _active(active)
{}

BucketInfo::BucketInfo(uint32_t checksum, uint32_t docCount,
                       uint32_t totDocSize, uint32_t metaCount,
                       uint32_t usedFileSize,
                       bool ready, bool active, Timestamp lastModified) noexcept
    : _lastModified(lastModified),
      _checksum(checksum),
      _docCount(docCount),
      _totDocSize(totDocSize),
      _metaCount(metaCount),
      _usedFileSize(usedFileSize),
      _ready(ready),
      _active(active)
{}

bool
BucketInfo::operator==(const BucketInfo& info) const noexcept
{
    return (_checksum == info._checksum &&
            _docCount == info._docCount &&
            _totDocSize == info._totDocSize &&
            _metaCount == info._metaCount &&
            _usedFileSize == info._usedFileSize &&
            _ready == info._ready &&
            _active == info._active);
}

// TODO: add ready/active to printing
vespalib::string
BucketInfo::toString() const
{
    vespalib::asciistream out;
    out << "BucketInfo(";
    if (valid()) {
        out << "crc 0x" << vespalib::hex << _checksum << vespalib::dec
            << ", docCount " << _docCount
            << ", totDocSize " << _totDocSize;
        if (_totDocSize != _usedFileSize) {
            out << ", metaCount " << _metaCount
                << ", usedFileSize " << _usedFileSize;
        }
        out << ", ready " << (_ready ? "true" : "false")
            << ", active " << (_active ? "true" : "false");

        if (_lastModified != 0) {
            out << ", last modified " << _lastModified;
        }
    } else {
        out << "invalid";
    }
    out << ")";
    return out.str();
}

void
BucketInfo::printXml(vespalib::XmlOutputStream& xos) const
{
    using namespace vespalib::xml;
    xos << XmlAttribute("checksum", _checksum, XmlAttribute::HEX)
        << XmlAttribute("docs", _docCount)
        << XmlAttribute("size", _totDocSize)
        << XmlAttribute("metacount", _metaCount)
        << XmlAttribute("usedfilesize", _usedFileSize)
        << XmlAttribute("ready", _ready)
        << XmlAttribute("active", _active)
        << XmlAttribute("lastmodified", _lastModified);
}

std::ostream &
operator << (std::ostream & os, const BucketInfo & bucketInfo) {
    return os << bucketInfo.toString();
}

}
