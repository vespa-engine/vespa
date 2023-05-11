// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributor_message_sender_stub.h"
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/distributor/stripe_host_info_notifier.h>

namespace storage {

namespace framework { struct TickingThreadPool; }

namespace distributor {

class DistributorBucketSpace;
class DistributorBucketSpaceRepo;
class DistributorMetricSet;
class DistributorNodeContext;
class DistributorStripe;
class DistributorStripeComponent;
class DistributorStripeOperationContext;
class DistributorStripePool;
class DocumentSelectionParser;
class ExternalOperationHandler;
class IdealStateManager;
class IdealStateMetricSet;
struct NodeSupportedFeatures;
class Operation;
class StripeBucketDBUpdater;

/**
 * Helper class with utilities needed when testing DistributorStripe.
 *
 * This was copied from DistributorTestUtil (used in LegacyDistributorTest)
 * and adjusted to work with one distributor stripe.
 */
class DistributorStripeTestUtil : public DoneInitializeHandler,
                                  public StripeHostInfoNotifier {
public:
    DistributorStripeTestUtil();
    ~DistributorStripeTestUtil() override;

    /**
     * Sets up the storage link chain.
     */
    void createLinks();
    void setTypeRepo(const std::shared_ptr<const document::DocumentTypeRepo>& repo);

    void close();

    /**
     * Returns a string with the nodes currently stored in the bucket
     * database for the given bucket.
     */
    std::string getNodes(document::BucketId id);

    /**
     * Returns a string with the ideal state nodes for the given bucket.
     */
    std::string getIdealStr(document::BucketId id, const lib::ClusterState& state);

    /**
     * Adds the ideal nodes for the given bucket and the given cluster state
     * to the bucket database.
     */
    void addIdealNodes(const lib::ClusterState& state, const document::BucketId& id);

    /**
     * Adds all the ideal nodes for the given bucket to the bucket database.
     */
    void addIdealNodes(const document::BucketId& id);

    /**
     * Parses the given string to a set of node => bucket info data,
     * and inserts them as nodes in the given bucket.
     * Format:
     *   "node1=checksum/docs/size,node2=checksum/docs/size"
     */
    void addNodesToBucketDB(const document::Bucket& bucket, const std::string& nodeStr);
    // As the above, but always inserts into default bucket space
    void addNodesToBucketDB(const document::BucketId& id, const std::string& nodeStr);

    /**
     * Removes the given bucket from the bucket database.
     */
    void removeFromBucketDB(const document::BucketId& id);

    /**
     * Inserts the given bucket information for the given bucket and node in
     * the bucket database.
     */
    void insertBucketInfo(document::BucketId id,
                          uint16_t node,
                          uint32_t checksum,
                          uint32_t count,
                          uint32_t size,
                          bool trusted = false,
                          bool active = false);

    /**
     * Inserts the given bucket information for the given bucket and node in
     * the bucket database.
     */
    void insertBucketInfo(document::BucketId id,
                          uint16_t node,
                          const api::BucketInfo& info,
                          bool trusted = false,
                          bool active = false);

    std::string dumpBucket(const document::BucketId& bucket);

    /**
     * Replies to message idx sent upwards with the given result code.
     * If idx = -1, replies to the last command received upwards.
     */
    void sendReply(Operation& op,
                   int idx = -1,
                   api::ReturnCode::Result result = api::ReturnCode::OK);

    StripeBucketDBUpdater& getBucketDBUpdater();
    IdealStateManager& getIdealStateManager();
    ExternalOperationHandler& getExternalOperationHandler();
    const storage::distributor::DistributorNodeContext& node_context() const;
    storage::distributor::DistributorStripeOperationContext& operation_context();
    const DocumentSelectionParser& doc_selection_parser() const;
    DistributorMetricSet& metrics();

    bool tick();

    const DistributorConfiguration& getConfig();

    vdstestlib::DirConfig& getDirConfig() {
        return _config;
    }

    // TODO explicit notion of bucket spaces for tests
    DistributorBucketSpace& getDistributorBucketSpace();
    BucketDatabase& getBucketDatabase(); // Implicit default space only
    BucketDatabase& getBucketDatabase(document::BucketSpace space);
    const BucketDatabase& getBucketDatabase() const; // Implicit default space only
    const BucketDatabase& getBucketDatabase(document::BucketSpace space) const;
    DistributorBucketSpaceRepo& getBucketSpaceRepo();
    const DistributorBucketSpaceRepo& getBucketSpaceRepo() const;
    DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo();
    const DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() const;
    [[nodiscard]] bool stripe_is_in_recovery_mode() const noexcept;
    [[nodiscard]] const lib::ClusterStateBundle& current_cluster_state_bundle() const noexcept;
    [[nodiscard]] std::string active_ideal_state_operations() const;
    [[nodiscard]] const PendingMessageTracker& pending_message_tracker() const noexcept;
    [[nodiscard]] PendingMessageTracker& pending_message_tracker() noexcept;
    [[nodiscard]] std::chrono::steady_clock::duration db_memory_sample_interval() const noexcept;
    void set_node_supported_features(uint16_t node, const NodeSupportedFeatures& features);

    const lib::Distribution& getDistribution() const;

    framework::defaultimplementation::FakeClock& getClock() { return _node->getClock(); }
    DistributorComponentRegister& getComponentRegister() { return _node->getComponentRegister(); }
    DistributorComponentRegisterImpl& getComponentRegisterImpl() { return _node->getComponentRegister(); }

    void setup_stripe(int redundancy,
                      int nodeCount,
                      const std::string& systemState,
                      uint32_t earlyReturn = false,
                      bool requirePrimaryToBeWritten = true);

    void setup_stripe(int redundancy,
                      int node_count,
                      const lib::ClusterStateBundle& state,
                      uint32_t early_return = false,
                      bool require_primary_to_be_written = true);

    void set_redundancy(uint32_t redundancy);

    void trigger_distribution_change(std::shared_ptr<lib::Distribution> distr);

    using ConfigBuilder = vespa::config::content::core::StorDistributormanagerConfigBuilder;

    std::shared_ptr<DistributorConfiguration> make_config() const;
    void configure_stripe(std::shared_ptr<const DistributorConfiguration> config);
    void configure_stripe(const ConfigBuilder& builder);

    // Implements DoneInitializeHandler
    void notifyDoneInitializing() override {}

    // Implements StripeHostInfoNotifier
    void notify_stripe_wants_to_send_host_info(uint16_t stripe_index) override {
        (void) stripe_index;
    }

    void disableBucketActivationInConfig(bool disable);

    BucketDatabase::Entry getBucket(const document::Bucket& bucket) const;
    // Gets bucket entry from default space only
    BucketDatabase::Entry getBucket(const document::BucketId& bId) const;

    std::vector<document::BucketSpace> getBucketSpaces() const;

    DistributorMessageSenderStub& sender() noexcept { return _sender; }
    const DistributorMessageSenderStub& sender() const noexcept { return _sender; }

    void setSystemState(const lib::ClusterState& systemState);

    // Invokes full cluster state transition pipeline rather than directly applying
    // the state and just pretending everything has been completed.
    void receive_set_system_state_command(const vespalib::string& state_str);

    void handle_top_level_message(const std::shared_ptr<api::StorageMessage>& msg);

    void simulate_set_pending_cluster_state(const vespalib::string& state_str);
    void clear_pending_cluster_state_bundle();

    template <typename CmdType>
    requires std::is_base_of_v<api::StorageCommand, CmdType>
    [[nodiscard]] std::shared_ptr<CmdType> sent_command(size_t idx) {
        assert(idx < _sender.commands().size());
        auto cmd = std::dynamic_pointer_cast<CmdType>(_sender.command(idx));
        assert(cmd != nullptr);
        return cmd;
    }

    template <typename ReplyType>
    requires std::is_base_of_v<api::StorageReply, ReplyType>
    [[nodiscard]] std::shared_ptr<ReplyType> sent_reply(size_t idx) {
        assert(idx < _sender.replies().size());
        auto reply = std::dynamic_pointer_cast<ReplyType>(_sender.reply(idx));
        assert(reply != nullptr);
        return reply;
    }

    void config_enable_condition_probing(bool enable);
    void tag_content_node_supports_condition_probing(uint16_t index, bool supported);

protected:
    vdstestlib::DirConfig _config;
    std::unique_ptr<TestDistributorApp> _node;
    std::shared_ptr<DistributorMetricSet> _metrics;
    std::shared_ptr<IdealStateMetricSet>  _ideal_state_metrics;
    std::unique_ptr<DistributorStripe> _stripe;
    DistributorMessageSenderStub _sender;
    DistributorMessageSenderStub _senderDown;
    HostInfo _hostInfo;
    bool _done_initializing;

    struct MessageSenderImpl : public ChainedMessageSender {
        DistributorMessageSenderStub& _sender;
        DistributorMessageSenderStub& _senderDown;
        MessageSenderImpl(DistributorMessageSenderStub& up, DistributorMessageSenderStub& down)
            : _sender(up), _senderDown(down) {}

        void sendUp(const std::shared_ptr<api::StorageMessage>& msg) override {
            _sender.send(msg);
        }
        void sendDown(const std::shared_ptr<api::StorageMessage>& msg) override {
            _senderDown.send(msg);
        }
    };
    MessageSenderImpl _messageSender;

    void enable_cluster_state(vespalib::stringref state);
    void enable_cluster_state(const lib::ClusterStateBundle& state);
};

}

}
