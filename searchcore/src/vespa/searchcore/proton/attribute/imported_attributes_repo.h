// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search { namespace attribute { class ImportedAttributeVector; } }

namespace proton {

/**
 * A repository of imported attribute vector instances.
 */
class ImportedAttributesRepo {
private:
    using ImportedAttributeVector = search::attribute::ImportedAttributeVector;
    using Repo = vespalib::hash_map<vespalib::string, std::shared_ptr<ImportedAttributeVector>>;

    Repo _repo;

public:
    using UP = std::unique_ptr<ImportedAttributesRepo>;
    ImportedAttributesRepo();
    ~ImportedAttributesRepo();
    void add(const vespalib::string &name, std::shared_ptr<ImportedAttributeVector> attr);
    const std::shared_ptr<ImportedAttributeVector> & get(const vespalib::string &name) const;
    void getAll(std::vector<std::shared_ptr<ImportedAttributeVector>> &result) const;
    size_t size() const { return _repo.size(); }
};

}
