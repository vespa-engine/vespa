// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attributes_repo.h"
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace proton {

using search::attribute::ImportedAttributeVector;

namespace {

ImportedAttributeVector::SP _empty;

}

ImportedAttributesRepo::ImportedAttributesRepo() = default;
ImportedAttributesRepo::~ImportedAttributesRepo() = default;

void
ImportedAttributesRepo::add(const vespalib::string &name,
                            ImportedAttributeVector::SP attr)
{
    _repo[name] = std::move(attr);
}

const ImportedAttributeVector::SP &
ImportedAttributesRepo::get(const vespalib::string &name) const
{
    auto itr = _repo.find(name);
    if (itr != _repo.end()) {
        return itr->second;
    }
    return _empty;
}

void
ImportedAttributesRepo::getAll(std::vector<std::shared_ptr<ImportedAttributeVector>> &result) const
{
    result.reserve(_repo.size());
    for (const auto &itr : _repo) {
        result.push_back(itr.second);
    }
}

}
