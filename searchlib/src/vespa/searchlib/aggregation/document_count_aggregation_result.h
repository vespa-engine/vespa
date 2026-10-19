// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "countaggregationresult.h"
#include <vespa/document/base/globalid.h>
#include <optional>

namespace search::aggregation {

/** Counts each document once while its grouping elements are visited together. */
class DocumentCountAggregationResult : public CountAggregationResult {
    std::optional<DocId> _last_docid;
    std::optional<document::GlobalId> _last_gid;

public:
    DECLARE_AGGREGATIONRESULT_BASE(DocumentCountAggregationResult);
    DocumentCountAggregationResult();
    DocumentCountAggregationResult(const DocumentCountAggregationResult& other);
    DocumentCountAggregationResult& operator=(const DocumentCountAggregationResult& other);
    ~DocumentCountAggregationResult() override;
    DocumentCountAggregationResult* clone() const override {
        return new DocumentCountAggregationResult(*this);
    }

private:
    void onMerge(const AggregationResult& other) override;
    void onAggregate(const ResultNode&, DocId docid, HitRank) override;
    void onAggregate(const ResultNode&, const document::Document& doc, HitRank) override;
    void onReset() override;
};

} // namespace search::aggregation
