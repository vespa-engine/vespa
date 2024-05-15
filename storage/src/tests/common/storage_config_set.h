// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/common/configcontext.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/subscription/sourcespec.h>
#include <memory>

// FIXME "internal" here is very counter-productive since it precludes easy fwd decls.
//  Currently have to punch holes in internal abstractions to make this work at all.
namespace cloud::config::internal { class InternalSlobroksType; }
namespace messagebus::internal { class InternalMessagebusType; }
namespace metrics::internal { class InternalMetricsmanagerType; }
namespace document::config::internal { class InternalDocumenttypesType; }
namespace vespa::config::content::internal {
class InternalPersistenceType;
class InternalStorDistributionType;
class InternalStorFilestorType;
class InternalUpgradingType;
}
namespace vespa::config::content::core::internal {
class InternalBucketspacesType;
class InternalStorBouncerType;
class InternalStorCommunicationmanagerType;
class InternalStorDistributormanagerType;
class InternalStorPrioritymappingType;
class InternalStorServerType;
class InternalStorStatusType;
class InternalStorVisitorType;
class InternalStorVisitordispatcherType;
}

namespace storage {

class StorageConfigSet {
    using SlobroksConfigBuilder                 = cloud::config::internal::InternalSlobroksType;
    using MessagebusConfigBuilder               = messagebus::internal::InternalMessagebusType;
    using MetricsmanagerConfigBuilder           = metrics::internal::InternalMetricsmanagerType;
    using DocumenttypesConfigBuilder            = document::config::internal::InternalDocumenttypesType;
    using PersistenceConfigBuilder              = vespa::config::content::internal::InternalPersistenceType;
    using StorDistributionConfigBuilder         = vespa::config::content::internal::InternalStorDistributionType;
    using StorFilestorConfigBuilder             = vespa::config::content::internal::InternalStorFilestorType;
    using UpgradingConfigBuilder                = vespa::config::content::internal::InternalUpgradingType;
    using BucketspacesConfigBuilder             = vespa::config::content::core::internal::InternalBucketspacesType;
    using StorBouncerConfigBuilder              = vespa::config::content::core::internal::InternalStorBouncerType;
    using StorCommunicationmanagerConfigBuilder = vespa::config::content::core::internal::InternalStorCommunicationmanagerType;
    using StorDistributormanagerConfigBuilder   = vespa::config::content::core::internal::InternalStorDistributormanagerType;
    using StorPrioritymappingConfigBuilder      = vespa::config::content::core::internal::InternalStorPrioritymappingType;
    using StorServerConfigBuilder               = vespa::config::content::core::internal::InternalStorServerType;
    using StorStatusConfigBuilder               = vespa::config::content::core::internal::InternalStorStatusType;
    using StorVisitorConfigBuilder              = vespa::config::content::core::internal::InternalStorVisitorType;
    using StorVisitordispatcherConfigBuilder    = vespa::config::content::core::internal::InternalStorVisitordispatcherType;

    std::unique_ptr<DocumenttypesConfigBuilder>            _document_type_config;
    std::unique_ptr<SlobroksConfigBuilder>                 _slobroks_config;
    std::unique_ptr<MessagebusConfigBuilder>               _messagebus_config;
    std::unique_ptr<MetricsmanagerConfigBuilder>           _metrics_config;
    std::unique_ptr<PersistenceConfigBuilder>              _persistence_config;
    std::unique_ptr<StorDistributionConfigBuilder>         _distribution_config;
    std::unique_ptr<StorFilestorConfigBuilder>             _filestor_config;
    std::unique_ptr<UpgradingConfigBuilder>                _upgrading_config;
    std::unique_ptr<BucketspacesConfigBuilder>             _bucket_spaces_config;
    std::unique_ptr<StorBouncerConfigBuilder>              _bouncer_config;
    std::unique_ptr<StorCommunicationmanagerConfigBuilder> _communication_manager_config;
    std::unique_ptr<StorDistributormanagerConfigBuilder>   _distributor_manager_config;
    std::unique_ptr<StorPrioritymappingConfigBuilder>      _priority_mapping_config; // TODO removable?
    std::unique_ptr<StorServerConfigBuilder>               _server_config;
    std::unique_ptr<StorStatusConfigBuilder>               _status_config;
    std::unique_ptr<StorVisitorConfigBuilder>              _visitor_config;
    std::unique_ptr<StorVisitordispatcherConfigBuilder>    _visitor_dispatcher_config;

    vespalib::string                       _config_id_str;
    config::ConfigSet                      _config_set;
    std::shared_ptr<config::ConfigContext> _config_ctx;
    config::ConfigUri                      _config_uri;

public:
    StorageConfigSet(vespalib::string config_id_str, bool is_storage_node);
    ~StorageConfigSet();

    void init_default_configs(bool is_storage_node);
    void add_bucket_space_mapping(vespalib::string doc_type, vespalib::string bucket_space_name);
    void add_metric_consumer(vespalib::string name, const std::vector<vespalib::string>& added_metrics);
    void add_distribution_config(uint16_t nodes_in_top_level_group);
    void set_slobrok_config_port(int slobrok_port);
    void set_node_index(uint16_t node_index);

    [[nodiscard]] const config::ConfigUri& config_uri() const noexcept {
        return _config_uri;
    }

    DocumenttypesConfigBuilder&            document_type_config() noexcept { return *_document_type_config; }
    SlobroksConfigBuilder&                 slobroks_config() noexcept { return *_slobroks_config; }
    MessagebusConfigBuilder&               messagebus_config() noexcept {return *_messagebus_config; }
    MetricsmanagerConfigBuilder&           metrics_config() noexcept { return *_metrics_config; }
    PersistenceConfigBuilder&              persistence_config() noexcept { return *_persistence_config; }
    StorDistributionConfigBuilder&         distribution_config() noexcept { return *_distribution_config; }
    StorFilestorConfigBuilder&             filestor_config() noexcept { return *_filestor_config; }
    BucketspacesConfigBuilder&             bucket_spaces_config() noexcept { return *_bucket_spaces_config; }
    StorBouncerConfigBuilder&              bouncer_config() noexcept { return *_bouncer_config; };
    StorCommunicationmanagerConfigBuilder& communication_manager_config() noexcept { return *_communication_manager_config; }
    StorDistributormanagerConfigBuilder&   distributor_manager_config() noexcept { return *_distributor_manager_config; }
    StorServerConfigBuilder&               server_config() noexcept { return *_server_config; }
    StorStatusConfigBuilder&               status_config() noexcept { return *_status_config; }
    StorVisitorConfigBuilder&              visitor_config() noexcept { return *_visitor_config; }
    StorVisitordispatcherConfigBuilder&    visitor_dispatcher_config() noexcept {  return *_visitor_dispatcher_config; }

    [[nodiscard]] static std::unique_ptr<StorageConfigSet> make_node_config(bool is_storage_node) {
        return std::make_unique<StorageConfigSet>("my-node", is_storage_node);
    }

    [[nodiscard]] static std::unique_ptr<StorageConfigSet> make_storage_node_config() {
        return make_node_config(true);
    }

    [[nodiscard]] static std::unique_ptr<StorageConfigSet> make_distributor_node_config() {
        return make_node_config(false);
    }
};

}
