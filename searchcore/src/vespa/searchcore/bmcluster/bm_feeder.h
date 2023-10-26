// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <memory>
#include <atomic>

namespace document {

class DocumentTypeRepo;

}

namespace vespalib {

class ThreadStackExecutor;
class nbostream;

}

namespace search::bmcluster {

class AvgSampler;
class BmFeedParams;
class BmRange;
class IBmFeedHandler;
class PendingTracker;

/*
 * Class to feed serialized feed operations to a feed handler.
 */
class BmFeeder {
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    document::BucketSpace                             _bucket_space;
    IBmFeedHandler&                                   _feed_handler;
    vespalib::ThreadStackExecutor&                    _executor;
    vespalib::string                                  _all_fields;
    bool                                              _use_timestamp;
    std::atomic<bool>                                 _stop;
public:
    BmFeeder(std::shared_ptr<const document::DocumentTypeRepo> repo, IBmFeedHandler& feed_handler, vespalib::ThreadStackExecutor& executor);
    ~BmFeeder();
    void feed_operation(uint32_t op_idx, vespalib::nbostream &serialized_feed, int64_t time_bias, PendingTracker& tracker);
    uint32_t feed_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias);
    void run_feed_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler, const vespalib::string& op_name);
    IBmFeedHandler& get_feed_handler() const { return _feed_handler; }
    void stop();
    void run_feed_tasks_loop(int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, const vespalib::string &op_name);
};

}
