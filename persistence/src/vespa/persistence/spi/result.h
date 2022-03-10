// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketinfo.h"
#include "bucket.h"
#include <vespa/document/bucket/bucketidlist.h>

namespace storage::spi {

class DocEntry;

class Result {
public:
    typedef std::unique_ptr<Result> UP;

    enum class ErrorType {
        NONE,
        TRANSIENT_ERROR,
        PERMANENT_ERROR,
        TIMESTAMP_EXISTS,
        FATAL_ERROR,
        RESOURCE_EXHAUSTED,
        ERROR_COUNT
    };

    /**
     * Constructor to use for a result where there is no error.
     */
    Result() noexcept : _errorCode(ErrorType::NONE), _errorMessage() {}

    /**
     * Constructor to use when an error has been detected.
     */
    Result(ErrorType error, const vespalib::string& errorMessage) noexcept
        : _errorCode(error),
          _errorMessage(errorMessage)
    {}

    Result(const Result &);
    Result(Result&&) noexcept;
    Result & operator = (const Result &);
    Result& operator=(Result&&) noexcept;

    virtual ~Result();

    bool operator==(const Result& o) const {
        return _errorCode == o._errorCode
               && _errorMessage == o._errorMessage;
    }

    bool hasError() const {
        return _errorCode != ErrorType::NONE;
    }

    ErrorType getErrorCode() const {
        return _errorCode;
    }

    const vespalib::string& getErrorMessage() const {
        return _errorMessage;
    }

    vespalib::string toString() const;

private:
    ErrorType _errorCode;
    vespalib::string _errorMessage;
};

std::ostream & operator << (std::ostream & os, const Result & r);

std::ostream & operator << (std::ostream & os, const Result::ErrorType &errorCode);

class BucketInfoResult final : public Result {
public:
    /**
     * Constructor to use for a result where an error has been detected.
     * The service layer will not update the bucket information in this case,
     * so it should not be returned either.
     */
    BucketInfoResult(ErrorType error, const vespalib::string& errorMessage)
        : Result(error, errorMessage) {};

    /**
     * Constructor to use when the write operation was successful,
     * and the bucket info was modified.
     */
    BucketInfoResult(const BucketInfo& info) : _info(info) {}

    const BucketInfo& getBucketInfo() const {
        return _info;
    }

private:
    BucketInfo _info;
};

class UpdateResult final : public Result
{
public:
    /**
     * Constructor to use for a result where an error has been detected.
     * The service layer will not update the bucket information in this case,
     * so it should not be returned either.
     */
    UpdateResult(ErrorType error, const vespalib::string& errorMessage)
        : Result(error, errorMessage),
          _existingTimestamp(0) { }

    /**
     * Constructor to use when no document to update was found.
     */
    UpdateResult()
        : _existingTimestamp(0) { }

    /**
     * Constructor to use when the update was successful.
     */
    UpdateResult(Timestamp existingTimestamp)
        : _existingTimestamp(existingTimestamp) {}

    Timestamp getExistingTimestamp() const { return _existingTimestamp; }

private:
    // Set to 0 if non-existing.
    Timestamp _existingTimestamp;
};

class RemoveResult : public Result
{
public:
    /**
     * Constructor to use for a result where an error has been detected.
     * The service layer will not update the bucket information in this case,
     * so it should not be returned either.
     */
    RemoveResult(ErrorType error, const vespalib::string& errorMessage) noexcept
        : Result(error, errorMessage),
          _numRemoved(0)
    { }

    explicit RemoveResult(bool found) noexcept
            : RemoveResult(found ? 1u : 0u) { }
    explicit RemoveResult(uint32_t numRemoved) noexcept
        : _numRemoved(numRemoved) { }
    bool wasFound() const { return _numRemoved > 0; }
    uint32_t num_removed() const { return _numRemoved; }
    void inc_num_removed(uint32_t add) { _numRemoved += add; }

private:
    uint32_t _numRemoved;
};

class GetResult final : public Result {
public:
    /**
     * Constructor to use when there was an error retrieving the document.
     * Not finding the document is not an error in this context.
     */
    GetResult(ErrorType error, const vespalib::string& errorMessage)
        : Result(error, errorMessage),
          _timestamp(0),
          _is_tombstone(false)
    {
    }

