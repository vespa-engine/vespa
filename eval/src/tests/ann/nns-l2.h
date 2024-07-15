// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <cstring>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>

template <typename T, size_t VLEN>
static double hw_l2_sq_dist(const T * af, const T * bf, size_t sz)
{
    constexpr const size_t OpsPerV = VLEN/sizeof(T);
    typedef T V __attribute__ ((vector_size (VLEN), aligned(VLEN)));

    const V * a = reinterpret_cast<const V *>(af);
    const V * b = reinterpret_cast<const V *>(bf);

    V tmp_diff;
    V tmp_squa;
    V tmp_sum;
    memset(&tmp_sum, 0, sizeof(tmp_sum));

    const size_t numOps = sz/OpsPerV;
    for (size_t i = 0; i < numOps; ++i) {
        tmp_diff = a[i] - b[i];
        tmp_squa = tmp_diff * tmp_diff;
        tmp_sum += tmp_squa;
    }
    double sum = 0;
    for (size_t i = 0; i < OpsPerV; ++i) {
        sum += tmp_sum[i];
    }
    return sum;
}

template <typename FltType = float>
struct L2DistCalc {
    const vespalib::hwaccelerated::IAccelerated & _hw;

    L2DistCalc() : _hw(vespalib::hwaccelerated::IAccelerated::getAccelerator()) {}

    using Arr = vespalib::ArrayRef<FltType>;
    using ConstArr = vespalib::ConstArrayRef<FltType>;

    double product(const FltType *v1, const FltType *v2, size_t sz) {
        return _hw.dotProduct(v1, v2, sz);
    }
    double product(ConstArr v1, ConstArr v2) {
        const FltType *p1 = v1.data();
        const FltType *p2 = v2.data();
        return _hw.dotProduct(p1, p2, v1.size());
    }
    double l2sq(ConstArr vector) {
        const FltType *v = vector.data();
        return _hw.dotProduct(v, v, vector.size());
    }
    double l2sq_dist(ConstArr v1, ConstArr v2, Arr tmp) {
        for (size_t i = 0; i < v1.size(); ++i) {
            tmp[i] = (v1[i] - v2[i]);
        }
        return l2sq(tmp);
    }
    double l2sq_dist(ConstArr v1, ConstArr v2) {
        return hw_l2_sq_dist<FltType, 32>(v1.data(), v2.data(), v1.size());
    }
};

static L2DistCalc l2distCalc;
