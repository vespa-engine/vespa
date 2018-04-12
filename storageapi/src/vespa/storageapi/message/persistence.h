// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file persistence.h
 *
 * Persistence related commands, like put, get & remove
 */
#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>
#include <vespa/storageapi/defs.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>

namespace storage {
namespace api {

using documentapi::TestAndSetCondition;

class TestAndSetCommand : public BucketInfoCommand {
    TestAndSetCondition _condition;

public:
    TestAndSetCommand(const MessageType & messageType, const document::Bucket &bucket);
    ~TestAndSetCommand();

    void setCondition(const TestAndSetCondition & condition) { _condition = condition; }
    const TestAndSetCondition & getCondition() const { return _condition; }

    /**
     * Uniform interface to get document id
     * Used by test and set to retrieve already existing document
     */
    virtual const document::DocumentId & getDocumentId() const = 0;
};

/**
 * @class PutCommand
 * @ingroup message
 *
 * @brief Command for adding a document to the storage system.
 */
class PutCommand : public TestAndSetCommand {
    document::Document::SP _doc;
    Timestamp _timestamp;
    Timestamp _updateTimestamp;

public:
    PutCommand(const document::Bucket &bucket, const document::Document::SP&, Timestamp);
    ~PutCommand();

    void setTimestamp(Timestamp ts) { _timestamp = ts; }

    /**
     * If set, this PUT will only update the header of an existing document,
     * rather than writing an entire new PUT. It will only perform the write if
     * there exists a document already with the given timestamp.
     */
    void setUpdateTimestamp(Timestamp ts) { _updateTimestamp = ts; }
    Timestamp getUpdateTimestamp() const { return _updateTimestamp; }

    const document::Document::SP& getDocument() const { return _doc; }
    const document::DocumentId& getDocumentId() const override { return _doc->getId(); }
    Timestamp getTimestamp() const { return _timestamp; }

    vespalib::string getSummary() const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(PutCommand, onPut);
};

/**
 * @class PutReply
 * @ingroup message
 *
 * @brief Reply of a put command.
 */
class PutReply : public BucketInfoReply {
    document::DocumentId _docId;
    document::Document::SP _document; // Not serialized
    Timestamp _timestamp;
    Timestamp _updateTimestamp;
    bool _wasFound;

public:
    explicit PutReply(const PutCommand& cmd, bool wasFound = true);
    ~PutReply();

    const document::DocumentId& getDocumentId() const { return _docId; }
    bool hasDocument() const { return _document.get(); }
    const document::Document::SP& getDocument() const { return _document; }
    Timestamp getTimestamp() const { return _timestamp; };
    Timestamp getUpdateTimestamp() const { return _updateTimestamp; }

    bool isHeadersOnlyPut() const { return (_updateTimestamp != 0); }
    bool wasFound() const { return _wasFound; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(PutReply, onPutReply)
};

/**
 * @class UpdateCommand
 * @ingroup message
 *
 * @brief Command for updating a document to the storage system.
 */
class UpdateCommand : public TestAndSetCommand {
    document::DocumentUpdate::SP _update;
    Timestamp _timestamp;
    Timestamp _oldTimestamp;

public:
    UpdateCommand(const document::Bucket &bucket,
                  const document::DocumentUpdate::SP&, Timestamp);
    ~UpdateCommand();

    void setTimestamp(Timestamp ts) { _timestamp = ts; }
    void setOldTimestamp(Timestamp ts) { _oldTimestamp = ts; }

    const document::DocumentUpdate::SP& getUpdate() const { return _update; }
    const document::DocumentId& getDocumentId() const override
        { return _update->getId(); }
    Timestamp getTimestamp() const { return _timestamp; }
    Timestamp getOldTimestamp() const { return _oldTimestamp; }

    vespalib::string getSummary() const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(UpdateCommand, onUpdate);
};

/**
 * @class UpdateReply
 * @ingroup message
 *
 * @brief Reply of a update command.
 */
class UpdateReply : public BucketInfoReply {
    document::DocumentId _docId;
    Timestamp _timestamp;
    Timestamp _oldTimestamp;
    uint16_t _consistentNode;

public:
    UpdateReply(const UpdateCommand& cmd, Timestamp oldTimestamp = 0);
    ~UpdateReply();

    void setOldTimestamp(Timestamp ts) { _oldTimestamp = ts; }

    const document::DocumentId& getDocumentId() const { return _docId; }
    Timestamp getTimestamp() const { return _timestamp; }
    Timestamp getOldTimestamp() const { return _oldTimestamp; }

    bool wasFound() const { return (_oldTimestamp != 0); }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
     * If this update was inconsistent (multiple different timestamps returned),
     * set the "best" node.
     */
    void setNodeWithNewestTimestamp(uint16_t node) { _consistentNode = node; }

