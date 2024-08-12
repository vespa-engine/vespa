// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string_view>

namespace document {

class DocumentType;

class IDocumentTypeRepo {
public:
    virtual ~IDocumentTypeRepo() = default;
    virtual const DocumentType *getDocumentType(std::string_view name) const noexcept = 0;
};

}
