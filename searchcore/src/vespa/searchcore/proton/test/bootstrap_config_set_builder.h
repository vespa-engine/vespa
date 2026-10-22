// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-bucketspaces.h>
#include <vespa/config-proton.h>
#include <vespa/config-telemetry.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>

#include <string>

namespace document {
class DocumentTypeRepo;
}
namespace vespalib {
class HwInfo;
}

namespace proton::test {

/*
 * Builders for the portion of a config set related to proton bootstrap
 */
struct BootstrapConfigSetBuilder {
    using BucketspacesConfigBuilder = vespa::config::content::core::BucketspacesConfigBuilder;
    using DocumenttypesConfigBuilder = document::config::DocumenttypesConfigBuilder;
    using FiledistributorrpcConfigBuilder = cloud::config::filedistribution::FiledistributorrpcConfigBuilder;
    using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;
    using TelemetryConfigBuilder = ai::vespa::telemetry::TelemetryConfigBuilder;
    using BucketspacesConfig = vespa::config::content::core::BucketspacesConfig;
    using DocumenttypesConfig = document::config::DocumenttypesConfig;
    using FiledistributorrpcConfig = cloud::config::filedistribution::FiledistributorrpcConfig;
    using ProtonConfig = vespa::config::search::core::ProtonConfig;
    using TelemetryConfig = ai::vespa::telemetry::TelemetryConfig;

    BucketspacesConfigBuilder       bucketspaces;
    DocumenttypesConfigBuilder      documenttypes;
    FiledistributorrpcConfigBuilder filedistributorrpc;
    ProtonConfigBuilder             proton;
    TelemetryConfigBuilder          telemetry;

    BootstrapConfigSetBuilder();
    ~BootstrapConfigSetBuilder();

    void add_builders(config::ConfigSet& set, const std::string& config_id);
    std::shared_ptr<BootstrapConfig> build(int64_t generation, std::shared_ptr<const document::DocumentTypeRepo> repo,
                                           const vespalib::HwInfo& hw_info) const;
    std::shared_ptr<BootstrapConfig> build(int64_t generation, const vespalib::HwInfo& hw_info) const;
};

} // namespace proton::test
