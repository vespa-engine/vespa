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
 * The map key is matched exactly (cased), even when the key attribute has an uncased dictionary.
 * This is consistent with map lookup in grouping, but differs from matching on <map>.key.
 *
 * The enum handle of the map key is resolved once and compared against raw key enum handles for
 * the lifetime of the writer. The caller must therefore hold key_attr with a stable enum guard
 * (see IAttributeContext::getAttributeStableEnum) for as long as the writer is used; otherwise
 * enum store compaction may remap the key handles, so documents may silently miss or select the
 * value of a different key.
 * value_attr needs only an ordinary read guard, since the selected value is copied into the
 * sort blob immediately.
 *
 * Returns nullptr (after reporting an issue) if the attribute combination is not supported.
 */
std::unique_ptr<ISortBlobWriter> make_map_lookup_sort_blob_writer(const IAttributeVector&      key_attr,
                                                                  const IAttributeVector&      value_attr,
                                                                  const common::FieldSortSpec& field_sort_spec);

} // namespace search::attribute
