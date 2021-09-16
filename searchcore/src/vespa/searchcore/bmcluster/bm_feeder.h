// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <memory>

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

/*
 * Class to feed serialized feed operations to a feed handler.
 */
class BmFeeder {
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    document::BucketSpace                             _bucket_space;
    IBmFeedHandler&                                   _feed_handler;
    vespalib::ThreadStackExecutor&                    _executor;
public:
    BmFeeder(std::shared_ptr<const document::DocumentTypeRepo> repo, IBmFeedHandler& feed_handler, vespalib::ThreadStackExecutor& executor);
    ~BmFeeder();
    void put_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias);
    void update_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias);
    void get_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed);
    void remove_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias);
    void run_put_async_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler);
    void run_update_async_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler);
    void run_get_async_tasks(int pass, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler);
    void run_remove_async_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler);
    IBmFeedHandler& get_feed_handler() const { return _feed_handler; }
};

}
