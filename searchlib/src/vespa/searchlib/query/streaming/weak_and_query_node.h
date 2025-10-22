// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query.h"

namespace search::streaming {

class QueryVisitor;

/**
 * N-ary WeakAnd operator for streaming search.
 * WeakAnd is a soft AND that matches documents where at least some terms match,
 * ranking by the number of matched terms.
 */
class WeakAndQueryNode : public OrQueryNode
{
    uint32_t    _targetNumHits;
    std::string _view;
public:
    WeakAndQueryNode(uint32_t targetNumHits, std::string view)
        : OrQueryNode("WAND"),
          _targetNumHits(targetNumHits),
          _view(std::move(view))
    {}
    ~WeakAndQueryNode() override;

    uint32_t getTargetNumHits() const { return _targetNumHits; }
    const std::string & getView() const { return _view; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void accept(QueryVisitor &visitor);
};

}
