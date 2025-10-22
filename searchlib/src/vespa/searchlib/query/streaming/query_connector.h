// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"
#include <vespa/searchlib/parsequery/parse.h>
#include <optional>

namespace search::streaming {

class QueryVisitor;

/**
   Base class for all N-ary query operators.
   Implements the width, depth, print, and collect all leafs operators(terms).
*/
class QueryConnector : public QueryNode
{
public:
    explicit QueryConnector(const char * opName) noexcept;
    ~QueryConnector() override;
    const HitList & evaluateHits(HitList & hl) override;
    void unpack_match_data(uint32_t docid, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env,
                           search::common::ElementIds element_ids) override;
    void reset() override;
    void getLeaves(QueryTermList & tl) override;
    void getLeaves(ConstQueryTermList & tl) const override;
    size_t depth() const override;
    size_t width() const override;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    void setIndex(std::string index) override { _index = std::move(index); }
    const std::string & getIndex() const override { return _index; }
    static std::unique_ptr<QueryConnector> create(ParseItem::ItemType type, const QueryNodeResultFactory& factory);
    virtual bool isFlattenable(ParseItem::ItemType type) const { (void) type; return false; }
    const QueryNodeList & getChildren() const { return _children; }
    virtual void addChild(std::unique_ptr<QueryNode> child);
    size_t size() const { return _children.size(); }
    const std::unique_ptr<QueryNode> & operator [](size_t index) const { return _children[index]; }
private:
    std::string _opName;
    std::string _index;
    QueryNodeList _children;
protected:
    std::optional<bool> _cached_evaluate_result;
};

}
