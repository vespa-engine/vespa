// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_feeder.h"
#include "avg_sampler.h"
#include "bm_feed_params.h"
#include "bm_range.h"
#include "bucket_selector.h"
#include "pending_tracker.h"
#include "i_bm_feed_handler.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucket.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <cassert>
#include <chrono>

#include <vespa/log/log.h>
LOG_SETUP(".bmcluster.bm_feeder");

using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using vespalib::makeLambdaTask;

namespace search::bmcluster {

BmFeeder::BmFeeder(std::shared_ptr<const DocumentTypeRepo> repo, IBmFeedHandler& feed_handler, vespalib::ThreadStackExecutor& executor)
    : _repo(std::move(repo)),
      _bucket_space(document::test::makeBucketSpace("test")),
      _feed_handler(feed_handler),
      _executor(executor)
{
}

BmFeeder::~BmFeeder() = default;

void
BmFeeder::put_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "put_async_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    _feed_handler.attach_bucket_info_queue(pending_tracker);
    auto &repo = *_repo;
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    bool use_timestamp = !_feed_handler.manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        auto document = std::make_unique<Document>(repo, is);
        _feed_handler.put(bucket, std::move(document), (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeeder::update_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "update_async_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    _feed_handler.attach_bucket_info_queue(pending_tracker);
    auto &repo = *_repo;
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    bool use_timestamp = !_feed_handler.manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        auto document_update = DocumentUpdate::createHEAD(repo, is);
        _feed_handler.update(bucket, std::move(document_update), (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeeder::get_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed)
{
    LOG(debug, "get_async_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    vespalib::string all_fields(document::AllFields::NAME);
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        DocumentId document_id(is);
        _feed_handler.get(bucket, all_fields, document_id, pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeeder::remove_async_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "remove_async_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    _feed_handler.attach_bucket_info_queue(pending_tracker);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    bool use_timestamp = !_feed_handler.manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        DocumentId document_id(is);
        _feed_handler.remove(bucket, document_id, (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeeder::run_put_async_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler)
{
    uint32_t old_errors = _feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < params.get_client_threads(); ++i) {
        auto range = params.get_range(i);
        _executor.execute(makeLambdaTask([this, max_pending = params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { put_async_task(max_pending, range, serialized_feed, time_bias); }));
    }
    _executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = _feed_handler.get_error_count() - old_errors;
    double throughput = params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "putAsync: pass=%u, errors=%u, puts/s: %8.2f", pass, new_errors, throughput);
    time_bias += params.get_documents();
}

void
BmFeeder::run_update_async_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler)
{
    uint32_t old_errors = _feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < params.get_client_threads(); ++i) {
        auto range = params.get_range(i);
        _executor.execute(makeLambdaTask([this, max_pending = params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { update_async_task(max_pending, range, serialized_feed, time_bias); }));
    }
    _executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = _feed_handler.get_error_count() - old_errors;
    double throughput = params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "updateAsync: pass=%u, errors=%u, updates/s: %8.2f", pass, new_errors, throughput);
    time_bias += params.get_documents();
}

void
BmFeeder::run_get_async_tasks(int pass, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler)
{
    uint32_t old_errors = _feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < params.get_client_threads(); ++i) {
        auto range = params.get_range(i);
        _executor.execute(makeLambdaTask([this, max_pending = params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range]()
                                        { get_async_task(max_pending, range, serialized_feed); }));
    }
    _executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = _feed_handler.get_error_count() - old_errors;
    double throughput = params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "getAsync: pass=%u, errors=%u, gets/s: %8.2f", pass, new_errors, throughput);
}

void
BmFeeder::run_remove_async_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler)
{
    uint32_t old_errors = _feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    for (uint32_t i = 0; i < params.get_client_threads(); ++i) {
        auto range = params.get_range(i);
        _executor.execute(makeLambdaTask([this, max_pending = params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias]()
                                        { remove_async_task(max_pending, range, serialized_feed, time_bias); }));
    }
    _executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = _feed_handler.get_error_count() - old_errors;
    double throughput = params.get_documents() / elapsed.count();
    sampler.sample(throughput);
    LOG(info, "removeAsync: pass=%u, errors=%u, removes/s: %8.2f", pass, new_errors, throughput);
    time_bias += params.get_documents();
}

}
