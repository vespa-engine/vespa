// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

template <typename FltType = float>
struct L2DistCalc {
    vespalib::hwaccelrated::IAccelrated::UP _hw;

    L2DistCalc() : _hw(vespalib::hwaccelrated::IAccelrated::getAccelrator()) {}

    using Arr = vespalib::ArrayRef<FltType>;
    using ConstArr = vespalib::ConstArrayRef<FltType>;
    
    double product(ConstArr v1, ConstArr v2) {
        const FltType *p1 = v1.begin();
        const FltType *p2 = v2.begin();
        return _hw->dotProduct(p1, p2, v1.size());
    }
    double l2sq(ConstArr vector) {
        const FltType *v = vector.begin();
        return _hw->dotProduct(v, v, vector.size());
    }
    double l2sq_dist(ConstArr v1, ConstArr v2, Arr tmp) {
        for (size_t i = 0; i < v1.size(); ++i) {
            tmp[i] = (v1[i] - v2[i]);
        }
        return l2sq(tmp);
    }
    double l2sq_dist(ConstArr v1, ConstArr v2) {
        std::vector<FltType> tmp;
        tmp.resize(v1.size());
        return l2sq_dist(v1, v2, Arr(tmp));
    }
};

static L2DistCalc l2distCalc;
