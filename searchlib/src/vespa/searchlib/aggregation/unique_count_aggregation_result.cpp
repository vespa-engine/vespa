// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "unique_count_aggregation_result.h"
#include "aggregation.hpp"
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <algorithm>
#include <cmath>
#include <iterator>

namespace search::aggregation {

namespace {
// Precision-10 HLL++ bias data, shared with the container estimator.
constexpr double raw_estimates[] = {
    738.1256, 750.4234, 763.1064, 775.4732, 788.4636, 801.0644, 814.488, 827.9654,
    841.0832, 854.7864, 868.1992, 882.2176, 896.5228, 910.1716, 924.7752, 938.899,
    953.6126, 968.6492, 982.9474, 998.5214, 1013.1064, 1028.6364, 1044.2468, 1059.4588,
    1075.3832, 1091.0584, 1106.8606, 1123.3868, 1139.5062, 1156.1862, 1172.463, 1189.339,
    1206.1936, 1223.1292, 1240.1854, 1257.2908, 1275.3324, 1292.8518, 1310.5204, 1328.4854,
    1345.9318, 1364.552, 1381.4658, 1400.4256, 1419.849, 1438.152, 1456.8956, 1474.8792,
    1494.118, 1513.62, 1532.5132, 1551.9322, 1570.7726, 1590.6086, 1610.5332, 1630.5918,
    1650.4294, 1669.7662, 1690.4106, 1710.7338, 1730.9012, 1750.4486, 1770.1556, 1791.6338,
    1812.7312, 1833.6264, 1853.9526, 1874.8742, 1896.8326, 1918.1966, 1939.5594, 1961.07,
    1983.037, 2003.1804, 2026.071, 2047.4884, 2070.0848, 2091.2944, 2114.333, 2135.9626,
    2158.2902, 2181.0814, 2202.0334, 2224.4832, 2246.39, 2269.7202, 2292.1714, 2314.2358,
    2338.9346, 2360.891, 2384.0264, 2408.3834, 2430.1544, 2454.8684, 2476.9896, 2501.4368,
    2522.8702, 2548.0408, 2570.6738, 2593.5208, 2617.0158, 2640.2302, 2664.0962, 2687.4986,
    2714.2588, 2735.3914, 2759.6244, 2781.8378, 2808.0072, 2830.6516, 2856.2454, 2877.2136,
    2903.4546, 2926.785, 2951.2294, 2976.468, 3000.867, 3023.6508, 3049.91, 3073.5984,
    3098.162, 3121.5564, 3146.2328, 3170.9484, 3195.5902, 3221.3346, 3242.7032, 3271.6112,
    3296.5546, 3317.7376, 3345.072, 3369.9518, 3394.326, 3418.1818, 3444.6926, 3469.086,
    3494.2754, 3517.8698, 3544.248, 3565.3768, 3588.7234, 3616.979, 3643.7504, 3668.6812,
    3695.72, 3719.7392, 3742.6224, 3770.4456, 3795.6602, 3819.9058, 3844.002, 3869.517,
    3895.6824, 3920.8622, 3947.1364, 3973.985, 3995.4772, 4021.62, 4046.628, 4074.65,
    4096.2256, 4121.831, 4146.6406, 4173.276, 4195.0744, 4223.9696, 4251.3708, 4272.9966,
    4300.8046, 4326.302, 4353.1248, 4374.312, 4403.0322, 4426.819, 4450.0598, 4478.5206,
    4504.8116, 4528.8928, 4553.9584, 4578.8712, 4603.8384, 4632.3872, 4655.5128, 4675.821,
    4704.6222, 4731.9862, 4755.4174, 4781.2628, 4804.332, 4832.3048, 4862.8752, 4883.4148,
    4906.9544, 4935.3516, 4954.3532, 4984.0248, 5011.217, 5035.3258, 5057.3672, 5084.1828,
};
constexpr double bias_estimates[] = {
    737.1256, 724.4234, 711.1064, 698.4732, 685.4636, 673.0644, 660.488, 647.9654,
    636.0832, 623.7864, 612.1992, 600.2176, 588.5228, 577.1716, 565.7752, 554.899,
    543.6126, 532.6492, 521.9474, 511.5214, 501.1064, 490.6364, 480.2468, 470.4588,
    460.3832, 451.0584, 440.8606, 431.3868, 422.5062, 413.1862, 404.463, 395.339,
    386.1936, 378.1292, 369.1854, 361.2908, 353.3324, 344.8518, 337.5204, 329.4854,
    321.9318, 314.552, 306.4658, 299.4256, 292.849, 286.152, 278.8956, 271.8792,
    265.118, 258.62, 252.5132, 245.9322, 239.7726, 233.6086, 227.5332, 222.5918,
    216.4294, 210.7662, 205.4106, 199.7338, 194.9012, 188.4486, 183.1556, 178.6338,
    173.7312, 169.6264, 163.9526, 159.8742, 155.8326, 151.1966, 147.5594, 143.07,
    140.037, 134.1804, 131.071, 127.4884, 124.0848, 120.2944, 117.333, 112.9626,
    110.2902, 107.0814, 103.0334, 99.4832000000001, 96.3899999999999, 93.7202000000002, 90.1714000000002, 87.2357999999999,
    85.9346, 82.8910000000001, 80.0264000000002, 78.3834000000002, 75.1543999999999, 73.8683999999998, 70.9895999999999, 69.4367999999999,
    64.8701999999998, 65.0408000000002, 61.6738, 59.5207999999998, 57.0158000000001, 54.2302, 53.0962, 50.4985999999999,
    52.2588000000001, 47.3914, 45.6244000000002, 42.8377999999998, 43.0072, 40.6516000000001, 40.2453999999998, 35.2136,
    36.4546, 33.7849999999999, 33.2294000000002, 32.4679999999998, 30.8670000000002, 28.6507999999999, 28.9099999999999, 27.5983999999999,
    26.1619999999998, 24.5563999999999, 23.2328000000002, 21.9484000000002, 21.5902000000001, 21.3346000000001, 17.7031999999999, 20.6111999999998,
    19.5545999999999, 15.7375999999999, 17.0720000000001, 16.9517999999998, 15.326, 13.1817999999998, 14.6925999999999, 13.0859999999998,
    13.2754, 10.8697999999999, 11.248, 7.3768, 4.72339999999986, 7.97899999999981, 8.7503999999999, 7.68119999999999,
    9.7199999999998, 7.73919999999998, 5.6224000000002, 7.44560000000001, 6.6601999999998, 5.9058, 4.00199999999995, 4.51699999999983,
    4.68240000000014, 3.86220000000003, 5.13639999999987, 5.98500000000013, 2.47719999999981, 2.61999999999989, 1.62800000000016, 4.65000000000009,
    0.225599999999758, 0.831000000000131, -0.359400000000278, 1.27599999999984, -2.92559999999958, -0.0303999999996449, 2.37079999999969, -2.0033999999996,
    0.804600000000391, 0.30199999999968, 1.1247999999996, -2.6880000000001, 0.0321999999996478, -1.18099999999959, -3.9402, -1.47940000000017,
    -0.188400000000001, -2.10720000000038, -2.04159999999956, -3.12880000000041, -4.16160000000036, -0.612799999999879, -3.48719999999958, -8.17900000000009,
    -5.37780000000021, -4.01379999999972, -5.58259999999973, -5.73719999999958, -7.66799999999967, -5.69520000000011, -1.1247999999996, -5.58520000000044,
    -8.04560000000038, -4.64840000000004, -11.6468000000004, -7.97519999999986, -5.78300000000036, -7.67420000000038, -10.6328000000003, -9.81720000000041,
};

double estimate_bias(double estimate) {
    auto next = std::lower_bound(std::begin(raw_estimates), std::end(raw_estimates), estimate);
    if (next == std::begin(raw_estimates)) return bias_estimates[0];
    if (next == std::end(raw_estimates)) return bias_estimates[std::size(bias_estimates) - 1];
    size_t index = next - std::begin(raw_estimates);
    double fraction = (estimate - raw_estimates[index - 1]) / (*next - raw_estimates[index - 1]);
    return bias_estimates[index - 1] + fraction * (bias_estimates[index] - bias_estimates[index - 1]);
}

int64_t estimate_count(const Sketch<10, uint32_t>& sketch) {
    if (sketch.getClassId() == SparseSketch<10, uint32_t>::classId)
        return static_cast<const SparseSketch<10, uint32_t>&>(sketch).getSize();
    const auto& normal = static_cast<const NormalSketch<10, uint32_t>&>(sketch);
    constexpr double bucket_count = 1024;
    double sum = 0;
    size_t zero_buckets = 0;
    for (auto bucket : normal.bucket) {
        sum += std::ldexp(1.0, -bucket);
        zero_buckets += bucket == 0;
    }
    double estimate = (0.7213 / (1 + 1.079 / bucket_count)) * bucket_count * bucket_count / sum;
    if (estimate <= 5 * bucket_count) estimate -= estimate_bias(estimate);
    if (zero_buckets > 0) {
        double linear_estimate = bucket_count * std::log(bucket_count / zero_buckets);
        if (linear_estimate <= 900) estimate = linear_estimate;
    }
    return static_cast<int64_t>(std::floor(estimate + 0.5));
}
} // namespace


IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, UniqueCountAggregationResult, ExpressionCountAggregationResult);

