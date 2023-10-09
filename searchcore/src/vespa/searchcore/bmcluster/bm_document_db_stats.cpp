// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_document_db_stats.h"

namespace search::bmcluster {

BmDocumentDbStats::BmDocumentDbStats()
    : BmDocumentDbStats(0u, 0u, 0u, 0u)
{
}

BmDocumentDbStats::BmDocumentDbStats(uint64_t active_docs, uint64_t stored_docs, uint64_t total_docs, uint64_t removed_docs)
    : _active_docs(active_docs),
      _stored_docs(stored_docs),
      _total_docs(total_docs),
      _removed_docs(removed_docs)
{
}


BmDocumentDbStats::~BmDocumentDbStats() = default;

BmDocumentDbStats&
BmDocumentDbStats::operator+=(const BmDocumentDbStats& rhs)
{
    _active_docs += rhs._active_docs;
    _stored_docs += rhs._stored_docs;
    _total_docs += rhs._total_docs;
    _removed_docs += rhs._removed_docs;
    return *this;
}

bool
BmDocumentDbStats::operator==(const BmDocumentDbStats &rhs) const
{
    return ((_active_docs == rhs._active_docs) &&
            (_stored_docs == rhs._stored_docs) &&
            (_total_docs  == rhs._total_docs) &&
            (_removed_docs == rhs._removed_docs));
}

}
