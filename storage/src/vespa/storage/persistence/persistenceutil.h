// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/persistence/filestorage/filestorhandler.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/persistence/spi/context.h>
#include <vespa/vespalib/io/fileutil.h>

namespace storage::api {
    class StorageMessage;
    class StorageReply;
    class BucketInfo;
}

namespace storage::spi {
    struct PersistenceProvider;
}

namespace storage {

class PersistenceUtil;

struct DeferredReplySenderStub : MessageSender {
    std::mutex _mutex;
    std::vector<std::shared_ptr<api::StorageReply>> _deferred_replies;

    DeferredReplySenderStub();
    ~DeferredReplySenderStub() override;

    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override {
        abort(); // Not supported
    }
    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        std::lock_guard lock(_mutex);
        _deferred_replies.emplace_back(reply);
    }
};

class AsyncMessageBatch {
    std::shared_ptr<FileStorHandler::BucketLockInterface> _bucket_lock;
    const PersistenceUtil& _env;
    MessageSender& _reply_sender;
    DeferredReplySenderStub _deferred_sender_stub;
public:
    AsyncMessageBatch(std::shared_ptr<FileStorHandler::BucketLockInterface> bucket_lock,
                      const PersistenceUtil& env,
                      MessageSender& reply_sender) noexcept;
    // Triggered by last referencing batched MessageTracker being destroyed.
    // Fetches bucket info, updates DB and sends all deferred replies with the new bucket info.
    ~AsyncMessageBatch();

    [[nodiscard]] MessageSender& deferred_sender_stub() noexcept { return _deferred_sender_stub; }
};

class MessageTracker {
public:
    using UP = std::unique_ptr<MessageTracker>;

    MessageTracker(const framework::MilliSecTimer & timer, const PersistenceUtil & env, MessageSender & reply_sender,
                   FileStorHandler::BucketLockInterface::SP bucket_lock, std::shared_ptr<api::StorageMessage> msg,
                   ThrottleToken throttle_token);

    // For use with batching where bucket lock is held separately and bucket info
    // is _not_ fetched or updated per message.
    MessageTracker(const framework::MilliSecTimer& timer, const PersistenceUtil& env,
                   std::shared_ptr<AsyncMessageBatch> batch,
                   MessageSender& deferred_reply_sender,
                   std::shared_ptr<api::StorageMessage> msg,
                   ThrottleToken throttle_token);

    ~MessageTracker();

    void setMetric(FileStorThreadMetrics::Op& metric);

    /**
     * Called by operation handlers to set reply if they need to send a
     * non-default reply. They should call this function as soon as they create
     * a reply, to ensure it is stored in case of failure after reply creation.
     */
    void setReply(std::shared_ptr<api::StorageReply> reply) {
        assert( ! _reply );
        _reply = std::move(reply);
    }

    /** Utility function to be able to write a bit less in client. */
    void fail(uint32_t result, std::string_view message = "") {
        fail(api::ReturnCode((api::ReturnCode::Result)result, message));
    }
    /** Set the request to fail with the given failure. */
    void fail(const api::ReturnCode&);

    /** Don't send reply for the command being processed. Used by multi chain
     * commands like merge. */
    void dontReply() { _sendReply = false; }

    [[nodiscard]] bool hasReply() const { return bool(_reply); }
    [[nodiscard]] const api::StorageReply & getReply() const {
        return *_reply;
    }
    [[nodiscard]] api::StorageReply & getReply() {
        return *_reply;
    }
    [[nodiscard]] std::shared_ptr<api::StorageReply> && stealReplySP() && {
        return std::move(_reply);
    }

    void generateReply(api::StorageCommand& cmd);

    [[nodiscard]] api::ReturnCode getResult() const { return _result; }

    [[nodiscard]] spi::Context & context() { return _context; }
    [[nodiscard]] document::BucketId getBucketId() const {
        return _bucketLock->getBucket().getBucketId();
    }

    void sendReply();

    [[nodiscard]] bool checkForError(const spi::Result& response);

