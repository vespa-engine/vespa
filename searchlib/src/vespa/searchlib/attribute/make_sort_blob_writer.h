// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::common { struct FieldSortSpec; }

namespace search::attribute {

class IAttributeVector;
class ISortBlobWriter;

std::unique_ptr<ISortBlobWriter>
make_sort_blob_writer(const IAttributeVector* vector, const search::common::FieldSortSpec& field_sort_spec);

std::unique_ptr<ISortBlobWriter>
make_fieldpath_sort_blob_writer(const IAttributeVector* keyVector, 
                                const IAttributeVector* valueVector,
                                const std::string& searchKey,
                                const search::common::FieldSortSpec& field_sort_spec);

}
