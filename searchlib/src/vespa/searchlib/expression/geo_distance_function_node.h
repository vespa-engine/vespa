// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"

namespace search::expression {

/**
 * Implements geo distance from a position attribute to a point.
 *
 * If the attribute is of type `array<position>`, the distance is computed for each position in the array,
 * and the minimum distance is returned.
 */
class GeoDistanceFunctionNode : public MultiArgFunctionNode {
public:
    enum class Unit : uint8_t {
        KM = 0,
        MILES = 1
    };

private:
    Unit _unit;

public:
    GeoDistanceFunctionNode() noexcept;
    ~GeoDistanceFunctionNode() override;
    GeoDistanceFunctionNode(const GeoDistanceFunctionNode&);
    GeoDistanceFunctionNode& operator=(const GeoDistanceFunctionNode&);

    DECLARE_EXPRESSIONNODE(GeoDistanceFunctionNode);

    // for testing
    explicit GeoDistanceFunctionNode(Unit unit);

private:
    void onPrepareResult() override;
    void onExecute() const override;
    vespalib::Serializer& onSerialize(vespalib::Serializer& os) const override;
    vespalib::Deserializer& onDeserialize(vespalib::Deserializer& is) override;
};

} // namespace search::expression
