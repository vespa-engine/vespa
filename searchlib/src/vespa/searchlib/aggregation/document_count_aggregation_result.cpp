// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "document_count_aggregation_result.h"
#include "aggregation.hpp"
#include <vespa/document/fieldvalue/document.h>

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(DocumentCountAggregationResult, CountAggregationResult);

DocumentCountAggregationResult::DocumentCountAggregationResult() = default;
DocumentCountAggregationResult::~DocumentCountAggregationResult() = default;

DocumentCountAggregationResult::DocumentCountAggregationResult(const DocumentCountAggregationResult& other)
    : CountAggregationResult(other), _last_docid(), _last_gid()
{
}

DocumentCountAggregationResult&
DocumentCountAggregationResult::operator=(const DocumentCountAggregationResult& other)
{
    if (this != &other) {
        CountAggregationResult::operator=(other);
        _last_docid.reset();
        _last_gid.reset();
    }
    return *this;
}

void DocumentCountAggregationResult::onAggregate(const ResultNode&, DocId docid, HitRank)
{
    if (_last_docid != docid) {
        setCount(getCount() + 1);
        _last_docid = docid;
        _last_gid.reset();
    }
}

void DocumentCountAggregationResult::onAggregate(const ResultNode&, const document::Document& doc, HitRank)
{
    const auto& gid = doc.getId().getGlobalId();
    if (_last_gid != gid) {
        setCount(getCount() + 1);
        _last_gid = gid;
        _last_docid.reset();
    }
}

void DocumentCountAggregationResult::onReset()
{
    setCount(0);
    _last_docid.reset();
    _last_gid.reset();
}

void DocumentCountAggregationResult::onMerge(const AggregationResult& other)
{
    setCount(getCount() + static_cast<const DocumentCountAggregationResult&>(other).getCount());
    _last_docid.reset();
    _last_gid.reset();
}

vespalib::Serializer& DocumentCountAggregationResult::onSerialize(vespalib::Serializer& os) const
{
    return CountAggregationResult::onSerialize(os);
}

vespalib::Deserializer& DocumentCountAggregationResult::onDeserialize(vespalib::Deserializer& is)
{
    _last_docid.reset();
    _last_gid.reset();
    return CountAggregationResult::onDeserialize(is);
}

} // namespace search::aggregation

void forcelink_file_searchlib_aggregation_document_count_aggregation_result() {}
