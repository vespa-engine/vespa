// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/service_layer_error_listener.h>
#include <vespa/storage/storageserver/mergethrottler.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/vdstestlib/config/dirconfig.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage {

struct ServiceLayerErrorListenerTest : Test {
};

namespace {

class TestShutdownListener
    : public framework::defaultimplementation::ShutdownListener
{
public:
    TestShutdownListener() : _reason() {}

    void requestShutdown(vespalib::stringref reason) override {
        _reason = reason;
    }

    bool shutdown_requested() const { return !_reason.empty(); }
    const vespalib::string& reason() const { return _reason; }
private:
    vespalib::string _reason;
};

struct Fixture {
    vdstestlib::DirConfig config{getStandardConfig(true)};
    TestServiceLayerApp app;
    ServiceLayerComponent component{app.getComponentRegister(), "dummy"};
    MergeThrottler merge_throttler{config::ConfigUri(config.getConfigId()), app.getComponentRegister()};
    TestShutdownListener shutdown_listener;
    ServiceLayerErrorListener error_listener{component, merge_throttler};

    ~Fixture();
};

Fixture::~Fixture() = default;

}

TEST_F(ServiceLayerErrorListenerTest, shutdown_invoked_on_fatal_error) {
    Fixture f;

    f.app.getComponentRegister().registerShutdownListener(f.shutdown_listener);
    EXPECT_FALSE(f.shutdown_listener.shutdown_requested());

    f.error_listener.on_fatal_error("eject! eject!");
    EXPECT_TRUE(f.shutdown_listener.shutdown_requested());
    EXPECT_EQ("eject! eject!", f.shutdown_listener.reason());

    // Should only be invoked once
    f.error_listener.on_fatal_error("here be dragons");
    EXPECT_EQ("eject! eject!", f.shutdown_listener.reason());
}

TEST_F(ServiceLayerErrorListenerTest, merge_throttle_backpressure_invoked_on_resource_exhaustion_error) {
    Fixture f;

    EXPECT_FALSE(f.merge_throttler.backpressure_mode_active());
    f.error_listener.on_resource_exhaustion_error("buy more RAM!");
    EXPECT_TRUE(f.merge_throttler.backpressure_mode_active());
}

}
