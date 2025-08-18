// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace vespalib {

class KLLSketch  {
    struct Impl;
    std::unique_ptr<Impl> _impl{};

public:
    KLLSketch();
    ~KLLSketch();
    KLLSketch(const KLLSketch&);
    KLLSketch& operator=(const KLLSketch&);
    KLLSketch(KLLSketch&&) noexcept = default;
    KLLSketch& operator=(KLLSketch&&) noexcept = default;

    void update(double item);

    void merge(const KLLSketch& other);

    [[nodiscard]] bool is_empty() const;

    [[nodiscard]] double get_quantile(double rank) const;
};

} // namespace
