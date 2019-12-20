// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

struct Hit {
    uint32_t docid;
    double distance;
    Hit() : docid(0u), distance(0.0) {}
    Hit(int id, double dist) : docid(id), distance(dist) {}
};
