// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bm_document_db_stats.h"
#include "bm_buckets_stats.h"
#include <optional>

namespace search::bmcluster {

/*
 * Class containing stats for a node
 */
class BmNodeStats
{
    std::optional<BmDocumentDbStats> _document_db;
    std::optional<BmBucketsStats>    _buckets;
public:
    BmNodeStats();
    ~BmNodeStats();
    BmNodeStats& operator+=(const BmNodeStats& rhs);
    bool operator==(const BmNodeStats &rhs) const;
    void set_document_db_stats(const BmDocumentDbStats &document_db);
    void merge_bucket_stats(const BmBucketsStats &buckets);
    const std::optional<BmDocumentDbStats>& get_document_db_stats() const noexcept { return _document_db; }
    const std::optional<BmBucketsStats>& get_buckets_stats() const noexcept { return _buckets; }
};

}
