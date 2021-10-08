// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/juniper/rewriter.h>
#include <string>

class FakeRewriter: public juniper::IRewriter
{
public:
    FakeRewriter() : _name() {}
    const char* Name() const override;
    juniper::RewriteHandle* Rewrite(uint32_t langid, const char* term) override;
    juniper::RewriteHandle* Rewrite(uint32_t langid, const char* term, size_t length) override;
    const char* NextTerm(juniper::RewriteHandle* exp, size_t& length) override;
private:
    std::string _name;
};

