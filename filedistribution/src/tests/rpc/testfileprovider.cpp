// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN

#include "mockfileprovider.h"
#include <vespa/filedistribution/rpc/filedistributorrpc.h>
#include <vespa/frtstream/frtclientstream.h>
#include <iostream>
#include <boost/test/unit_test.hpp>

namespace fd = filedistribution;

using fd::MockFileProvider;

const std::string MockFileProvider::_queueForeverFileReference("queue-forever");

BOOST_AUTO_TEST_CASE(fileDistributionRPCTest) {
    const std::string spec("tcp/localhost:9111");
    fd::FileProvider::SP provider(new fd::MockFileProvider());
    fd::FileDistributorRPC::SP fileDistributorRPC(new fd::FileDistributorRPC(spec, provider));
    fileDistributorRPC->start();

    frtstream::FrtClientStream rpc(spec);
    frtstream::Method method("waitFor");

    std::string path;
    rpc <<method <<"dd";
    rpc >> path;
    BOOST_CHECK_EQUAL("direct/result/path", path);

    rpc <<method <<"0123456789abcdef";
    rpc >> path;
    BOOST_CHECK_EQUAL("downloaded/path/0123456789abcdef", path);
}

//must be run through valgrind
BOOST_AUTO_TEST_CASE(require_that_queued_requests_does_not_leak_memory) {
    const std::string spec("tcp/localhost:9111");
    std::shared_ptr<MockFileProvider> provider(new MockFileProvider());
    fd::FileDistributorRPC::SP fileDistributorRPC(new fd::FileDistributorRPC(spec, provider));
    fileDistributorRPC->start();

    FRT_Supervisor supervisor;

    supervisor.Start();
    FRT_Target *target = supervisor.GetTarget(spec.c_str());

    FRT_RPCRequest* request = supervisor.AllocRPCRequest();
    request->SetMethodName("waitFor");
    request->GetParams()->AddString(MockFileProvider::_queueForeverFileReference.c_str());
    target->InvokeVoid(request);

    provider->_queueForeverBarrier.wait(); //the request has been enqueued.
    fileDistributorRPC.reset();

    target->SubRef();
    supervisor.ShutDown(true);

}

BOOST_AUTO_TEST_CASE(require_that_port_can_be_extracted_from_connection_spec) {
    BOOST_CHECK_EQUAL(9056, fd::FileDistributorRPC::get_port("tcp/host:9056"));
    BOOST_CHECK_EQUAL(9056, fd::FileDistributorRPC::get_port("tcp/9056"));
    BOOST_CHECK_EQUAL(9056, fd::FileDistributorRPC::get_port("9056"));
}
