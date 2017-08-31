// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/service_layer_error_listener.h>
#include <vespa/storage/storageserver/mergethrottler.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vdstestlib/cppunit/dirconfig.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>

namespace storage {

class ServiceLayerErrorListenerTest : public CppUnit::TestFixture {
public:
    CPPUNIT_TEST_SUITE(ServiceLayerErrorListenerTest);
    CPPUNIT_TEST(shutdown_invoked_on_fatal_error);
    CPPUNIT_TEST(merge_throttle_backpressure_invoked_on_resource_exhaution_error);
    CPPUNIT_TEST_SUITE_END();

    void shutdown_invoked_on_fatal_error();
    void merge_throttle_backpressure_invoked_on_resource_exhaution_error();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ServiceLayerErrorListenerTest);

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
    MergeThrottler merge_throttler{config.getConfigId(), app.getComponentRegister()};
    TestShutdownListener shutdown_listener;
    ServiceLayerErrorListener error_listener{component, merge_throttler};

    ~Fixture();
};

Fixture::~Fixture() {}

}

void ServiceLayerErrorListenerTest::shutdown_invoked_on_fatal_error() {
    Fixture f;

    f.app.getComponentRegister().registerShutdownListener(f.shutdown_listener);
    CPPUNIT_ASSERT(!f.shutdown_listener.shutdown_requested());

    f.error_listener.on_fatal_error("eject! eject!");
    CPPUNIT_ASSERT(f.shutdown_listener.shutdown_requested());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"), f.shutdown_listener.reason());

    // Should only be invoked once
    f.error_listener.on_fatal_error("here be dragons");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"), f.shutdown_listener.reason());
}

void ServiceLayerErrorListenerTest::merge_throttle_backpressure_invoked_on_resource_exhaution_error() {
    Fixture f;

    CPPUNIT_ASSERT(!f.merge_throttler.backpressure_mode_active());
    f.error_listener.on_resource_exhaustion_error("buy more RAM!");
    CPPUNIT_ASSERT(f.merge_throttler.backpressure_mode_active());
}

}
