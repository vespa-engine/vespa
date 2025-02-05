// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_length_info.h"
#include <atomic>
#include <algorithm>
#include <cstdint>

namespace search::index {
/**
 * Class used to calculate average field length, with a bias towards
 * the latest field lengths when max_num_samples samples have been reached.
 */
class FieldLengthCalculator {
    std::atomic<double>   _average_field_length;
    std::atomic<double>   _average_element_length;
    std::atomic<uint32_t> _num_samples; // Capped by _max_num_samples
    uint32_t              _max_num_samples;
    double                _average_elements;

    static double calc_average_elements(double average_field_length, double average_element_length,
                                        uint32_t num_samples) {
        return (num_samples == 0) ? 0.0 : average_field_length / average_element_length;
    }

    static double calc_decay(double old_value, double new_value, uint32_t num_samples) {
        return (old_value * (num_samples - 1) + new_value) / num_samples;
    }

public:
    FieldLengthCalculator()
        : FieldLengthCalculator(0.0, 0.0, 0) {
    }

    FieldLengthCalculator(double average_field_length, double average_element_length, uint32_t num_samples,
                          uint32_t max_num_samples = 100000)
        : _average_field_length(average_field_length),
          _average_element_length(average_element_length),
          _num_samples(std::min(num_samples, max_num_samples)),
          _max_num_samples(max_num_samples),
          _average_elements(calc_average_elements(average_field_length, average_element_length, num_samples)) {
    }

    FieldLengthCalculator(const FieldLengthInfo& info, uint32_t max_num_samples = 100000)
        : _average_field_length(info.get_average_field_length()),
          _average_element_length(info.get_average_element_length()),
          _num_samples(std::min(info.get_num_samples(), max_num_samples)),
          _max_num_samples(max_num_samples),
          _average_elements(calc_average_elements(info.get_average_field_length(),
                                                  info.get_average_element_length(),
                                                  info.get_num_samples()))
    {
    }

    double get_average_field_length() const noexcept { return _average_field_length.load(std::memory_order_relaxed); }
    double get_average_element_length() const noexcept { return _average_element_length.load(std::memory_order_relaxed); }
    uint32_t get_num_samples() const noexcept { return _num_samples.load(std::memory_order_relaxed); }
    uint32_t get_max_num_samples() const noexcept { return _max_num_samples; }

    FieldLengthInfo get_info() const noexcept {
        return FieldLengthInfo(get_average_field_length(), get_average_element_length(), get_num_samples());
    }

    void add_field_length(uint32_t field_length, uint32_t elements) noexcept {
        auto num_samples = get_num_samples();
        if (num_samples < _max_num_samples) {
            ++num_samples;
            _num_samples.store(num_samples, std::memory_order_relaxed);
        }
        auto average_field_length = calc_decay(_average_field_length.load(std::memory_order_relaxed),
                                               field_length, num_samples);
        _average_field_length.store(average_field_length, std::memory_order_relaxed);
        _average_elements = calc_decay(_average_elements, elements, num_samples);
        _average_element_length.store(average_field_length / _average_elements, std::memory_order_relaxed);
    }
};

}
