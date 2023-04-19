// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "testandsethelper.h"
#include "persistenceutil.h"
#include "fieldvisitor.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace std::string_literals;

namespace storage {

void TestAndSetHelper::resolveDocumentType(const document::DocumentTypeRepo & documentTypeRepo) {
    if (_docTypePtr != nullptr) return;
    if (!_docId.hasDocType()) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Document id has no doctype"));
    }

    _docTypePtr = documentTypeRepo.getDocumentType(_docId.getDocType());
    if (_docTypePtr == nullptr) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Document type does not exist"));
    }
}

void TestAndSetHelper::parseDocumentSelection(const document::DocumentTypeRepo & documentTypeRepo,
                                              const document::BucketIdFactory & bucketIdFactory) {
    document::select::Parser parser(documentTypeRepo, bucketIdFactory);

    try {
        _docSelectionUp = parser.parse(_condition.getSelection());
    } catch (const document::select::ParsingFailedException & e) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Failed to parse test and set condition: "s + e.getMessage()));
    }
}

spi::GetResult TestAndSetHelper::retrieveDocument(const document::FieldSet & fieldSet, spi::Context & context) {
    return _spi.get(_env.getBucket(_docId, _bucket), fieldSet, _docId, context);
}

TestAndSetHelper::TestAndSetHelper(const PersistenceUtil& env,
                                   const spi::PersistenceProvider& spi,
                                   const document::BucketIdFactory& bucket_id_factory,
                                   const documentapi::TestAndSetCondition& condition,
                                   document::Bucket bucket,
                                   document::DocumentId doc_id,
                                   const document::DocumentType* doc_type_ptr,
                                   bool missingDocumentImpliesMatch)
    : _env(env),
      _spi(spi),
      _condition(condition),
      _bucket(bucket),
      _docId(std::move(doc_id)),
      _docTypePtr(doc_type_ptr),
      _missingDocumentImpliesMatch(missingDocumentImpliesMatch)
{
    const auto & repo = _env.getDocumentTypeRepo();
    resolveDocumentType(repo);
    parseDocumentSelection(repo, bucket_id_factory);
}

TestAndSetHelper::~TestAndSetHelper() = default;

TestAndSetHelper::Result
TestAndSetHelper::fetch_and_match_raw(spi::Context& context) {
    // Walk document selection tree to build a minimal field set
    FieldVisitor fieldVisitor(*_docTypePtr);
    try {
        _docSelectionUp->visit(fieldVisitor);
    } catch (const document::FieldNotFoundException& e) {
        throw TestAndSetException(api::ReturnCode(
                api::ReturnCode::ILLEGAL_PARAMETERS,
                vespalib::make_string("Condition field '%s' could not be found, or is an imported field. "
                                      "Imported fields are not supported in conditional mutations.",
                                      e.getFieldName().c_str())));
    }
    auto result = retrieveDocument(fieldVisitor.getFieldSet(), context);
    // If document exists, match it with selection
    if (result.hasDocument()) {
        auto docPtr = result.getDocumentPtr();
        if (_docSelectionUp->contains(*docPtr) != document::select::Result::True) {
            return {result.getTimestamp(), Result::ConditionOutcome::IsNotMatch};
        }
        // Document matches
        return {result.getTimestamp(), Result::ConditionOutcome::IsMatch};
    }
    return {result.getTimestamp(), result.is_tombstone() ? Result::ConditionOutcome::IsTombstone
                                                         : Result::ConditionOutcome::DocNotFound};
}

api::ReturnCode
TestAndSetHelper::to_api_return_code(const Result& result) const {
    switch (result.condition_outcome) {
    case Result::ConditionOutcome::IsNotMatch:
        return {api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                vespalib::make_string("Condition did not match document nodeIndex=%d bucket=%" PRIx64,
                                      _env._nodeIndex, _bucket.getBucketId().getRawId())};
    case Result::ConditionOutcome::IsTombstone:
    case Result::ConditionOutcome::DocNotFound:
        if (!_missingDocumentImpliesMatch) {
            return {api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                    vespalib::make_string("Document does not exist nodeIndex=%d bucket=%" PRIx64,
                                          _env._nodeIndex, _bucket.getBucketId().getRawId())};
        }
        [[fallthrough]]; // as match
    case Result::ConditionOutcome::IsMatch:
        return {}; // OK
    }
    abort();
}

api::ReturnCode
TestAndSetHelper::retrieveAndMatch(spi::Context & context) {
    auto result = fetch_and_match_raw(context);
    return to_api_return_code(result);
}

} // storage
