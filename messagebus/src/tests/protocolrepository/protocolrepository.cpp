// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/protocolrepository.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace mbus;

class TestProtocol : public IProtocol {
private:
    const string _name;

public:

    TestProtocol(const string &name) noexcept
        : _name(name)
    { }

    const string & getName() const override { return _name; }

    IRoutingPolicy::UP createPolicy(const string &, const string &) const override {
        throw std::exception();
    }

    Blob encode(const vespalib::Version &, const Routable &) const override {
        throw std::exception();
    }

    Routable::UP decode(const vespalib::Version &, BlobRef ) const override {
        throw std::exception();
    }
};

TEST("protocolrepository_test") {

    ProtocolRepository repo;
    IProtocol::SP prev;
    prev = repo.putProtocol(std::make_shared<TestProtocol>("foo"));
    ASSERT_FALSE(prev);

    IRoutingPolicy::SP policy = repo.getRoutingPolicy("foo", "bar", "baz");
    prev = repo.putProtocol(std::make_shared<TestProtocol>("foo"));
    ASSERT_TRUE(prev);
    ASSERT_NOT_EQUAL(prev.get(), repo.getProtocol("foo"));

    policy = repo.getRoutingPolicy("foo", "bar", "baz");
    ASSERT_FALSE(policy);
}

TEST_MAIN() { TEST_RUN_ALL(); }
