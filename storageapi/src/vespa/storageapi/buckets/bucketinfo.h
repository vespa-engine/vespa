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
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/util/xmlserializable.h>

namespace storage::api {

class BucketInfo : public vespalib::AsciiPrintable
{
    Timestamp _lastModified;
    uint32_t _checksum;
    uint32_t _docCount;
    uint32_t _totDocSize;
    uint32_t _metaCount;
    uint32_t _usedFileSize;
    bool _ready;
    bool _active;

public:
    BucketInfo();
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize);
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize,
               uint32_t metaCount, uint32_t usedFileSize);
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize,
               uint32_t metaCount, uint32_t usedFileSize,
               bool ready, bool active);
    BucketInfo(uint32_t checksum, uint32_t docCount, uint32_t totDocSize,
               uint32_t metaCount, uint32_t usedFileSize,
               bool ready, bool active, Timestamp lastModified);
    BucketInfo(const BucketInfo &);
    BucketInfo & operator = (const BucketInfo &);
    ~BucketInfo();

    Timestamp getLastModified() const { return _lastModified; }
    uint32_t getChecksum() const { return _checksum; }
    uint32_t getDocumentCount() const { return _docCount; }
    uint32_t getTotalDocumentSize() const { return _totDocSize; }
    uint32_t getMetaCount() const { return _metaCount; }
    uint32_t getUsedFileSize() const { return _usedFileSize; }
    bool isReady() const { return _ready; }
    bool isActive() const { return _active; }

    void setChecksum(uint32_t crc) { _checksum = crc; }
    void setDocumentCount(uint32_t count) { _docCount = count; }
    void setTotalDocumentSize(uint32_t size) { _totDocSize = size; }
    void setMetaCount(uint32_t count) { _metaCount = count; }
    void setUsedFileSize(uint32_t size) { _usedFileSize = size; }
    void setReady(bool ready = true) { _ready = ready; }
    void setActive(bool active = true) { _active = active; }
    void setLastModified(Timestamp lastModified) { _lastModified = lastModified; }

    /**
     * Only compare checksum, total document count and document
     * size, not meta count or used file size.
     */
    bool equalDocumentInfo(const BucketInfo& other) const {
        return (_checksum == other._checksum
                && _docCount == other._docCount
                && _totDocSize == other._totDocSize);
    }

    bool operator==(const BucketInfo& info) const;
    bool valid() const { return (_docCount > 0 || _totDocSize == 0); }
    bool empty() const {
        return _metaCount == 0 && _usedFileSize == 0 && _checksum == 0;
    }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override {
        vespalib::AsciiPrintable::print(out, verbose, indent);
    }
    void print(vespalib::asciistream&, const PrintProperties&) const override;

    void printXml(vespalib::XmlOutputStream&) const;
};

}
