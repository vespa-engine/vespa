// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::common {
struct FieldSortSpec;
}

namespace search::attribute {

class IAttributeVector;
class ISortBlobWriter;

/*
 * Creates a sort blob writer that selects the value at the position of a given key in a map attribute
 * stored as two aligned array attributes (<map>.key and <map>.value). The selected value is treated as
 * a logically single valued optional scalar.
 *
 * Returns nullptr (after reporting an issue) if the attribute combination is not supported.
 */
std::unique_ptr<ISortBlobWriter> make_map_lookup_sort_blob_writer(const IAttributeVector&      key_attr,
                                                                  const IAttributeVector&      value_attr,
                                                                  const common::FieldSortSpec& field_sort_spec);

} // namespace search::attribute
