// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/test.h>
#include <tests/persistence/persistencetestutils.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/storage/persistence/provider_error_wrapper.h>

using storage::spi::test::makeSpiBucket;

namespace storage {

struct ProviderErrorWrapperTest : PersistenceTestUtils {
};

namespace {

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
          errorWrapper(providerWrapper)
    {
        providerWrapper.setFailureMask(PersistenceProviderWrapper::FAIL_ALL_OPERATIONS);
    }
    ~Fixture() {}

    void perform_spi_operation() {
        errorWrapper.getBucketInfo(makeSpiBucket(document::BucketId(16, 1234)));
    }

    void check_no_listener_invoked_for_error(MockErrorListener& listener, spi::Result::ErrorType error) {
        providerWrapper.setResult(spi::Result(error, "beep boop"));
        perform_spi_operation();
        EXPECT_FALSE(listener._seen_fatal_error);
        EXPECT_FALSE(listener._seen_resource_exhaustion_error);
    }
};

}

TEST_F(ProviderErrorWrapperTest, fatal_error_invokes_listener) {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);
    f.providerWrapper.setResult(spi::Result(spi::Result::ErrorType::FATAL_ERROR, "eject! eject!"));

    EXPECT_FALSE(listener->_seen_fatal_error);
    f.perform_spi_operation();

    EXPECT_FALSE(listener->_seen_resource_exhaustion_error);
    EXPECT_TRUE(listener->_seen_fatal_error);
    EXPECT_EQ(vespalib::string("eject! eject!"), listener->_fatal_error);
}

TEST_F(ProviderErrorWrapperTest, resource_exhaustion_error_invokes_listener) {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);
    f.providerWrapper.setResult(spi::Result(spi::Result::ErrorType::RESOURCE_EXHAUSTED, "out of juice"));

    EXPECT_FALSE(listener->_seen_resource_exhaustion_error);
    f.perform_spi_operation();

    EXPECT_FALSE(listener->_seen_fatal_error);
    EXPECT_TRUE(listener->_seen_resource_exhaustion_error);
    EXPECT_EQ(vespalib::string("out of juice"), listener->_resource_exhaustion_error);
}

TEST_F(ProviderErrorWrapperTest, listener_not_invoked_on_success) {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);
    f.perform_spi_operation();

    EXPECT_FALSE(listener->_seen_fatal_error);
    EXPECT_FALSE(listener->_seen_resource_exhaustion_error);
}

TEST_F(ProviderErrorWrapperTest, listener_not_invoked_on_regular_errors) {
    Fixture f(getPersistenceProvider());
    auto listener = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener);

    EXPECT_NO_FATAL_FAILURE(f.check_no_listener_invoked_for_error(*listener, spi::Result::ErrorType::TRANSIENT_ERROR));
    EXPECT_NO_FATAL_FAILURE(f.check_no_listener_invoked_for_error(*listener, spi::Result::ErrorType::PERMANENT_ERROR));
}

TEST_F(ProviderErrorWrapperTest, multiple_listeners_can_be_registered) {
    Fixture f(getPersistenceProvider());
    auto listener1 = std::make_shared<MockErrorListener>();
    auto listener2 = std::make_shared<MockErrorListener>();
    f.errorWrapper.register_error_listener(listener1);
    f.errorWrapper.register_error_listener(listener2);

    f.providerWrapper.setResult(spi::Result(spi::Result::ErrorType::RESOURCE_EXHAUSTED, "out of juice"));
    f.perform_spi_operation();

    EXPECT_TRUE(listener1->_seen_resource_exhaustion_error);
    EXPECT_TRUE(listener2->_seen_resource_exhaustion_error);
}

} // ns storage


