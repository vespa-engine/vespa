// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/persistence/provider_error_wrapper.h>
#include <atomic>

namespace storage {

class StorageComponent;
class MergeThrottler;

/*
 * Listener implementation for SPI errors that require action beyond simply
 * responding to the command that generated them.
 *
 * - Fatal errors will trigger a process shutdown.
 * - Resource exhaustion errors will trigger merge back-pressure.
 */
class ServiceLayerErrorListener : public ProviderErrorListener {
    StorageComponent& _component;
    MergeThrottler&   _merge_throttler;
    std::atomic<bool> _shutdown_initiated;
public:
    ServiceLayerErrorListener(StorageComponent& component,
                              MergeThrottler& merge_throttler) noexcept
        : _component(component),
          _merge_throttler(merge_throttler),
          _shutdown_initiated(false)
    {}

    void on_fatal_error(vespalib::stringref message) override;
    void on_resource_exhaustion_error(vespalib::stringref message) override;
};

}
