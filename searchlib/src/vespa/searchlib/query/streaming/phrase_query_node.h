// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"

namespace search::streaming {

/**
   N-ary phrase operator. All terms must be satisfied and have the correct order
   with distance to next term equal to 1.
*/
class PhraseQueryNode : public AndQueryNode
{
public:
    PhraseQueryNode() noexcept : AndQueryNode("PHRASE"), _fieldInfo(32) { }
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void getPhrases(QueryNodeRefList & tl) override;
    void getPhrases(ConstQueryNodeRefList & tl) const override;
    const QueryTerm::FieldInfo & getFieldInfo(size_t fid) const { return _fieldInfo[fid]; }
    size_t getFieldInfoSize() const { return _fieldInfo.size(); }
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_NOT; }
    void addChild(QueryNode::UP child) override;
private:
    mutable std::vector<QueryTerm::FieldInfo> _fieldInfo;
    void updateFieldInfo(size_t fid, size_t offset, size_t fieldLength) const;
#if WE_EVER_NEED_TO_CACHE_THIS_WE_MIGHT_WANT_SOME_CODE_HERE
    HitList _cachedHitList;
    bool    _evaluated;
#endif
};

}
