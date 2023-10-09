// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vespa/vespalib/stllike/string.h>

namespace search::bmcluster {

class BmRange;

/*
 * Parameters for generating synthetic feed of documents and for
 * feeding them to the cluster.
 */
class BmFeedParams
{
    uint32_t _client_threads;
    uint32_t _documents;
    uint32_t _max_pending;
    uint32_t get_start(uint32_t thread_id) const {
        return (_documents / _client_threads) * thread_id + std::min(thread_id, _documents % _client_threads);
    }
public:
    BmFeedParams();
    ~BmFeedParams();
    uint32_t get_client_threads() const { return _client_threads; }
    uint32_t get_documents() const { return _documents; }
    uint32_t get_max_pending() const { return _max_pending; }
    BmRange get_range(uint32_t thread_id) const;
    void set_documents(uint32_t documents_in) { _documents = documents_in; }
    void set_client_threads(uint32_t threads_in) { _client_threads = threads_in; }
    void set_max_pending(uint32_t max_pending_in) { _max_pending = max_pending_in; }
    bool check() const;
};

}
