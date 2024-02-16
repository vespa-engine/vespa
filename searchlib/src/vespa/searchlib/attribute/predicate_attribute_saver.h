// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include <vespa/vespalib/stllike/allocator.h>

namespace search::predicate { class ISaver; }

namespace search {

/**
 * Class for saving a predicate attribute.
 */
class PredicateAttributeSaver : public AttributeSaver
{
public:
    using GenerationHandler = vespalib::GenerationHandler;
    using MinFeatureVector = std::vector<uint8_t, vespalib::allocator_large<uint8_t>>;
    using IntervalRangeVector = std::vector<uint16_t, vespalib::allocator_large<uint16_t>>;

private:
    uint32_t                           _version;
    std::unique_ptr<predicate::ISaver> _index_saver;
    MinFeatureVector                   _min_feature;
    IntervalRangeVector                _interval_range_vector;
    uint16_t                           _max_interval_range;
public:
    PredicateAttributeSaver(GenerationHandler::Guard&& guard,
                            const attribute::AttributeHeader& header,
                            uint32_t version,
                            std::unique_ptr<predicate::ISaver> index_saver,
                            MinFeatureVector min_feature,
                            IntervalRangeVector interval_range_vector,
                            uint16_t max_interval_range);
    ~PredicateAttributeSaver() override;
    bool onSave(IAttributeSaveTarget& save_target) override;
};

}
