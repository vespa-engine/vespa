// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/searchcore/proton/common/dbdocumentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <persistence/spi/types.h>
#include <vespa/searchlib/query/base.h>

namespace proton {

class DocumentOperation : public FeedOperation
{
protected:
    document::BucketId      _bucketId;
    storage::spi::Timestamp _timestamp;
    DbDocumentId            _dbdId;
    DbDocumentId            _prevDbdId;
    bool                    _prevMarkedAsRemoved;
    storage::spi::Timestamp _prevTimestamp;
    mutable uint32_t        _serializedDocSize; // Set by serialize()/deserialize()

    DocumentOperation(Type type);

    DocumentOperation(Type type, const document::BucketId &bucketId,
                      const storage::spi::Timestamp &timestamp);

    void assertValidBucketId(const document::DocumentId &docId) const;
    vespalib::string docArgsToString() const;

public:
    ~DocumentOperation() override {}
    const document::BucketId &getBucketId() const { return _bucketId; }
    storage::spi::Timestamp getTimestamp() const { return _timestamp; }

    search::DocumentIdT getLid() const { return _dbdId.getLid(); }
    search::DocumentIdT getPrevLid() const { return _prevDbdId.getLid(); }
    uint32_t getSubDbId() const { return _dbdId.getSubDbId(); }
    uint32_t getPrevSubDbId() const { return _prevDbdId.getSubDbId(); }
    bool getValidDbdId() const { return _dbdId.valid(); }
    bool getValidDbdId(uint32_t subDbId) const { return _dbdId.valid() && _dbdId.getSubDbId() == subDbId; }
    bool getValidPrevDbdId() const { return _prevDbdId.valid(); }
    bool getValidPrevDbdId(uint32_t subDbId) const { return _prevDbdId.valid() && _prevDbdId.getSubDbId() == subDbId; }
    bool changedDbdId() const { return _dbdId != _prevDbdId; }
    bool getPrevMarkedAsRemoved() const { return _prevMarkedAsRemoved; }
    void setPrevMarkedAsRemoved(bool prevMarkedAsRemoved) { _prevMarkedAsRemoved = prevMarkedAsRemoved; }
    DbDocumentId getDbDocumentId() const;
    DbDocumentId getPrevDbDocumentId() const { return _prevDbdId; }

    void setDbDocumentId(DbDocumentId dbdId) { _dbdId = dbdId; }
    void setPrevDbDocumentId(DbDocumentId prevDbdId) { _prevDbdId = prevDbdId; }

    search::DocumentIdT getNewOrPrevLid(uint32_t subDbId) const {
        if (getValidDbdId() && getSubDbId() == subDbId)
            return getLid();
        if (getValidPrevDbdId() && getPrevSubDbId() == subDbId)
            return getPrevLid();
        return 0;
    }

    bool getValidNewOrPrevDbdId() const {
        return getValidDbdId() || getValidPrevDbdId();
    }

    bool notMovingLidInSameSubDb() const {
        return !getValidDbdId() ||
            !getValidPrevDbdId() ||
            getSubDbId() != getPrevSubDbId() ||
            getLid() == getPrevLid();
    }

    bool movingLidIfInSameSubDb() const {
        return !getValidDbdId() ||
            !getValidPrevDbdId() ||
            getSubDbId() != getPrevSubDbId() ||
            getLid() != getPrevLid();
    }

    storage::spi::Timestamp getPrevTimestamp() const { return _prevTimestamp; }
    void setPrevTimestamp(storage::spi::Timestamp prevTimestamp) { _prevTimestamp = prevTimestamp; }

    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;

    uint32_t getSerializedDocSize() const { return _serializedDocSize; }

    // Provided as a hook for tests.
    void serializeDocumentOperationOnly(vespalib::nbostream &os) const;
};

} // namespace proton
