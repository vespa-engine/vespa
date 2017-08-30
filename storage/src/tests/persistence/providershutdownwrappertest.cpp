// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/persistence/persistencetestutils.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>

namespace storage {

// TODO rename file
class ProviderErrorWrapperTest : public SingleDiskPersistenceTestUtils {
public:
    CPPUNIT_TEST_SUITE(ProviderErrorWrapperTest);
    CPPUNIT_TEST(testShutdownOnFatalError);
    CPPUNIT_TEST(fatal_error_invokes_listener);
    CPPUNIT_TEST(resource_exhaustion_error_invokes_listener);
    CPPUNIT_TEST(listener_not_invoked_on_success);
    CPPUNIT_TEST(listener_not_invoked_on_regular_errors);
    CPPUNIT_TEST(multiple_listeners_can_be_registered);
    CPPUNIT_TEST_SUITE_END();

    void testShutdownOnFatalError();
    void fatal_error_invokes_listener();
    void resource_exhaustion_error_invokes_listener();
    void listener_not_invoked_on_success();
    void listener_not_invoked_on_regular_errors();
    void multiple_listeners_can_be_registered();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ProviderErrorWrapperTest);

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

struct MockErrorListener : ProviderErrorListener {
    void on_fatal_error(vespalib::stringref message) override {
        _seen_fatal_error = true;
        _fatal_error = message;
    }
    void on_resource_exhaustion_error(vespalib::stringref message) override {
        _seen_resource_exhaustion_error = true;
        _resource_exhaustion_error = message;
    }

    vespalib::string _fatal_error;
    vespalib::string _resource_exhaustion_error;
    bool _seen_fatal_error{false};
    bool _seen_resource_exhaustion_error{false};
};

struct Fixture {
    // We wrap the wrapper. It's turtles all the way down!
    PersistenceProviderWrapper providerWrapper;
    TestServiceLayerApp app;
    ServiceLayerComponent component;
    ProviderErrorWrapper errorWrapper;

    Fixture(spi::PersistenceProvider& provider)
        : providerWrapper(provider),
          app(),
          component(app.getComponentRegister(), "dummy"),
          errorWrapper(providerWrapper, component)
    {
        providerWrapper.setFailureMask(PersistenceProviderWrapper::FAIL_ALL_OPERATIONS);
    }
    ~Fixture();

    void perform_spi_operation() {
        errorWrapper.getBucketInfo(spi::Bucket(document::BucketId(16, 1234), spi::PartitionId(0)));
    }
};

Fixture::~Fixture() {}

}

void ProviderErrorWrapperTest::fatal_error_invokes_listener() {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);
    f.providerWrapper.setResult(spi::Result(spi::Result::FATAL_ERROR, "eject! eject!"));

    CPPUNIT_ASSERT(!listener->_seen_fatal_error);
    f.perform_spi_operation();

    CPPUNIT_ASSERT(!listener->_seen_resource_exhaustion_error);
    CPPUNIT_ASSERT(listener->_seen_fatal_error);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"), listener->_fatal_error);
}

void ProviderErrorWrapperTest::resource_exhaustion_error_invokes_listener() {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);
    f.providerWrapper.setResult(spi::Result(spi::Result::RESOURCE_EXHAUSTED, "out of juice"));

    CPPUNIT_ASSERT(!listener->_seen_resource_exhaustion_error);
    f.perform_spi_operation();

    CPPUNIT_ASSERT(!listener->_seen_fatal_error);
    CPPUNIT_ASSERT(listener->_seen_resource_exhaustion_error);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("out of juice"), listener->_resource_exhaustion_error);
}

void ProviderErrorWrapperTest::listener_not_invoked_on_success() {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);
    f.perform_spi_operation();

    CPPUNIT_ASSERT(!listener->_seen_fatal_error);
    CPPUNIT_ASSERT(!listener->_seen_resource_exhaustion_error);
}

void ProviderErrorWrapperTest::listener_not_invoked_on_regular_errors() {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);

    // TODO dedupe
    f.providerWrapper.setResult(spi::Result(spi::Result::TRANSIENT_ERROR, "beep boop"));
    f.perform_spi_operation();
    CPPUNIT_ASSERT(!listener->_seen_fatal_error);
    CPPUNIT_ASSERT(!listener->_seen_resource_exhaustion_error);

    f.providerWrapper.setResult(spi::Result(spi::Result::PERMANENT_ERROR, "beep boop!!"));
    f.perform_spi_operation();
    CPPUNIT_ASSERT(!listener->_seen_fatal_error);
    CPPUNIT_ASSERT(!listener->_seen_resource_exhaustion_error);
}

void ProviderErrorWrapperTest::multiple_listeners_can_be_registered() {
    Fixture f(getPersistenceProvider());
    auto listener1 = std::make_shared<MockErrorListener>();
    auto listener2 = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener1);
    f.errorWrapper.register_error_listener(listener2);

    f.providerWrapper.setResult(spi::Result(spi::Result::RESOURCE_EXHAUSTED, "out of juice"));
    f.perform_spi_operation();

    CPPUNIT_ASSERT(listener1->_seen_resource_exhaustion_error);
    CPPUNIT_ASSERT(listener2->_seen_resource_exhaustion_error);
}



// TODO rewrite, move component interaction testing elsewhere
void
ProviderErrorWrapperTest::testShutdownOnFatalError() {
    Fixture f(getPersistenceProvider());

    TestShutdownListener shutdownListener;

    f.app.getComponentRegister().registerShutdownListener(shutdownListener);

    f.providerWrapper.setResult(
            spi::Result(spi::Result::FATAL_ERROR, "eject! eject!"));
    f.providerWrapper.setFailureMask(
            PersistenceProviderWrapper::FAIL_ALL_OPERATIONS);

    CPPUNIT_ASSERT(!shutdownListener.shutdownRequested());
    // This should cause the node to implicitly be shut down
    f.errorWrapper.getBucketInfo(
            spi::Bucket(document::BucketId(16, 1234),
                        spi::PartitionId(0)));

    CPPUNIT_ASSERT(shutdownListener.shutdownRequested());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"),
                         shutdownListener.getReason());

    // Triggering a new error should not cause shutdown to be requested twice.
    f.providerWrapper.setResult(
            spi::Result(spi::Result::FATAL_ERROR, "boom!"));

    f.errorWrapper.getBucketInfo(
            spi::Bucket(document::BucketId(16, 1234),
                        spi::PartitionId(0)));

    CPPUNIT_ASSERT_EQUAL(vespalib::string("eject! eject!"),
                         shutdownListener.getReason());
}

} // ns storage


