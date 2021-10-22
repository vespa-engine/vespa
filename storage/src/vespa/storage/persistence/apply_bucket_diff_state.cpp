// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "apply_bucket_diff_state.h"
#include "mergehandler.h"
#include "persistenceutil.h"
#include <vespa/document/base/documentid.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/vespalib/stllike/asciistream.h>

using storage::spi::Result;
using vespalib::RetainGuard;

namespace storage {

ApplyBucketDiffState::ApplyBucketDiffState(const MergeBucketInfoSyncer& merge_bucket_info_syncer, const spi::Bucket& bucket, RetainGuard&& retain_guard)
    : _merge_bucket_info_syncer(merge_bucket_info_syncer),
      _bucket(bucket),
      _fail_message(),
      _failed_flag(),
      _stale_bucket_info(false),
      _promise(),
      _tracker(),
      _delayed_reply(),
      _sender(nullptr),
      _retain_guard(std::move(retain_guard))
{
}

ApplyBucketDiffState::~ApplyBucketDiffState()
{
    try {
        sync_bucket_info();
    } catch (std::exception& e) {
        if (_fail_message.empty()) {
            _fail_message = e.what();
        }
    }
    if (_promise.has_value()) {
        _promise.value().set_value(_fail_message);
    }
    if (_delayed_reply) {
        if (!_delayed_reply->getResult().failed() && !_fail_message.empty()) {
            _delayed_reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, _fail_message));
        }
        if (_sender) {
            _sender->sendReply(std::move(_delayed_reply));
        } else {
            // _tracker->_reply and _delayed_reply points to the same reply.
            _tracker->sendReply();
        }
    }
}

void
ApplyBucketDiffState::on_entry_complete(std::unique_ptr<Result> result, const document::DocumentId &doc_id, const char *op)
{
    if (result->hasError() && !_failed_flag.test_and_set()) {
        vespalib::asciistream ss;
        ss << "Failed " << op
           << " for " << doc_id.toString()
           << " in " << _bucket
           << ": " << result->toString();
        _fail_message = ss.str();
    }
}

void
ApplyBucketDiffState::mark_stale_bucket_info()
{
    _stale_bucket_info = true;
}

void
ApplyBucketDiffState::sync_bucket_info()
{
    if (_stale_bucket_info) {
        _merge_bucket_info_syncer.sync_bucket_info(_bucket);
        _stale_bucket_info = false;
    }
}

std::future<vespalib::string>
ApplyBucketDiffState::get_future()
{
    _promise = std::promise<vespalib::string>();
    return _promise.value().get_future();
}

void
ApplyBucketDiffState::set_delayed_reply(std::unique_ptr<MessageTracker>&& tracker, std::shared_ptr<api::StorageReply>&& delayed_reply)
{
    _tracker = std::move(tracker);
    _delayed_reply = std::move(delayed_reply);
}

void
ApplyBucketDiffState::set_delayed_reply(std::unique_ptr<MessageTracker>&& tracker, MessageSender& sender, std::shared_ptr<api::StorageReply>&& delayed_reply)
{
    _tracker = std::move(tracker);
    _sender = &sender;
    _delayed_reply = std::move(delayed_reply);
}

}
