// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#pragma once
#include <vespa/storage/persistence/persistencethread.h>

namespace storage {

class TestAndSetException : public std::runtime_error {
    api::ReturnCode _code;

public:
    TestAndSetException(api::ReturnCode code)
        : std::runtime_error(code.getMessage()),
        _code(std::move(code))
    {}

    const api::ReturnCode & getCode() const { return _code; }
};

class TestAndSetHelper {
    PersistenceThread & _thread;
    ServiceLayerComponent & _component;
    const api::TestAndSetCommand & _cmd;

    const document::DocumentId _docId;
    const document::DocumentType * _docTypePtr;
    std::unique_ptr<document::select::Node> _docSelectionUp;

    void getDocumentType();
    void parseDocumentSelection();
    spi::GetResult retrieveDocument(const document::FieldSet & fieldSet);

public:
    TestAndSetHelper(PersistenceThread & thread, const api::TestAndSetCommand & cmd);
    ~TestAndSetHelper();
    api::ReturnCode retrieveAndMatch();
};

} // storage
