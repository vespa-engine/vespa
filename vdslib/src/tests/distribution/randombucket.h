// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace RandomBucket{

    void setUserDocScheme(uint64_t num_users, uint64_t locationbits);
    void setDocScheme();
    uint64_t get();
    void setSeed(int seed=-1);
}
