// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_feed_params.h"
#include "bm_range.h"
#include <iostream>

namespace search::bmcluster {

BmFeedParams::BmFeedParams()
    : _client_threads(1),
      _documents(160000),
      _max_pending(1000)
{
}

BmFeedParams::~BmFeedParams() = default;

BmRange
BmFeedParams::get_range(uint32_t thread_id) const
{
    return BmRange(get_start(thread_id), get_start(thread_id + 1));
}

bool
BmFeedParams::check() const
{
    if (_client_threads < 1) {
        std::cerr << "Too few client threads: " << _client_threads << std::endl;
        return false;
    }
    if (_client_threads > 1024) {
        std::cerr << "Too many client threads: " << _client_threads << std::endl;
        return false;
    }
    if (_documents < _client_threads) {
        std::cerr << "Too few documents: " << _documents << std::endl;
        return false;
    }
    return true;
}

}
