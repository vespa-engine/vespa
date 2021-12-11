// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

struct Hit {
    uint32_t docid;
    double distance;
    Hit() noexcept : docid(0u), distance(0.0) {}
    Hit(int id, double dist) : docid(id), distance(dist) {}
};
