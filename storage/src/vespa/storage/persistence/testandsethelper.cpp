// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include <vespa/storage/persistence/fieldvisitor.h>
#include <vespa/storage/persistence/testandsethelper.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/repo/documenttyperepo.h>

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

spi::GetResult TestAndSetHelper::retrieveDocument(const document::FieldSet & fieldSet) { 
    return _thread._spi.get(
        _thread.getBucket(_docId, _cmd.getBucketId()),
        fieldSet,
        _cmd.getDocumentId(),
        _thread._context);
}

TestAndSetHelper::TestAndSetHelper(PersistenceThread & thread, const api::TestAndSetCommand & cmd)
    : _thread(thread),
      _component(thread._env._component),
      _cmd(cmd),
      _docId(cmd.getDocumentId())
{
    getDocumentType();
    parseDocumentSelection();
}

TestAndSetHelper::~TestAndSetHelper() {
}

api::ReturnCode TestAndSetHelper::retrieveAndMatch() {
    // Walk document selection tree to build a minimal field set 
    FieldVisitor fieldVisitor(*_docTypePtr);
    _docSelectionUp->visit(fieldVisitor);

    // Retrieve document
    auto result = retrieveDocument(fieldVisitor.getFieldSet());

    // If document exists, match it with selection
    if (result.hasDocument()) {
        auto docPtr = result.getDocumentPtr();
        if (_docSelectionUp->contains(*docPtr) != document::select::Result::True) {
            return api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED, "Condition did not match document");
        }

        // Document matches
        return api::ReturnCode();
    }

    return api::ReturnCode(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED, "Document does not exist");
}

} // storage
