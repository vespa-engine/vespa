// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "document_db_referent_registry.h"

namespace proton {

DocumentDBReferentRegistry::DocumentDBReferentRegistry()
    : _lock()
{
}

DocumentDBReferentRegistry::~DocumentDBReferentRegistry()
{
}

std::shared_ptr<IDocumentDBReferent>
DocumentDBReferentRegistry::getDocumentDBReferent(vespalib::stringref name) const
{
    std::unique_lock<std::mutex> guard(_lock);
    auto itr = _handlers.find(name);
    while (itr == _handlers.end()) {
        _cv.wait(guard);
        itr = _handlers.find(name);
    }
    return itr->second;
}

void
DocumentDBReferentRegistry::addDocumentDBReferent(vespalib::stringref name, std::shared_ptr<IDocumentDBReferent> referee)
{
    std::lock_guard<std::mutex> guard(_lock);
    _handlers[name] = referee;
    _cv.notify_all();
}

void
DocumentDBReferentRegistry::removeDocumentDBReferent(vespalib::stringref name)
{
    std::lock_guard<std::mutex> guard(_lock);
    _handlers.erase(name);
}

} // namespace proton
