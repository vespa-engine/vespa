// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace vespalib::eval { struct ConstantValue; }

namespace search::fef {

class Properties;
class FieldInfo;
class ITableManager;
class OnnxModel;

/**
 * Abstract view of index related information available to the
 * framework.
 **/
class IIndexEnvironment
{
public:
    using string = std::string;
    /**
     * This enum defines the different motivations the framework has
     * for configuring a feature blueprint. RANK means the feature is
     * needed for ranking calculations in normal operation. DUMP means
     * the feature is needed to perform a feature dump. VERIFY_SETUP
     * means that we are just trying to figure out if this setup is
     * valid; the feature will never actually be executed.
     **/
    enum FeatureMotivation {
        UNKNOWN = 0,
        RANK = 1,
        DUMP = 2,
        VERIFY_SETUP = 3
    };

    /**
     * Obtain the set of properties associated with this index
     * environment.
     *
     * @return properties
     **/
    virtual const Properties &getProperties() const = 0;

    /**
     * Obtain the number of fields
     *
     * @return number of fields
     **/
    virtual uint32_t getNumFields() const = 0;

    /**
     * Obtain a field by using the field enumeration. The legal range
     * for id is [0, getNumFields>. If id is out of bounds, 0 will be
     * returned.
     *
     * @return information about a single field
     **/
    virtual const FieldInfo *getField(uint32_t id) const = 0;

    /**
     * Obtain a field by using the field name. If the field is not
     * found, 0 will be returned.
     *
     * @return information about a single field
     **/
    virtual const FieldInfo *getFieldByName(const string &name) const = 0;

    /**
     * Obtain the table manager associated with this index environment.
     *
     * @return table manager
     **/
    virtual const ITableManager &getTableManager() const = 0;

    /**
     * Obtain the current motivation behind feature setup. The
     * motivation is typically that we want to set up features for
     * ranking or dumping. In some cases we are also setting things up
     * just to verify that it is possible.
     *
     * @return current feature motivation
     **/
    virtual FeatureMotivation getFeatureMotivation() const = 0;

    /**
     * Hint about the nature of the feature blueprints we are about to
     * configure. This method provides additional information that may
     * be useful when interpreting hints about future field and
     * attribute access.
     *
     * @param motivation the motivation behind the feature blueprints
     *                   the framework is about to configure.
     **/
    virtual void hintFeatureMotivation(FeatureMotivation motivation) const = 0;

    /**
     * Returns a constant rank value with the given name or null ptr if no such value exists.
     */
    virtual std::unique_ptr<vespalib::eval::ConstantValue> getConstantValue(const std::string &name) const = 0;

    /**
     * Returns the ranking expression with the given name or empty string if not found.
     **/
    virtual std::string getRankingExpression(const std::string &name) const = 0;

    /**
     * Get configuration for the given onnx model.
     **/
    virtual const OnnxModel *getOnnxModel(const std::string &name) const = 0;

    virtual uint32_t getDistributionKey() const = 0;

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~IIndexEnvironment() = default;
};

}

