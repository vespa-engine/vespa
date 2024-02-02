// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DistributorStripeComponent
 * \ingroup common
 *
 * \brief Component class including some service layer specific information.
 */

/**
 * \class storage::DistributorComponentRegister
 * \ingroup common
 *
 * \brief Specialization of ComponentRegister handling service layer components.
 */

/**
 * \class storage::DistributorManagedComponent
 * \ingroup common
 *
 * \brief Specialization of StorageManagedComponent.
 *
 * A service layer component register will use this interface in order to set
 * the service layer functionality parts.
 */

#pragma once

#include "storagecomponent.h"
#include <vespa/storageapi/defs.h>

namespace vespa::config::content::core::internal {
    class InternalStorDistributormanagerType;
    class InternalStorVisitordispatcherType;
}

namespace storage {

class DistributorConfiguration;

using DistributorManagerConfig = vespa::config::content::core::internal::InternalStorDistributormanagerType;
using VisitorDispatcherConfig = vespa::config::content::core::internal::InternalStorVisitordispatcherType;

struct UniqueTimeCalculator {
    virtual ~UniqueTimeCalculator() = default;
    [[nodiscard]] virtual api::Timestamp generate_unique_timestamp() = 0;
};

struct DistributorManagedComponent
{
    virtual ~DistributorManagedComponent() = default;

    virtual void setTimeCalculator(UniqueTimeCalculator&) = 0;
    virtual void setDistributorConfig(const DistributorManagerConfig&)= 0;
    virtual void setVisitorConfig(const VisitorDispatcherConfig&) = 0;
};

struct DistributorComponentRegister : public virtual StorageComponentRegister
{
    virtual void registerDistributorComponent(DistributorManagedComponent&) = 0;
};

class DistributorComponent : public StorageComponent,
                             private DistributorManagedComponent
{
    mutable UniqueTimeCalculator*                   _timeCalculator;
    std::unique_ptr<DistributorManagerConfig>       _distributorConfig;
    std::unique_ptr<VisitorDispatcherConfig>        _visitorConfig;
    uint64_t                                        _internal_config_generation; // Note: NOT related to config system generations
    std::shared_ptr<const DistributorConfiguration> _config_snapshot;

    void setTimeCalculator(UniqueTimeCalculator& utc) override { _timeCalculator = &utc; }
    void setDistributorConfig(const DistributorManagerConfig& c) override;
    void setVisitorConfig(const VisitorDispatcherConfig& c) override;
    void update_config_snapshot();
public:
    using UP = std::unique_ptr<DistributorComponent>;

    DistributorComponent(DistributorComponentRegister& compReg, vespalib::stringref name);
    ~DistributorComponent() override;

    [[nodiscard]] api::Timestamp getUniqueTimestamp() const {
        return _timeCalculator->generate_unique_timestamp();
    }
    const DistributorManagerConfig& getDistributorConfig() const {
        return *_distributorConfig;
    }

    uint64_t internal_config_generation() const noexcept {
        return _internal_config_generation;
    }
    std::shared_ptr<const DistributorConfiguration> total_distributor_config_sp() const noexcept {
        return _config_snapshot;
    }
};

} // storage
