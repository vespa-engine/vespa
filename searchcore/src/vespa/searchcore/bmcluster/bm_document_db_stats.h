// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::bmcluster {

/*
 * Class containing stats for a document db
 */
class BmDocumentDbStats
{
    uint64_t _active_docs;
    uint64_t _stored_docs;
    uint64_t _total_docs;
    uint64_t _removed_docs;
    
public:
    BmDocumentDbStats();
    BmDocumentDbStats(uint64_t active_docs, uint64_t stored_docs, uint64_t total_docs, uint64_t removed_docs);
    ~BmDocumentDbStats();
    BmDocumentDbStats& operator+=(const BmDocumentDbStats& rhs);
    bool operator==(const BmDocumentDbStats &rhs) const;
    uint64_t get_active_docs() const noexcept { return _active_docs; }
    uint64_t get_stored_docs() const noexcept { return _stored_docs; }
    uint64_t get_total_docs() const noexcept { return _total_docs; }
    uint64_t get_removed_docs() const noexcept { return _removed_docs; }
};


}
