// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/protocolrepository.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

TEST_SETUP(Test);

class TestProtocol : public IProtocol {
private:
    const string _name;

public:

    TestProtocol(const string &name)
        : _name(name)
    {
        // empty
    }

    const string &
    getName() const override
    {
        return _name;
    }

    IRoutingPolicy::UP
    createPolicy(const string &name, const string &param) const override
    {
        (void)name;
        (void)param;
        throw std::exception();
    }

    Blob
    encode(const vespalib::Version &version, const Routable &routable) const override
    {
        (void)version;
        (void)routable;
        throw std::exception();
    }

    Routable::UP
    decode(const vespalib::Version &version, BlobRef data) const override
    {
        (void)version;
        (void)data;
        throw std::exception();
    }
    bool requireSequencing() const override { return false; }
};

int
Test::Main()
{
    TEST_INIT("protocolrepository_test");

    ProtocolRepository repo;
    IProtocol::SP prev;
    prev = repo.putProtocol(IProtocol::SP(new TestProtocol("foo")));
    ASSERT_TRUE(prev.get() == NULL);

    IRoutingPolicy::SP policy = repo.getRoutingPolicy("foo", "bar", "baz");
    prev = repo.putProtocol(IProtocol::SP(new TestProtocol("foo")));
    ASSERT_TRUE(prev.get() != NULL);
    ASSERT_NOT_EQUAL(prev.get(), repo.getProtocol("foo"));

    policy = repo.getRoutingPolicy("foo", "bar", "baz");
    ASSERT_TRUE(policy.get() == NULL);

    TEST_DONE();
}
