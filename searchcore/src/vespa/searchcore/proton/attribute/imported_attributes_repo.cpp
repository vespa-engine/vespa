// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "imported_attributes_repo.h"
#include <vespa/searchcommon/attribute/iattributevector.h>

namespace proton {

using search::attribute::IAttributeVector;

ImportedAttributesRepo::ImportedAttributesRepo()
    : _repo()
{
}

ImportedAttributesRepo::~ImportedAttributesRepo()
{
}

void
ImportedAttributesRepo::add(const vespalib::string &name,
                            IAttributeVector::SP attr)
{
    _repo[name] = std::move(attr);
}

IAttributeVector::SP
ImportedAttributesRepo::get(const vespalib::string &name) const
{
    auto itr = _repo.find(name);
    if (itr != _repo.end()) {
        return itr->second;
    }
    return IAttributeVector::SP();
}

}