    /**
     * Constructor to use when we didn't find the document in question.
     */
    GetResult()
        : _timestamp(0),
          _doc(),
          _is_tombstone(false)
    {
    }
    GetResult(GetResult &&) noexcept = default;
    GetResult & operator=(GetResult &&) noexcept = default;

    /**
     * Constructor to use when we found the document asked for.
     *
     * @param doc The document we found
     * @param timestamp The timestamp with which the document was stored.
     */
    GetResult(DocumentUP doc, Timestamp timestamp);

    static GetResult make_for_tombstone(Timestamp removed_at_ts) {
        return GetResult(removed_at_ts, true);
    }

    static GetResult make_for_metadata_only(Timestamp removed_at_ts) {
        return GetResult(removed_at_ts, false);
    }

    ~GetResult() override;

    [[nodiscard]] Timestamp getTimestamp() const {
        return _timestamp;
    }

    [[nodiscard]] bool hasDocument() const {
        return (_doc.get() != nullptr);
    }

    [[nodiscard]] bool is_tombstone() const noexcept {
        return _is_tombstone;
    }

    const Document& getDocument() const {
        return *_doc;
    }

    Document& getDocument() {
        return *_doc;
    }

    const DocumentSP & getDocumentPtr() {
        return _doc;
    }

private:
    // Explicitly creates a metadata only GetResult with no document, optionally a tombstone (remove entry).
    GetResult(Timestamp removed_at_ts, bool is_tombstone);

    Timestamp  _timestamp;
    DocumentSP _doc;
    bool       _is_tombstone;
};

class BucketIdListResult final : public Result {
public:
    using List = document::bucket::BucketIdList;

    /**
     * Constructor used when there was an error listing the buckets.
     */
    BucketIdListResult(ErrorType error, const vespalib::string& errorMessage)
        : Result(error, errorMessage) {}

    /**
     * Constructor used when the bucket listing was successful.
     *
     * @param list The list of bucket ids this partition has. Is swapped with
     * the list internal to this object.
     */
    BucketIdListResult(List list)
        : Result(),
          _info(std::move(list))
    { }
    BucketIdListResult()
        : Result(),
          _info()
    { }
    BucketIdListResult(BucketIdListResult &&) noexcept = default;
    BucketIdListResult & operator =(BucketIdListResult &&) noexcept = default;
    ~BucketIdListResult();

    const List& getList() const { return _info; }
    List& getList() { return _info; }

private:
    List _info;
};

class CreateIteratorResult : public Result {
public:
    /**
     * Constructor used when there was an error creating the iterator.
     */
    CreateIteratorResult(ErrorType error, const vespalib::string& errorMessage) noexcept
        : Result(error, errorMessage),
          _iterator(0) { }

    /**
     * Constructor used when the iterator state was successfully created.
     */
    CreateIteratorResult(const IteratorId& id) noexcept
        : _iterator(id)
    { }

    const IteratorId& getIteratorId() const { return _iterator; }

private:
    IteratorId _iterator;
};

class IterateResult final : public Result {
public:
    using List = std::vector<std::unique_ptr<DocEntry>>;

    /**
     * Constructor used when there was an error creating the iterator.
     */
    IterateResult(ErrorType error, const vespalib::string& errorMessage);

    /**
     * Constructor used when the iteration was successful.
     * For performance concerns, the entries in the input vector
     * are swapped with the internal vector.
     *
     * @param completed Set to true if iteration has been completed.
     */
    IterateResult(List entries, bool completed);

    IterateResult(const IterateResult &) = delete;
    IterateResult(IterateResult &&rhs) noexcept;
    IterateResult &operator=(IterateResult &&rhs) noexcept;

    ~IterateResult();

    const List& getEntries() const { return _entries; }
    List steal_entries();
    bool isCompleted() const { return _completed; }

private:
    bool _completed;
    List _entries;
};

}

