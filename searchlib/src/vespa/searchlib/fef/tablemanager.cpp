// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tablemanager.h"

namespace search {
namespace fef {

TableManager::TableManager() :
    _factories(),
    _cache(),
    _lock()
{
}

TableManager::~TableManager() {}

const Table *
TableManager::getTable(const vespalib::string & name) const
{
    vespalib::LockGuard guard(_lock);
    TableCache::const_iterator itr = _cache.find(name);
    if (itr != _cache.end()) {
        return itr->second.get();
    }
    for (size_t i = 0; i < _factories.size(); ++i) {
        Table::SP table = _factories[i]->createTable(name);
        if (table.get() != NULL) {
            _cache.insert(std::make_pair(name, table));
            return table.get();
        }
    }
    _cache.insert(std::make_pair(name, Table::SP(NULL)));
    return NULL;
}

} // namespace fef
} // namespace search
