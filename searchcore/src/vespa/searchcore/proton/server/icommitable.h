// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Interface for anyone that needs to commit.
 **/
class ICommitable {
public:
    virtual void commit() = 0;
    virtual void commitAndWait() = 0;
protected:
    virtual ~ICommitable() { }
};

}
