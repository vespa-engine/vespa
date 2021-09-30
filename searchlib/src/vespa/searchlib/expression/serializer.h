// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search::expression {

class RawResultNode;
class ResultNodeVector;
class ResultNode;

class ResultSerializer
{
public:
    virtual ~ResultSerializer() = default;
    virtual ResultSerializer & putResult(const RawResultNode & value) = 0;
    virtual ResultSerializer & putResult(const ResultNodeVector & value) = 0;
    virtual void proxyPut(const ResultNode & value) = 0;
};

class ResultDeserializer
{
public:
    virtual ~ResultDeserializer() = default;
    virtual ResultDeserializer & getResult(RawResultNode & value) = 0;
    virtual ResultDeserializer & getResult(ResultNodeVector & value) = 0;
    virtual void proxyGet(const ResultNode & value) = 0;
};

}
