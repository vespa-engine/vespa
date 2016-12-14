// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "namehash.h"
#include "memoryconsumption.h"
#include <vespa/vespalib/stllike/hash_set.h>

namespace metrics {

struct NameSet : public vespalib::hash_set<std::string>  { };

NameHash::NameHash()
    : _hash(std::make_unique<NameSet>()),
      _unifiedCounter(0),
      _checkedCounter(0)
{ }

NameHash::~NameHash() { }

void
NameHash::updateName(std::string& name) {
    ++_checkedCounter;
    NameSet::const_iterator it(_hash->find(name));
    if (it != _hash->end()) {
        if (name.c_str() != it->c_str()) {
            name = *it;
            ++_unifiedCounter;
        }
    } else {
        _hash->insert(name);
    }
}

void
NameHash::addMemoryUsage(MemoryConsumption& mc) const {
    mc._nameHash += sizeof(NameHash)
                 + _hash->getMemoryConsumption()
                 - sizeof(NameSet);
    for (const std::string & name : *_hash) {
        mc._nameHashStrings += mc.getStringMemoryUsage(name, mc._nameHashUnique);
    }
}

} // metrics
