// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tablemanager.h"

namespace search::fef {

TableManager::TableManager() = default;

TableManager::~TableManager() = default;

const Table *
TableManager::getTable(const vespalib::string & name) const
{
    std::lock_guard guard(_lock);
    auto itr = _cache.find(name);
    if (itr != _cache.end()) {
        return itr->second.get();
    }
    for (size_t i = 0; i < _factories.size(); ++i) {
        Table::SP table = _factories[i]->createTable(name);
        if (table) {
            _cache.insert(std::make_pair(name, table));
            return table.get();
        }
    }
    _cache.insert(std::make_pair(name, Table::SP()));
    return nullptr;
}

}
