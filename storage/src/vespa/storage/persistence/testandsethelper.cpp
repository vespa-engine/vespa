// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "testandsethelper.h"
#include "persistenceutil.h"
#include "fieldvisitor.h"
#include <vespa/persistence/spi/persistenceprovider.h>
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
        _docSelectionUp = parser.parse(_cmd.getCondition().getSelection());
    } catch (const document::select::ParsingFailedException & e) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Failed to parse test and set condition: "s + e.getMessage()));
    }
}

spi::GetResult TestAndSetHelper::retrieveDocument(const document::FieldSet & fieldSet, spi::Context & context) {
    return _spi.get(_env.getBucket(_docId, _cmd.getBucket()), fieldSet, _cmd.getDocumentId(), context);
}

TestAndSetHelper::TestAndSetHelper(const PersistenceUtil & env, const spi::PersistenceProvider  & spi,
                                   const document::BucketIdFactory & bucketFactory,
                                   const api::TestAndSetCommand & cmd, bool missingDocumentImpliesMatch)
    : _env(env),
      _spi(spi),
      _cmd(cmd),
      _docId(cmd.getDocumentId()),
      _docTypePtr(_cmd.getDocumentType()),
      _missingDocumentImpliesMatch(missingDocumentImpliesMatch)
{
    const auto & repo = _env.getDocumentTypeRepo();
    resolveDocumentType(repo);
    parseDocumentSelection(repo, bucketFactory);
}

TestAndSetHelper::~TestAndSetHelper() = default;

api::ReturnCode
TestAndSetHelper::retrieveAndMatch(spi::Context & context) {
    // Walk document selection tree to build a minimal field set 
    FieldVisitor fieldVisitor(*_docTypePtr);
    _docSelectionUp->visit(fieldVisitor);

    // Retrieve document
    auto result = retrieveDocument(fieldVisitor.getFieldSet(), context);

    // If document exists, match it with selection
    if (result.hasDocument()) {
        auto docPtr = result.getDocumentPtr();
        if (_docSelectionUp->contains(*docPtr) != document::select::Result::True) {
            return api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                                   vespalib::make_string("Condition did not match document nodeIndex=%d bucket=%" PRIx64 " %s",
                                                         _env._nodeIndex, _cmd.getBucketId().getRawId(),
                                                         _cmd.hasBeenRemapped() ? "remapped" : ""));
        }

        // Document matches
        return api::ReturnCode();
    } else if (_missingDocumentImpliesMatch) {
        return api::ReturnCode();
    }

    return api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                           vespalib::make_string("Document does not exist nodeIndex=%d bucket=%" PRIx64 " %s",
                                                 _env._nodeIndex, _cmd.getBucketId().getRawId(),
                                                 _cmd.hasBeenRemapped() ? "remapped" : ""));
}

} // storage
