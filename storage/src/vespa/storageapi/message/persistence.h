// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file persistence.h
 *
 * Persistence related commands, like put, get & remove
 */
#pragma once

#include <vespa/storageapi/messageapi/bucketinforeply.h>
#include <vespa/storageapi/defs.h>
#include <vespa/document/base/documentid.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>

namespace document {
    class DocumentUpdate;
    class Document;
}
namespace storage::api {

using documentapi::TestAndSetCondition;
using DocumentSP = std::shared_ptr<document::Document>;

class TestAndSetCommand : public BucketInfoCommand {
    TestAndSetCondition _condition;
public:
    TestAndSetCommand(const MessageType & messageType, const document::Bucket &bucket);
    ~TestAndSetCommand() override;

    void setCondition(const TestAndSetCondition & condition) { _condition = condition; }
    void clear_condition() { _condition = TestAndSetCondition(); }
    const TestAndSetCondition & getCondition() const { return _condition; }
    bool hasTestAndSetCondition() const noexcept override { return _condition.isPresent(); }

    /**
     * Uniform interface to get document id
     * Used by test and set to retrieve already existing document
     */
    virtual const document::DocumentId & getDocumentId() const = 0;
    virtual const document::DocumentType * getDocumentType() const { return nullptr; }
};

/**
 * @class PutCommand
 * @ingroup message
 *
 * @brief Command for adding a document to the storage system.
 */
class PutCommand : public TestAndSetCommand {
    DocumentSP _doc;
    Timestamp  _timestamp;
    Timestamp  _updateTimestamp;
    bool _create_if_non_existent = false;
public:
    PutCommand(const document::Bucket &bucket, const DocumentSP&, Timestamp);
    ~PutCommand() override;

    void setTimestamp(Timestamp ts) { _timestamp = ts; }

    /**
     * If set, this PUT will only update the header of an existing document,
     * rather than writing an entire new PUT. It will only perform the write if
     * there exists a document already with the given timestamp.
     */
    void setUpdateTimestamp(Timestamp ts) { _updateTimestamp = ts; }
    Timestamp getUpdateTimestamp() const { return _updateTimestamp; }

    const DocumentSP& getDocument() const { return _doc; }
    const document::DocumentId& getDocumentId() const override;
    Timestamp getTimestamp() const { return _timestamp; }
    const document::DocumentType * getDocumentType() const override;
    void set_create_if_non_existent(bool value) noexcept { _create_if_non_existent = value; }
    bool get_create_if_non_existent() const noexcept { return _create_if_non_existent; }

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
    DocumentSP _document; // Not serialized
    Timestamp _timestamp;
    Timestamp _updateTimestamp;
    bool _wasFound;

public:
    explicit PutReply(const PutCommand& cmd, bool wasFound = true);
    ~PutReply() override;

    const document::DocumentId& getDocumentId() const { return _docId; }
    bool hasDocument() const { return _document.get(); }
    const DocumentSP& getDocument() const { return _document; }
    Timestamp getTimestamp() const { return _timestamp; };
    Timestamp getUpdateTimestamp() const { return _updateTimestamp; }

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
    std::shared_ptr<document::DocumentUpdate> _update;
    Timestamp _timestamp;
    Timestamp _oldTimestamp;

public:
    UpdateCommand(const document::Bucket &bucket,
                  const std::shared_ptr<document::DocumentUpdate>&, Timestamp);
    ~UpdateCommand() override;

    void setTimestamp(Timestamp ts) { _timestamp = ts; }
    void setOldTimestamp(Timestamp ts) { _oldTimestamp = ts; }

    const std::shared_ptr<document::DocumentUpdate>& getUpdate() const { return _update; }
    const document::DocumentId& getDocumentId() const override;
    Timestamp getTimestamp() const { return _timestamp; }
    Timestamp getOldTimestamp() const { return _oldTimestamp; }

    const document::DocumentType * getDocumentType() const override;
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
    ~UpdateReply() override;

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
    document::DocumentId    _docId;
    Timestamp               _beforeTimestamp;
    vespalib::string        _fieldSet;
    TestAndSetCondition     _condition;
    InternalReadConsistency _internal_read_consistency;
public:
    GetCommand(const document::Bucket &bucket, const document::DocumentId&,
               vespalib::stringref fieldSet, Timestamp before = MAX_TIMESTAMP);
    ~GetCommand() override;
    void setBeforeTimestamp(Timestamp ts) { _beforeTimestamp = ts; }
    const document::DocumentId& getDocumentId() const { return _docId; }
    Timestamp getBeforeTimestamp() const { return _beforeTimestamp; }
    const vespalib::string& getFieldSet() const { return _fieldSet; }
    void setFieldSet(vespalib::stringref fieldSet) { _fieldSet = fieldSet; }
    [[nodiscard]] bool has_condition() const noexcept { return _condition.isPresent(); }
    [[nodiscard]] const TestAndSetCondition& condition() const noexcept { return _condition; }
    void set_condition(TestAndSetCondition cond) { _condition = std::move(cond); }
    InternalReadConsistency internal_read_consistency() const noexcept {
        return _internal_read_consistency;
    }
    void set_internal_read_consistency(InternalReadConsistency consistency) noexcept {
        _internal_read_consistency = consistency;
    }

    vespalib::string getSummary() const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    api::LockingRequirements lockingRequirements() const noexcept override {
        return api::LockingRequirements::Shared;
    }

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
    DocumentSP _doc; // Null pointer if not found
    Timestamp _beforeTimestamp;
    Timestamp _lastModifiedTime;
    bool _had_consistent_replicas;
    bool _is_tombstone;
    bool _condition_matched;
public:
    explicit GetReply(const GetCommand& cmd,
                      const DocumentSP& doc = DocumentSP(),
                      Timestamp lastModified = 0,
                      bool had_consistent_replicas = false,
                      bool is_tombstone = false,
                      bool condition_matched = false);

    ~GetReply() override;

    const DocumentSP& getDocument() const { return _doc; }
    const document::DocumentId& getDocumentId() const { return _docId; }
    const vespalib::string& getFieldSet() const { return _fieldSet; }

    Timestamp getLastModifiedTimestamp() const noexcept { return _lastModifiedTime; }
    Timestamp getBeforeTimestamp() const noexcept { return _beforeTimestamp; }

    [[nodiscard]] bool had_consistent_replicas() const noexcept { return _had_consistent_replicas; }
    [[nodiscard]] bool is_tombstone() const noexcept { return _is_tombstone; }
    [[nodiscard]] bool condition_matched() const noexcept { return _condition_matched; }

    bool wasFound() const { return (_doc.get() != nullptr); }
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
    ~RemoveCommand() override;

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
    ~RemoveReply() override;

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
    ~RevertCommand() override;
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
    ~RevertReply() override;
    const std::vector<Timestamp>& getRevertTokens() const { return _tokens; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(RevertReply, onRevertReply)
};

}
