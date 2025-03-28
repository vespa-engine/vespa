// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_reference_registry.h"
#include <mutex>
#include <condition_variable>
#include <map>

namespace proton {

/*
 * Class implementing a registry of named IDocumentDBReferences.
 */
class DocumentDBReferenceRegistry : public IDocumentDBReferenceRegistry
{
    mutable std::mutex _lock;
    mutable std::condition_variable _cv;
    std::map<std::string, std::shared_ptr<IDocumentDBReference>> _handlers;
public:
    DocumentDBReferenceRegistry();
    virtual ~DocumentDBReferenceRegistry();

    std::shared_ptr<IDocumentDBReference> get(std::string_view docType) const override;
    std::shared_ptr<IDocumentDBReference> tryGet(std::string_view docType) const override;
    void add(std::string_view name, std::shared_ptr<IDocumentDBReference> referee) override;
    void remove(std::string_view name) override;
};

} // namespace proton
