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
    std::unique_ptr<ISortBlobWriter> make_sort_blob_writer(bool ascending, const common::BlobConverter* converter,
                                                           common::sortspec::MissingPolicy policy,
                                                           std::string_view missing_value) const override;
};

}
