// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search::transactionlog { class Packet; }
namespace search::transactionlog::client {

class RPC
{
public:
enum Result { OK, FULL, ERROR };
};

class Callback {
public:
    virtual ~Callback() = default;
    virtual RPC::Result receive(const Packet & packet) = 0;
    virtual void eof() { }
};

}
