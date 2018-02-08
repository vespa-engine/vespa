// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::ReturnCode
 * @ingroup messageapi
 *
 * @brief Class for representing return values from the processing chain
 *
 * @version $Id$
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/document/util/serializable.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <iosfwd>

namespace document {
    class ByteBuffer;
}

namespace storage {
namespace api {

class ReturnCode : public document::Deserializable,
                   public vespalib::Printable {
public:
    typedef documentapi::DocumentProtocol Protocol;

    /** Return status codes */
    enum Result {
        OK                            = mbus::ErrorCode::NONE,
        ENCODE_ERROR                  = mbus::ErrorCode::ENCODE_ERROR,

        EXISTS                        = Protocol::ERROR_EXISTS,

        NOT_READY                     = Protocol::ERROR_NODE_NOT_READY,
        WRONG_DISTRIBUTION            = Protocol::ERROR_WRONG_DISTRIBUTION,
        REJECTED                      = Protocol::ERROR_REJECTED,
        ABORTED                       = Protocol::ERROR_ABORTED,
        BUCKET_NOT_FOUND              = Protocol::ERROR_BUCKET_NOT_FOUND,
        BUCKET_DELETED                = Protocol::ERROR_BUCKET_DELETED,
        TIMESTAMP_EXIST               = Protocol::ERROR_TIMESTAMP_EXIST,
        STALE_TIMESTAMP               = Protocol::ERROR_STALE_TIMESTAMP,
        TEST_AND_SET_CONDITION_FAILED = Protocol::ERROR_TEST_AND_SET_CONDITION_FAILED,

        // Wrong use
        UNKNOWN_COMMAND               = Protocol::ERROR_UNKNOWN_COMMAND,
        NOT_IMPLEMENTED               = Protocol::ERROR_NOT_IMPLEMENTED,
        ILLEGAL_PARAMETERS            = Protocol::ERROR_ILLEGAL_PARAMETERS,
        IGNORED                       = Protocol::ERROR_IGNORED,
        UNPARSEABLE                   = Protocol::ERROR_UNPARSEABLE,

        // Network failure
        NOT_CONNECTED                 = Protocol::ERROR_NOT_CONNECTED,
        TIMEOUT                       = mbus::ErrorCode::TIMEOUT,
        BUSY                          = Protocol::ERROR_BUSY,

        // Disk operations
        NO_SPACE                      = Protocol::ERROR_NO_SPACE,
        DISK_FAILURE                  = Protocol::ERROR_DISK_FAILURE,
        IO_FAILURE                    = Protocol::ERROR_IO_FAILURE,

        // Don't know what happened (catch-all)
        INTERNAL_FAILURE              = Protocol::ERROR_INTERNAL_FAILURE
    };

private:
    Result _result;
    vespalib::string _message;
    void onDeserialize(const document::DocumentTypeRepo &repo, document::ByteBuffer& buffer) override;
    void onSerialize(document::ByteBuffer& buffer) const override;

public:
    ReturnCode();
    explicit ReturnCode(Result result, const vespalib::stringref & msg = "");
    ReturnCode(const document::DocumentTypeRepo &repo,
               document::ByteBuffer& buffer);
    ReturnCode(const ReturnCode &);
    ReturnCode & operator = (const ReturnCode &);
    ReturnCode(ReturnCode &&) = default;
    ReturnCode & operator = (ReturnCode &&);
    ~ReturnCode();

    ReturnCode* clone() const override { return new ReturnCode(*this); }

    size_t getSerializedSize() const override;

    const vespalib::string& getMessage() const { return _message; }
    void setMessage(const vespalib::stringref & message) { _message = message; }

    Result getResult() const { return _result; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
     * Translate from status code to human-readable string
     * @param result Status code returned from getResult()
     */
    static vespalib::string getResultString(Result result);

    bool failed() const { return (_result != OK); }
    bool success() const { return (_result == OK); }

    bool operator==(Result res) const { return _result == res; }
    bool operator!=(Result res) const { return _result != res; }
    bool operator==(const ReturnCode& code) const
        { return _result == code._result && _message == code._message; }
    bool operator!=(const ReturnCode& code) const
        { return _result != code._result || _message != code._message; }

    // To avoid lots of code matching various return codes in storage, we define
    // some functions they can use to match those codes that corresponds to what
    // they want to match.

    bool isBusy() const;
    bool isNodeDownOrNetwork() const;
    bool isCriticalForMaintenance() const;
    bool isCriticalForVisitor() const;
    bool isCriticalForVisitorDispatcher() const;
    bool isShutdownRelated() const;
    bool isBucketDisappearance() const;
    bool isNonCriticalForIntegrityChecker() const;
};


} // api
} // storage
