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
    std::map<vespalib::string, std::shared_ptr<IDocumentDBReference>> _handlers;
public:
    DocumentDBReferenceRegistry();
    virtual ~DocumentDBReferenceRegistry();

    virtual std::shared_ptr<IDocumentDBReference> get(vespalib::stringref docType) const override;
    virtual std::shared_ptr<IDocumentDBReference> tryGet(vespalib::stringref docType) const override;
    virtual void add(vespalib::stringref name, std::shared_ptr<IDocumentDBReference> referee) override;
    virtual void remove(vespalib::stringref name) override;
};

} // namespace proton
