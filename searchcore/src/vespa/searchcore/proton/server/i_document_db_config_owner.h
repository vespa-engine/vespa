// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton {

class DocumentDBConfig;

class IDocumentDBConfigOwner
{
public:
    virtual ~IDocumentDBConfigOwner() { }
    virtual void reconfigure(const std::shared_ptr<DocumentDBConfig> & config) = 0;
};

} // namespace proton
