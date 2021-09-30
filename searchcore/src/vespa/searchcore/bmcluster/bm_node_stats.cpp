// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_node_stats.h"
#include <cassert>

namespace search::bmcluster {

namespace {

template <class Stats>
void merge(std::optional<Stats> &lhs, const Stats &rhs)
{
    if (lhs) {
        auto value = lhs.value();
        value += rhs;
        lhs = value;
    } else {
        lhs = rhs;
    }
}

template <class Stats>
void merge(std::optional<Stats> &lhs, const std::optional<Stats> &rhs)
{
    if (rhs) {
        merge(lhs, rhs.value());
    }
}

}

BmNodeStats::BmNodeStats()
    : _document_db(),
      _buckets()
{
}


BmNodeStats::~BmNodeStats() = default;

BmNodeStats&
BmNodeStats::operator+=(const BmNodeStats& rhs)
{
    merge(_document_db, rhs._document_db);
    merge(_buckets, rhs._buckets);
    return *this;
}

bool
BmNodeStats::operator==(const BmNodeStats &rhs) const
{
    return ((_document_db == rhs._document_db) &&
            (_buckets == rhs._buckets));
}

void
BmNodeStats::set_document_db_stats(const BmDocumentDbStats &document_db)
{
    assert(!_document_db);
    _document_db = document_db;
}

void
BmNodeStats::merge_bucket_stats(const BmBucketsStats &buckets)
{
    merge(_buckets, buckets);
}

}
