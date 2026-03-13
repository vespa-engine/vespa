// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_bool_attribute_access.h"
#include <vespa/vespalib/util/bit_packer.h>

namespace search::attribute {

/**
 * Attribute vector storing an array of bool values per document
 * for streaming search, using BitPacker for bit-packed storage.
 *
 * All bool values across all documents are packed into a single
 * BitPacker. Per-document offsets track where each document's
 * values start and how many there are.
 */
class ArrayBoolExtAttribute : public ArrayBoolAttributeAccess,
                              public IExtendAttribute,
                              public IArrayBoolReadView
{
    vespalib::BitPacker   _bits;
    std::vector<uint64_t> _idx;  // per-doc bit offset: doc values are bits[_idx[doc].._idx[doc+1])

public:
    ArrayBoolExtAttribute(const std::string& name);
    ~ArrayBoolExtAttribute() override;

    // ArrayBoolAttributeAccess
    vespalib::BitSpan get_bools(DocId docid) const override;

    // IArrayBoolReadView
    vespalib::BitSpan get_values(DocId docid) const override;

    // IExtendAttribute
    bool add(int64_t v, int32_t weight) override;
    IExtendAttribute* getExtendInterface() override;

    // AttributeVector stuff we want to ignore
    bool addDoc(DocId& docId) override;
    void onCommit() override;
    bool onLoad(vespalib::Executor* executor) override;
    void onUpdateStat(CommitParam::UpdateStats updateStats) override;
    uint32_t clearDoc(DocId docId) override;
    void onAddDocs(DocId lidLimit) override;
    std::unique_ptr<SearchContext>
    getSearch(QueryTermSimpleUP term, const SearchContextParams& params) const override;

    // IMultiValueAttribute
    const IArrayBoolReadView* make_read_view(ArrayBoolTag, vespalib::Stash& stash) const override;
};

}
