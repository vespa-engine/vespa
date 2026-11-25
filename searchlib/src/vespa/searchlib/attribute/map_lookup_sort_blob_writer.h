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
 * the lifetime of the writer. To keep the handle valid, the writer takes a stable enum guard on
 * key_attr when created and holds it until destroyed. That guard is a shared lock, which must be
 * released by the thread that took it, so the writer must be destroyed on the thread that created
 * it, and should be short lived since it blocks enum store changes to key_attr.
 * The caller still needs ordinary read guards on both attributes; value_attr needs no more than
 * that, since the selected value is copied into the sort blob immediately.
 *
 * Returns nullptr (after reporting an issue) if the attribute combination is not supported.
 */
std::unique_ptr<ISortBlobWriter> make_map_lookup_sort_blob_writer(const IAttributeVector&      key_attr,
                                                                  const IAttributeVector&      value_attr,
                                                                  const common::FieldSortSpec& field_sort_spec);

} // namespace search::attribute
