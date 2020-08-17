// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include <vespa/storage/persistence/fieldvisitor.h>
#include <vespa/storage/persistence/testandsethelper.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace std::string_literals;

namespace storage {

void TestAndSetHelper::getDocumentType() {
    if (!_docId.hasDocType()) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Document id has no doctype"));
    }

    _docTypePtr = _component.getTypeRepo()->getDocumentType(_docId.getDocType());
    if (_docTypePtr == nullptr) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Document type does not exist"));
    }
}

void TestAndSetHelper::parseDocumentSelection() {
    document::select::Parser parser(*_component.getTypeRepo(), _component.getBucketIdFactory());

    try {
        _docSelectionUp = parser.parse(_cmd.getCondition().getSelection());
    } catch (const document::select::ParsingFailedException & e) {
        throw TestAndSetException(api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS, "Failed to parse test and set condition: "s + e.getMessage()));
    }
}

spi::GetResult TestAndSetHelper::retrieveDocument(const document::FieldSet & fieldSet, spi::Context & context) {
    return _thread._spi.get(
        _thread.getBucket(_docId, _cmd.getBucket()),
        fieldSet,
        _cmd.getDocumentId(),
        context);
}

TestAndSetHelper::TestAndSetHelper(PersistenceThread & thread, const api::TestAndSetCommand & cmd,
                                   bool missingDocumentImpliesMatch)
    : _thread(thread),
      _component(thread._env._component),
      _cmd(cmd),
      _docId(cmd.getDocumentId()),
      _docTypePtr(nullptr),
      _missingDocumentImpliesMatch(missingDocumentImpliesMatch)
{
    getDocumentType();
    parseDocumentSelection();
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
                                   vespalib::make_string("Condition did not match document partition=%d, nodeIndex=%d bucket=%" PRIx64 " %s",
                                                         _thread._env._partition, _thread._env._nodeIndex, _cmd.getBucketId().getRawId(),
                                                         _cmd.hasBeenRemapped() ? "remapped" : ""));
        }

        // Document matches
        return api::ReturnCode();
    } else if (_missingDocumentImpliesMatch) {
        return api::ReturnCode();
    }

    return api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED,
                           vespalib::make_string("Document does not exist partition=%d, nodeIndex=%d bucket=%" PRIx64 " %s",
                                                 _thread._env._partition, _thread._env._nodeIndex, _cmd.getBucketId().getRawId(),
                                                 _cmd.hasBeenRemapped() ? "remapped" : ""));
}

} // storage
