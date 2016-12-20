// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace fnet {
/**
 * Interface implemented by objects that want to perform synchronous
 * connect initiated by an external thread.
 **/
class ExtConnectable {
protected:
    virtual ~ExtConnectable() {}
public:
    virtual void ext_connect() = 0;
};

}
