// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"

namespace search::attribute {

/**
 * Base class for all raw attributes.
 */
class RawAttribute : public NotImplementedAttribute
{
public:
    RawAttribute(const std::string& name, const Config& config);
    ~RawAttribute() override;

    bool is_sortable() const noexcept override;
    long onSerializeForAscendingSort(DocId doc, void* serTo, long available, const common::BlobConverter*) const override;
    long onSerializeForDescendingSort(DocId doc, void* serTo, long available, const common::BlobConverter*) const override;
};

}
