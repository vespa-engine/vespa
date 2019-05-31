// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <atomic>
#include <algorithm>

namespace search::index {

/**
 * Class used to calculate average field length, with a bias towards
 * the latest field lengths when MAX_NUM_SAMPLES samples have been reached.
 */
class FieldLengthCalculator {
    std::atomic<double>   _average_field_length;
    uint32_t              _num_samples;     // Capped by _max_num_samples
    static constexpr uint32_t    MAX_NUM_SAMPLES = 100000;

public:
    FieldLengthCalculator()
        : FieldLengthCalculator(0.0, 0)
    {
    }

    FieldLengthCalculator(double average_field_length, uint32_t num_samples)
        : _average_field_length(average_field_length),
          _num_samples(std::min(num_samples, MAX_NUM_SAMPLES))
    {
    }

    double get_average_field_length() const { return _average_field_length.load(std::memory_order_relaxed); }
    uint32_t get_num_samples() const { return _num_samples; }
    static constexpr uint32_t get_max_num_samples() { return MAX_NUM_SAMPLES; }
    
    void add_field_length(uint32_t field_length) {
        if (_num_samples < MAX_NUM_SAMPLES) {
            ++_num_samples;
        }
        _average_field_length.store((_average_field_length.load(std::memory_order_relaxed) * (_num_samples - 1) + field_length) / _num_samples, std::memory_order_relaxed);
    }
};

}
