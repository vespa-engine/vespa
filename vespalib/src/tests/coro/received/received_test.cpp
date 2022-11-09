// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/received.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <memory>

using vespalib::coro::Received;

TEST(ReceivedTest, can_store_simple_value) {
    Received<int> result;
    result.set_value(42);
    EXPECT_TRUE(result.has_value());
    EXPECT_FALSE(result.has_error());
    EXPECT_FALSE(result.was_canceled());
    EXPECT_FALSE(result.get_error());
    EXPECT_EQ(result.get_value(), 42);
}

TEST(ReceivedTest, can_store_error) {
    Received<int> result;
    auto err = std::make_exception_ptr(std::runtime_error("stuff happened"));
    result.set_error(err);
    EXPECT_FALSE(result.has_value());
    EXPECT_TRUE(result.has_error());
    EXPECT_FALSE(result.was_canceled());
    EXPECT_EQ(result.get_error(), err);
}

TEST(ReceivedTest, can_store_nothing) {
    Received<int> result;
    result.set_done();
    EXPECT_FALSE(result.has_value());
    EXPECT_FALSE(result.has_error());
    EXPECT_TRUE(result.was_canceled());
}

TEST(ReceivedTest, can_store_move_only_value) {
    Received<std::unique_ptr<int>> result;
    result.set_value(std::make_unique<int>(42));
    EXPECT_TRUE(result.has_value());
    EXPECT_FALSE(result.has_error());
    EXPECT_FALSE(result.was_canceled());
    EXPECT_FALSE(result.get_error());
    auto res = std::move(result).get_value();    
    EXPECT_EQ(*res, 42);
    EXPECT_TRUE(result.has_value());
    EXPECT_EQ(result.get_value().get(), nullptr);
}

TEST(ReceivedTest, can_forward_value_to_std_promise) {
    Received<std::unique_ptr<int>> result;
    result.set_value(std::make_unique<int>(42));
    std::promise<std::unique_ptr<int>> promise;
    auto future = promise.get_future();
    result.forward(promise);
    ASSERT_TRUE(future.wait_for(0ms) == std::future_status::ready);
    EXPECT_EQ(*future.get(), 42);
}

TEST(ReceivedTest, can_forward_error_to_std_promise) {
    Received<int> result;
    auto err = std::make_exception_ptr(std::runtime_error("stuff happened"));
    result.set_error(err);
    std::promise<int> promise;
    auto future = promise.get_future();
    result.forward(promise);
    ASSERT_TRUE(future.wait_for(0ms) == std::future_status::ready);
    EXPECT_THROW(future.get(), std::runtime_error);
}

TEST(ReceivedTest, can_forward_nothing_as_error_to_std_promise) {
    Received<int> result;
    result.set_done();
    std::promise<int> promise;
    auto future = promise.get_future();
    result.forward(promise);
    ASSERT_TRUE(future.wait_for(0ms) == std::future_status::ready);
    EXPECT_THROW(future.get(), vespalib::coro::UnavailableResultException);
}

struct MyReceiver {
    std::unique_ptr<int> value;
    std::exception_ptr error;
    bool done;
    MyReceiver() : value(), error(), done(false) {}
    void set_value(std::unique_ptr<int> v) { value = std::move(v); }
    void set_error(std::exception_ptr err) { error = err; }
    void set_done() { done = true; }
    ~MyReceiver();
};
MyReceiver::~MyReceiver() = default;
static_assert(vespalib::coro::receiver_of<MyReceiver,std::unique_ptr<int>>);

TEST(ReceivedTest, can_forward_value_to_receiver) {
    Received<std::unique_ptr<int>> result;
    result.set_value(std::make_unique<int>(42));
    MyReceiver r;
    result.forward(r);
    EXPECT_EQ(*r.value, 42);
    EXPECT_FALSE(r.error);
    EXPECT_FALSE(r.done);
}

TEST(ReceivedTest, can_forward_error_to_receiver) {
    Received<std::unique_ptr<int>> result;
    auto err = std::make_exception_ptr(std::runtime_error("stuff happened"));
    result.set_error(err);
    MyReceiver r;
    result.forward(r);
    EXPECT_EQ(r.error, err);
    EXPECT_TRUE(r.value.get() == nullptr);
    EXPECT_FALSE(r.done);
}

TEST(ReceivedTest, can_forward_nothing_to_receiver) {
    Received<std::unique_ptr<int>> result;
    result.set_done();
    MyReceiver r;
    result.forward(r);
    EXPECT_TRUE(r.done);
    EXPECT_FALSE(r.error);
    EXPECT_TRUE(r.value.get() == nullptr);
}

TEST(ReceivedTest, can_forward_itself_to_lvalue_lambda_callback) {
    Received<std::unique_ptr<int>> result;
    result.set_value(std::make_unique<int>(42));
    Received<std::unique_ptr<int>> other_result;
    auto callback = [&](auto res){ other_result = std::move(res); };
    result.forward(callback);
    EXPECT_EQ(*other_result.get_value(), 42);
}

TEST(ReceivedTest, can_forward_itself_to_rvalue_lambda_callback) {
    Received<std::unique_ptr<int>> result;
    result.set_value(std::make_unique<int>(42));
    Received<std::unique_ptr<int>> other_result;
    result.forward([&](auto res){ other_result = std::move(res); });
    EXPECT_EQ(*other_result.get_value(), 42);
}

GTEST_MAIN_RUN_ALL_TESTS()
