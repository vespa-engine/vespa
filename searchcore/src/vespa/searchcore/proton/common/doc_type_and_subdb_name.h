// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "doctypename.h"

#include <memory>

namespace proton {

/*
 * Document type name and subdb name, used by document subdbs, initializer tasks and flush targets.
 */
class DocTypeAndSubDbName {
    const DocTypeName _doc_type_name;
    const std::string _subdb_name;

public:
    DocTypeAndSubDbName(const DocTypeName& doc_type_name_in, const std::string& subdb_name_in);
    ~DocTypeAndSubDbName();

    [[nodiscard]] const DocTypeName& doc_type_name() const noexcept { return _doc_type_name; }
    [[nodiscard]] const std::string& subdb_name() const noexcept { return _subdb_name; }
    [[nodiscard]] std::string combined_name() const;
    [[nodiscard]] static std::shared_ptr<const DocTypeAndSubDbName> create(const DocTypeName& doc_type_name_in,
                                                                           const std::string& subdb_name_in);
};

} // namespace proton