    uint16_t getNodeWithNewestTimestamp() { return _consistentNode; }

    DECLARE_STORAGEREPLY(UpdateReply, onUpdateReply)
};


/**
 * @class GetCommand
 * @ingroup message
 *
 * @brief Command for returning a single document.
 *
 * Normally, the newest version of a document is retrieved. The timestamp can
 * be used to retrieve the newest copy, which is not newer than the given
 * timestamp.
 */
class GetCommand : public BucketInfoCommand {
    document::DocumentId _docId;
    Timestamp _beforeTimestamp;
    vespalib::string _fieldSet;

public:
    GetCommand(const document::Bucket &bucket, const document::DocumentId&,
               const vespalib::stringref & fieldSet, Timestamp before = MAX_TIMESTAMP);
    ~GetCommand();
    void setBeforeTimestamp(Timestamp ts) { _beforeTimestamp = ts; }
    const document::DocumentId& getDocumentId() const { return _docId; }
    Timestamp getBeforeTimestamp() const { return _beforeTimestamp; }
    const vespalib::string& getFieldSet() const { return _fieldSet; }
    void setFieldSet(const vespalib::stringref & fieldSet) { _fieldSet = fieldSet; }

    vespalib::string getSummary() const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(GetCommand, onGet)
};

/**
 * @class GetReply
 * @ingroup message
 *
 * @brief Reply for a get command.
 */
class GetReply : public BucketInfoReply {
    document::DocumentId _docId; // In case of not found, we want id still
    vespalib::string _fieldSet;
    document::Document::SP _doc; // Null pointer if not found
    Timestamp _beforeTimestamp;
    Timestamp _lastModifiedTime;

public:
    GetReply(const GetCommand& cmd,
             const document::Document::SP& doc = document::Document::SP(),
             Timestamp lastModified = 0);
    ~GetReply();

    const document::Document::SP& getDocument() const { return _doc; }
    const document::DocumentId& getDocumentId() const { return _docId; }
    const vespalib::string& getFieldSet() const { return _fieldSet; }

    Timestamp getLastModifiedTimestamp() const { return _lastModifiedTime; }
    Timestamp getBeforeTimestamp() const { return _beforeTimestamp; }

    bool wasFound() const { return (_doc.get() != 0); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(GetReply, onGetReply)
};

/**
 * @class RemoveCommand
 * @ingroup message
 *
 * @brief Command for removing a document.
 */
class RemoveCommand : public TestAndSetCommand {
    document::DocumentId _docId;
    Timestamp _timestamp;

public:
    RemoveCommand(const document::Bucket &bucket, const document::DocumentId& docId, Timestamp timestamp);
    ~RemoveCommand();

    void setTimestamp(Timestamp ts) { _timestamp = ts; }
    const document::DocumentId& getDocumentId() const override { return _docId; }
    Timestamp getTimestamp() const { return _timestamp; }
    vespalib::string getSummary() const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(RemoveCommand, onRemove)
};

/**
 * @class RemoveReply
 * @ingroup message
 *
 * @brief Reply for a remove command.
 */
class RemoveReply : public BucketInfoReply {
    document::DocumentId _docId;
    Timestamp _timestamp;
    Timestamp _oldTimestamp;
public:
    explicit RemoveReply(const RemoveCommand& cmd, Timestamp oldTimestamp = 0);
    ~RemoveReply();

    const document::DocumentId& getDocumentId() const { return _docId; }
    Timestamp getTimestamp() { return _timestamp; };
    Timestamp getOldTimestamp() const { return _oldTimestamp; }
    void setOldTimestamp(Timestamp oldTimestamp) { _oldTimestamp = oldTimestamp; }
    bool wasFound() const { return (_oldTimestamp != 0); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(RemoveReply, onRemoveReply)
};

/**
 * @class RevertCommand
 * @ingroup message
 *
 * @brief Command for reverting a write or remove operation.
 */
class RevertCommand : public BucketInfoCommand {
    std::vector<Timestamp> _tokens;
public:
    RevertCommand(const document::Bucket &bucket,
                  const std::vector<Timestamp>& revertTokens);
    ~RevertCommand();
    const std::vector<Timestamp>& getRevertTokens() const { return _tokens; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(RevertCommand, onRevert)
};

/**
 * @class RevertReply
 * @ingroup message
 *
 * @brief Reply for a revert command.
 */
class RevertReply : public BucketInfoReply {
    std::vector<Timestamp> _tokens;
public:
    explicit RevertReply(const RevertCommand& cmd);
    ~RevertReply();
    const std::vector<Timestamp>& getRevertTokens() const { return _tokens; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(RevertReply, onRevertReply)
};

} // api
} // storage
