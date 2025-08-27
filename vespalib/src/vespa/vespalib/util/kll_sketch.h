// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vector>

namespace vespalib {

/*
 * Wraps datasketches-cpp KllSketch.
 *
 * Approximate sketch.
 *
 * Supported java datatypes: double, float, long, item(generic).
 */
class KLLSketch  {
    struct Impl;
    std::unique_ptr<Impl> _impl{};

public:
    KLLSketch();
    ~KLLSketch();
    KLLSketch(const KLLSketch&);
    KLLSketch& operator=(const KLLSketch&);
    KLLSketch(KLLSketch&&) noexcept;
    KLLSketch& operator=(KLLSketch&&) noexcept;

    void update(double item);

    void merge(const KLLSketch& other);

    [[nodiscard]] bool is_empty() const;

    [[nodiscard]] double get_quantile(double rank) const;

    [[nodiscard]] std::vector<uint8_t> serialize() const;

    static KLLSketch deserialize(const std::vector<uint8_t>& bytes);
};

} // namespace
