// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"

#include <vespa/vespalib/util/transient_vector_snapshot.h>

#include <memory>

namespace search::predicate {
class ISaver;
}

namespace search {

/**
 * Class for saving a predicate attribute.
 */
class PredicateAttributeSaver : public AttributeSaver {
public:
    using MinFeatureVectorSnapshot = vespalib::TransientVectorSnapshot<uint8_t>;
    using IntervalRangeVectorSnapshot = vespalib::TransientVectorSnapshot<uint16_t>;

private:
    uint32_t                           _version;
    std::unique_ptr<predicate::ISaver> _index_saver;
    MinFeatureVectorSnapshot           _min_feature_snapshot;
    IntervalRangeVectorSnapshot        _interval_range_vector_snapshot;
    uint16_t                           _max_interval_range;

public:
    PredicateAttributeSaver(vespalib::GenerationGuard&& guard, const attribute::AttributeHeader& header,
                            uint32_t version, std::unique_ptr<predicate::ISaver> index_saver,
                            MinFeatureVectorSnapshot    min_feature_snashot,
                            IntervalRangeVectorSnapshot interval_range_vector_snapshot, uint16_t max_interval_range);
    ~PredicateAttributeSaver() override;
    bool onSave(IAttributeSaveTarget& save_target) override;
};

} // namespace search
