// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>

namespace search {

/*
 * This class provides a document id string view for the document with the given lid (local document id).
 * An empty string view indicates that the document id is not available.
 */
class IDocumentIdProvider {
public:
    virtual ~IDocumentIdProvider() = default;
    [[nodiscard]] virtual std::string_view get_document_id_string_view(uint32_t lid) const noexcept = 0;
};

} // namespace search
