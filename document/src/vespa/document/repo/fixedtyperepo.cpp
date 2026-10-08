// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixedtyperepo.h"

#include <vespa/check_require.h>

namespace document {

FixedTypeRepo::FixedTypeRepo(const DocumentTypeRepo& repo, std::string_view type) noexcept
    : _repo(&repo), _doc_type(repo.getDocumentType(type)) {
    CHECK(_doc_type);
}

} // namespace document
