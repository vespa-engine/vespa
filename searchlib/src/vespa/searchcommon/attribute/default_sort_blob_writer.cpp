// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "default_sort_blob_writer.h"
#include "iattributevector.h"

namespace search::attribute {

template <bool ascending>
DefaultSortBlobWriter<ascending>::DefaultSortBlobWriter(const IAttributeVector& attr, const common::BlobConverter* converter)
    : _attr(attr),
      _converter(converter)
{
}

template <bool ascending>
long
DefaultSortBlobWriter<ascending>::write(uint32_t docid, void* buf, long available) const
{
    if constexpr (ascending) {
        return _attr.serializeForAscendingSort(docid, buf, available, _converter);
    } else {
        return _attr.serializeForDescendingSort(docid, buf, available, _converter);
    }
}

template class DefaultSortBlobWriter<true>;
template class DefaultSortBlobWriter<false>;

}
