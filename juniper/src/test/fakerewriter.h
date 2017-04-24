// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/juniper/rewriter.h>
#include <string>

class FakeRewriter: public juniper::IRewriter
{
public:
    FakeRewriter() : _name() {}
    virtual const char* Name() const override;
    virtual juniper::RewriteHandle* Rewrite(uint32_t langid, const char* term) override;
    virtual juniper::RewriteHandle* Rewrite(uint32_t langid, const char* term, size_t length) override;
    virtual const char* NextTerm(juniper::RewriteHandle* exp, size_t& length) override;
private:
    std::string _name;
};

