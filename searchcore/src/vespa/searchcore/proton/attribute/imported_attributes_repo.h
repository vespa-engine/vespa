// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace attribute { class IAttributeVector; } }

namespace proton {

/**
 * A repository of imported attribute vector instances.
 */
class ImportedAttributesRepo {
private:
    using IAttributeVector = search::attribute::IAttributeVector;
    using Repo = std::map<vespalib::string, std::shared_ptr<IAttributeVector>>;

    Repo _repo;

public:
    using UP = std::unique_ptr<ImportedAttributesRepo>;
    ImportedAttributesRepo();
    void add(const vespalib::string &name, std::shared_ptr<IAttributeVector> attr);
    std::shared_ptr<IAttributeVector> get(const vespalib::string &name) const;
    size_t size() const { return _repo.size(); }
};

}
