// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_feeder.h"
#include "avg_sampler.h"
#include "bm_feed_operation.h"
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
      _executor(executor),
      _all_fields(document::AllFields::NAME),
      _use_timestamp(!_feed_handler.manages_timestamp()),
      _stop(false)
{
}

BmFeeder::~BmFeeder() = default;

void
BmFeeder::feed_operation(uint32_t op_idx, vespalib::nbostream &serialized_feed, int64_t time_bias, PendingTracker& tracker)
{
    document::BucketId bucket_id;
    BmFeedOperation feed_op;
    uint8_t feed_op_as_uint8_t;
    serialized_feed >> feed_op_as_uint8_t;
    feed_op = static_cast<BmFeedOperation>(feed_op_as_uint8_t);
    switch (feed_op) {
    case BmFeedOperation::PUT_OPERATION:
    {
        serialized_feed >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        auto document = std::make_unique<Document>(*_repo, serialized_feed);
        _feed_handler.put(bucket, std::move(document), (_use_timestamp ? (time_bias + op_idx) : 0), tracker);
    }
    break;
    case BmFeedOperation::UPDATE_OPERATION:
    {
        serialized_feed >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        auto document_update = DocumentUpdate::createHEAD(*_repo, serialized_feed);
        _feed_handler.update(bucket, std::move(document_update), (_use_timestamp ? (time_bias + op_idx) : 0), tracker);
    }
    break;
    case BmFeedOperation::GET_OPERATION:
    {
        serialized_feed >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        DocumentId document_id(serialized_feed);
        _feed_handler.get(bucket, _all_fields, document_id, tracker);
    }
    break;
    case BmFeedOperation::REMOVE_OPERATION:
    {
        serialized_feed >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        DocumentId document_id(serialized_feed);
        _feed_handler.remove(bucket, document_id, (_use_timestamp ? (time_bias + op_idx) : 0), tracker);
    }
    break;
    default:
        LOG(error, "Bad feed operation: %u", static_cast<unsigned int>(feed_op));
        std::_Exit(1);
    }
}


uint32_t
BmFeeder::feed_task(uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{

    LOG(debug, "feed_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    _feed_handler.attach_bucket_info_queue(pending_tracker);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    uint32_t op_count = 0;
    for (uint32_t i = range.get_start(); i < range.get_end() && !_stop.load(std::memory_order_relaxed); ++i) {
        feed_operation(i, is, time_bias, pending_tracker);
        ++op_count;
    }
    assert(is.empty() || _stop.load(std::memory_order_relaxed));
    pending_tracker.drain();
    return op_count;
}

void
BmFeeder::run_feed_tasks(int pass, int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, AvgSampler& sampler, const vespalib::string &op_name)
{
    uint32_t old_errors = _feed_handler.get_error_count();
    auto start_time = std::chrono::steady_clock::now();
    std::atomic<uint32_t> atomic_op_count(0u);
    for (uint32_t i = 0; i < params.get_client_threads(); ++i) {
        auto range = params.get_range(i);
        _executor.execute(makeLambdaTask([this, max_pending = params.get_max_pending(), &serialized_feed = serialized_feed_v[i], range, time_bias, &atomic_op_count]()
                                        { atomic_op_count += feed_task(max_pending, range, serialized_feed, time_bias); }));
    }
    _executor.sync();
    uint32_t op_count = atomic_op_count.load(std::memory_order_relaxed);
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    uint32_t new_errors = _feed_handler.get_error_count() - old_errors;
    double throughput = op_count / elapsed.count();
    sampler.sample(op_count, elapsed.count());
    LOG(info, "%sAsync: pass=%u, errors=%u, ops=%u of %u, %ss/s: %8.2f", op_name.c_str(), pass, new_errors, op_count, params.get_documents(), op_name.c_str(), throughput);
    time_bias += params.get_documents();
}

void
BmFeeder::stop()
{
    _stop.store(true, std::memory_order_release);
}

void
BmFeeder::run_feed_tasks_loop(int64_t& time_bias, const std::vector<vespalib::nbostream>& serialized_feed_v, const BmFeedParams& params, const vespalib::string &op_name)
{
    AvgSampler sampler;
    for (int pass = 0; !_stop.load(std::memory_order_relaxed); ++pass) {
        run_feed_tasks(pass, time_bias, serialized_feed_v, params, sampler, op_name);
    }
    LOG(info, "%sAsync: AVG %s/s: %8.2f", op_name.c_str(), op_name.c_str(), sampler.avg());
}

}
