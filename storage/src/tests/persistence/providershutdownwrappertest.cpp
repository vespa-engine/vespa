// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/persistence/persistencetestutils.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>

namespace storage {

class ProviderShutdownWrapperTest : public SingleDiskPersistenceTestUtils
{
public:
    CPPUNIT_TEST_SUITE(ProviderShutdownWrapperTest);
    CPPUNIT_TEST(testShutdownOnFatalError);
    CPPUNIT_TEST_SUITE_END();

    void testShutdownOnFatalError();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ProviderShutdownWrapperTest);

namespace {

class TestShutdownListener
    : public framework::defaultimplementation::ShutdownListener
{
public:
    TestShutdownListener() : _reason() {}

    void requestShutdown(vespalib::stringref reason) override {
        _reason = reason;
    }

    bool shutdownRequested() const { return !_reason.empty(); }
    const vespalib::string& getReason() const { return _reason; }
private:
    vespalib::string _reason;
};

}

void
ProviderShutdownWrapperTest::testShutdownOnFatalError()
{
    // We wrap the wrapper. It's turtles all the way down!
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    TestServiceLayerApp app;
    ServiceLayerComponent component(app.getComponentRegister(), "dummy");

    ProviderShutdownWrapper shutdownWrapper(providerWrapper, component);

    TestShutdownListener shutdownListener;

    app.getComponentRegister().registerShutdownListener(shutdownListener);

    providerWrapper.setResult(
            spi::Result(spi::Result::FATAL_ERROR, "eject! eject!"));
    providerWrapper.setFailureMask(
            PersistenceProviderWrapper::FAIL_ALL_OPERATIONS);

    CPPUNIT_ASSERT(!shutdownListener.shutdownRequested());
    // This should cause the node to implicitly be shut down
    shutdownWrapper.getBucketInfo(
            spi::Bucket(document::BucketId(16, 1234),
                        spi::PartitionId(0)));

    CPPUNIT_ASSERT(shutdownListener.shutdownRequested());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"),
                         shutdownListener.getReason());

    // Triggering a new error should not cause shutdown to be requested twice.
    providerWrapper.setResult(
            spi::Result(spi::Result::FATAL_ERROR, "boom!"));

    shutdownWrapper.getBucketInfo(
            spi::Bucket(document::BucketId(16, 1234),
                        spi::PartitionId(0)));

    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"),
                         shutdownListener.getReason());
}

} // ns storage