UniqueCountAggregationResult::UniqueCountAggregationResult() : ExpressionCountAggregationResult(1000) {}
UniqueCountAggregationResult::~UniqueCountAggregationResult() = default;

const UniqueCountAggregationResult::ResultNode& UniqueCountAggregationResult::onGetRank() const {
    if (!_estimate_valid) {
        _estimated_count.set(estimate_count(getSketch()));
        _estimate_valid = true;
    }
    return _estimated_count;
}

void UniqueCountAggregationResult::onMerge(const AggregationResult& result) {
    ExpressionCountAggregationResult::onMerge(result);
    _estimate_valid = false;
}

void UniqueCountAggregationResult::onReset() {
    ExpressionCountAggregationResult::onReset();
    _estimate_valid = false;
}

vespalib::Deserializer& UniqueCountAggregationResult::onDeserialize(vespalib::Deserializer& stream) {
    ExpressionCountAggregationResult::onDeserialize(stream);
    _estimate_valid = false;
    return stream;
}

void UniqueCountAggregationResult::onAggregate(const ResultNode& result) {
    _estimate_valid = false;
    if (auto* values = dynamic_cast<const expression::ResultNodeVector*>(&result)) {
        for (size_t i = 0; i < values->size(); ++i) {
            const auto& value = values->get(i);
            if (auto* string = dynamic_cast<const expression::StringResultNode*>(&value);
                string && string->get().empty()) continue;
            aggregate_value(value);
        }
    } else {
        if (auto* string = dynamic_cast<const expression::StringResultNode*>(&result);
            string && string->get().empty()) return;
        aggregate_value(result);
    }
}

} // namespace search::aggregation

void forcelink_file_searchlib_aggregation_unique_count_aggregation_result() {}
