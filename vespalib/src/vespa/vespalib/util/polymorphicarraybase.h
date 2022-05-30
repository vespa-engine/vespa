// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#pragma once

namespace vespalib {

class IArrayBase {
public:
    virtual ~IArrayBase() {}
    virtual void resize(size_t sz) = 0;
    virtual void reserve(size_t sz) = 0;
    virtual void clear() = 0;
    virtual IArrayBase *clone() const = 0;
    virtual size_t size() const = 0;
    bool empty() const { return size() == 0; }
};

}
