// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class BucketInfo
 * @ingroup bucket
 *
 * @brief Contains metadata about a bucket.
 *
 * This class contains metadata about a bucket. It is used to send metadata
 * within storage nodes and to distributors.
 *
 * @version $Id$
 */

#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/vespalib/util/xmlserializable.h>
#include <vespa/vespalib/stllike/string.h>

namespace storage::api {

class BucketInfo
{
    Timestamp _lastModified;
    uint32_t  _checksum;
    uint32_t  _docCount;
    uint32_t  _totDocSize;
    uint32_t  _metaCount;
    uint32_t  _usedFileSize;
    bool      _ready;
    bool      _active;

public:
    BucketInfo() noexcept;
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize) noexcept;
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize,
               uint32_t metaCount, uint32_t usedFileSize) noexcept;
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize,
               uint32_t metaCount, uint32_t usedFileSize,
               bool ready, bool active) noexcept;
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize,
               uint32_t metaCount, uint32_t usedFileSize,
               bool ready, bool active, Timestamp lastModified) noexcept;

    Timestamp getLastModified() const noexcept { return _lastModified; }
    uint32_t getChecksum() const noexcept { return _checksum; }
    uint32_t getDocumentCount() const noexcept { return _docCount; }
    uint32_t getTotalDocumentSize() const noexcept { return _totDocSize; }
    uint32_t getMetaCount() const noexcept { return _metaCount; }
    uint32_t getUsedFileSize() const noexcept { return _usedFileSize; }
    bool isReady() const noexcept { return _ready; }
    bool isActive() const noexcept { return _active; }

    void setChecksum(uint32_t crc) noexcept { _checksum = crc; }
    void setDocumentCount(uint32_t count) noexcept { _docCount = count; }
    void setTotalDocumentSize(uint32_t size) noexcept { _totDocSize = size; }
    void setMetaCount(uint32_t count) noexcept { _metaCount = count; }
    void setUsedFileSize(uint32_t size) noexcept { _usedFileSize = size; }
    void setReady(bool ready = true) noexcept { _ready = ready; }
    void setActive(bool active = true) noexcept { _active = active; }
    void setLastModified(Timestamp lastModified) noexcept { _lastModified = lastModified; }

    /**
     * Only compare checksum, total document count and document
     * size, not meta count or used file size.
     */
    bool equalDocumentInfo(const BucketInfo& other) const noexcept {
        return (_checksum == other._checksum
                && _docCount == other._docCount
                && _totDocSize == other._totDocSize);
    }

    bool operator==(const BucketInfo& info) const noexcept;
    bool valid() const noexcept { return (_docCount > 0 || _totDocSize == 0); }
    bool empty() const noexcept {
        return _metaCount == 0 && _usedFileSize == 0 && _checksum == 0;
    }
    vespalib::string toString() const;
    void printXml(vespalib::XmlOutputStream&) const;
};

std::ostream & operator << (std::ostream & os, const BucketInfo & bucketInfo);

}