    // Returns a non-nullptr notifier instance iff the underlying operation wants to be notified
    // when the sync phase is complete. Otherwise, returns a nullptr shared_ptr.
    [[nodiscard]] std::shared_ptr<FileStorHandler::OperationSyncPhaseDoneNotifier> sync_phase_done_notifier_or_nullptr() const;

    static MessageTracker::UP
    createForTesting(const framework::MilliSecTimer & timer, PersistenceUtil & env, MessageSender & replySender,
                     FileStorHandler::BucketLockInterface::SP bucketLock, std::shared_ptr<api::StorageMessage> msg);

private:
    MessageTracker(const framework::MilliSecTimer& timer, const PersistenceUtil& env,
                   MessageSender& reply_sender, bool update_bucket_info,
                   std::shared_ptr<FileStorHandler::BucketLockInterface> bucket_lock,
                   std::shared_ptr<AsyncMessageBatch> part_of_batch,
                   std::shared_ptr<api::StorageMessage> msg,
                   ThrottleToken throttle_token);

    [[nodiscard]] bool count_result_as_failure() const noexcept;

    bool                                     _sendReply;
    bool                                     _updateBucketInfo;
    // Either _bucketLock or _part_of_batch must be set, never both at the same time
    FileStorHandler::BucketLockInterface::SP _bucketLock;
    std::shared_ptr<AsyncMessageBatch>       _part_of_batch; // nullptr if not batched
    std::shared_ptr<api::StorageMessage>     _msg;
    ThrottleToken                            _throttle_token;
    spi::Context                             _context;
    const PersistenceUtil                   &_env;
    MessageSender                           &_replySender;
    FileStorThreadMetrics::Op               *_metric; // needs a better and thread safe solution
    std::shared_ptr<api::StorageReply>       _reply;
    api::ReturnCode                          _result;
    framework::MilliSecTimer                 _timer;
};

class PersistenceUtil {
public:
    /** Lock the given bucket in the file stor handler. */
    struct LockResult {
        std::shared_ptr<FileStorHandler::BucketLockInterface> lock;
        LockResult() : lock() {}
    };

    PersistenceUtil(const ServiceLayerComponent&, FileStorHandler& fileStorHandler,
                    FileStorThreadMetrics& metrics, spi::PersistenceProvider& provider);
    ~PersistenceUtil();

    StorBucketDatabase& getBucketDatabase(document::BucketSpace bucketSpace) const {
        return _component.getBucketDatabase(bucketSpace);
    }
    spi::Bucket getBucket(const document::DocumentId& id, const document::Bucket &bucket) const;
    void setBucketInfo(MessageTracker& tracker, const document::Bucket &bucket) const;
    void updateBucketDatabase(const document::Bucket &bucket, const api::BucketInfo& info) const;
    LockResult lockAndGetDisk(const document::Bucket &bucket, StorBucketDatabase::Flag flags = StorBucketDatabase::NONE);
    api::BucketInfo getBucketInfo(const document::Bucket &bucket) const;
    const document::DocumentTypeRepo & getDocumentTypeRepo() const {
        if (componentHasChanged()) {
            reloadComponent();
        }
        return *_repos->documentTypeRepo;
    }
    const document::FieldSetRepo & getFieldSetRepo() const {
        if (componentHasChanged()) {
            reloadComponent();
        }
        return *_repos->fieldSetRepo;
    }

    static api::BucketInfo convertBucketInfo(const spi::BucketInfo&);
    static uint32_t convertErrorCode(const spi::Result& response);
public:
    const ServiceLayerComponent                &_component;
    FileStorHandler                            &_fileStorHandler;
    FileStorThreadMetrics                      &_metrics;  // Needs a better solution for speed and thread safety
    uint16_t                                    _nodeIndex;
private:
    bool componentHasChanged() const {
        return _lastGeneration != _component.getGeneration();
    }
    void reloadComponent() const;

    const document::BucketIdFactory                 &_bucketIdFactory;
    spi::PersistenceProvider                        &_spi;
    mutable uint64_t                                 _lastGeneration;
    mutable std::shared_ptr<StorageComponent::Repos> _repos;
};

} // storage

