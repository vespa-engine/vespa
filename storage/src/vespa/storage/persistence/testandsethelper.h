// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#pragma once

#include <vespa/storageapi/message/persistence.h>
#include <vespa/persistence/spi/result.h>
#include <stdexcept>

namespace document::select { class Node; }
namespace document {
    class FieldSet;
    class BucketIdFactory;
}

namespace storage {

namespace spi {
    class Context;
    struct PersistenceProvider;
}
class PersistenceThread;
class ServiceLayerComponent;
class PersistenceUtil;

class TestAndSetException : public std::runtime_error {
    api::ReturnCode _code;
public:
    explicit TestAndSetException(api::ReturnCode code)
        : std::runtime_error(code.getMessage()),
        _code(std::move(code))
    {}

    const api::ReturnCode & getCode() const { return _code; }
};

class TestAndSetHelper {
    const PersistenceUtil&                  _env;
    const spi::PersistenceProvider&         _spi;
    const documentapi::TestAndSetCondition& _condition;
    const document::Bucket                  _bucket;
    const document::DocumentId              _docId;
    const document::DocumentType*           _docTypePtr;
    std::unique_ptr<document::select::Node> _docSelectionUp;
    bool                                    _missingDocumentImpliesMatch;

    void resolveDocumentType(const document::DocumentTypeRepo & documentTypeRepo);
    void parseDocumentSelection(const document::DocumentTypeRepo & documentTypeRepo,
                                const document::BucketIdFactory & bucketIdFactory);
    spi::GetResult retrieveDocument(const document::FieldSet & fieldSet, spi::Context & context);

public:
    struct Result {
        enum class ConditionOutcome {
            DocNotFound,
            IsMatch,
            IsNotMatch,
            IsTombstone
        };

        api::Timestamp timestamp = 0;
        ConditionOutcome condition_outcome = ConditionOutcome::IsNotMatch;

        [[nodiscard]] bool doc_not_found() const noexcept {
            return condition_outcome == ConditionOutcome::DocNotFound;
        }
        [[nodiscard]] bool is_match() const noexcept {
            return condition_outcome == ConditionOutcome::IsMatch;
        }
        [[nodiscard]] bool is_not_match() const noexcept {
            return condition_outcome == ConditionOutcome::IsNotMatch;
        }
        [[nodiscard]] bool is_tombstone() const noexcept {
            return condition_outcome == ConditionOutcome::IsTombstone;
        }
    };

    TestAndSetHelper(const PersistenceUtil& env,
                     const spi::PersistenceProvider& _spi,
                     const document::BucketIdFactory& bucket_id_factory,
                     const documentapi::TestAndSetCondition& condition,
                     document::Bucket bucket,
                     document::DocumentId doc_id,
                     const document::DocumentType* doc_type_ptr,
                     bool missingDocumentImpliesMatch = false);
    ~TestAndSetHelper();

    Result fetch_and_match_raw(spi::Context& context);
    api::ReturnCode to_api_return_code(const Result& result) const;

    api::ReturnCode retrieveAndMatch(spi::Context & context);
};

} // storage
