// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_array_bool_read_view.h"

namespace search::attribute {

ImportedArrayBoolReadView::ImportedArrayBoolReadView(TargetLids target_lids, const IArrayBoolReadView* target_read_view)
    : _target_lids(target_lids),
      _target_read_view(target_read_view)
{
}

ImportedArrayBoolReadView::~ImportedArrayBoolReadView() = default;

vespalib::BitSpan
ImportedArrayBoolReadView::get_values(uint32_t docid) const
{
    return _target_read_view->get_values(get_target_lid(docid));
}

}
