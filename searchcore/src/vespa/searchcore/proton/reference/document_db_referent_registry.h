// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_referent_registry.h"
#include <mutex>
#include <condition_variable>
#include <map>

namespace proton {

/*
 * Class implementing a registry of named IDocumentDBReferents.
 */
class DocumentDBReferentRegistry : public IDocumentDBReferentRegistry
{
    mutable std::mutex _lock;
    mutable std::condition_variable _cv;
    std::map<vespalib::string, std::shared_ptr<IDocumentDBReferent>> _handlers;
public:
    DocumentDBReferentRegistry();
    virtual ~DocumentDBReferentRegistry();

    virtual std::shared_ptr<IDocumentDBReferent> getDocumentDBReferent(vespalib::stringref docType) const override;
    virtual void addDocumentDBReferent(vespalib::stringref name, std::shared_ptr<IDocumentDBReferent> referee) override;
    virtual void removeDocumentDBReferent(vespalib::stringref name) override;
};

} // namespace proton
