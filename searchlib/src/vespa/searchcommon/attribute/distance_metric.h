// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::attribute {

enum class DistanceMetric : uint8_t { Euclidean, Angular, GeoDegrees, InnerProduct, Hamming, PrenormalizedAngular, Dotproduct };

}
