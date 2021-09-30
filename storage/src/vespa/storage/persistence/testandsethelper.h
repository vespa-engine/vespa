// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    TestAndSetException(api::ReturnCode code)
        : std::runtime_error(code.getMessage()),
        _code(std::move(code))
    {}

    const api::ReturnCode & getCode() const { return _code; }
};

class TestAndSetHelper {
    const PersistenceUtil                  &_env;
    const spi::PersistenceProvider         &_spi;
    const api::TestAndSetCommand           &_cmd;
    const document::DocumentId              _docId;
    const document::DocumentType *          _docTypePtr;
    std::unique_ptr<document::select::Node> _docSelectionUp;
    bool                                    _missingDocumentImpliesMatch;

    void resolveDocumentType(const document::DocumentTypeRepo & documentTypeRepo);
    void parseDocumentSelection(const document::DocumentTypeRepo & documentTypeRepo,
                                const document::BucketIdFactory & bucketIdFactory);
    spi::GetResult retrieveDocument(const document::FieldSet & fieldSet, spi::Context & context);

public:
    TestAndSetHelper(const PersistenceUtil & env, const spi::PersistenceProvider & _spi,
                     const document::BucketIdFactory & bucketIdFactory,
                     const api::TestAndSetCommand & cmd, bool missingDocumentImpliesMatch = false);
    ~TestAndSetHelper();
    api::ReturnCode retrieveAndMatch(spi::Context & context);
};

} // storage
