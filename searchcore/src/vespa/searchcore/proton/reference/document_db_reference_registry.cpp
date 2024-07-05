// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_reference_registry.h"

namespace proton {

DocumentDBReferenceRegistry::DocumentDBReferenceRegistry()
    : _lock(),
      _cv(),
      _handlers()
{
}

DocumentDBReferenceRegistry::~DocumentDBReferenceRegistry()
{
}

std::shared_ptr<IDocumentDBReference>
DocumentDBReferenceRegistry::get(std::string_view name) const
{
    std::unique_lock<std::mutex> guard(_lock);
    auto itr = _handlers.find(name);
    while (itr == _handlers.end()) {
        _cv.wait(guard);
        itr = _handlers.find(name);
    }
    return itr->second;
}

std::shared_ptr<IDocumentDBReference>
DocumentDBReferenceRegistry::tryGet(std::string_view name) const
{
    std::lock_guard<std::mutex> guard(_lock);
    auto itr = _handlers.find(name);
    if (itr == _handlers.end()) {
        return std::shared_ptr<IDocumentDBReference>();
    } else {
        return itr->second;
    }
}

void
DocumentDBReferenceRegistry::add(std::string_view name, std::shared_ptr<IDocumentDBReference> referee)
{
    std::lock_guard<std::mutex> guard(_lock);
    _handlers[name] = referee;
    _cv.notify_all();
}

void
DocumentDBReferenceRegistry::remove(std::string_view name)
{
    std::lock_guard<std::mutex> guard(_lock);
    _handlers.erase(name);
}

} // namespace proton
