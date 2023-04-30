// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <limits>

namespace search::features {

/**
 * Implements the executor for the foreach feature.
 * Uses a condition and operation template class to perform the computation.
 */
template <typename CO, typename OP>
class ForeachExecutor : public fef::FeatureExecutor {
private:
    CO        _condition;
    OP        _operation;
    uint32_t  _numInputs;

public:
    ForeachExecutor(const CO & condition, uint32_t numInputs);
    void execute(uint32_t docId) override;
};

/**
 * Base class for condition template class.
 **/
class ConditionBase {
protected:
    feature_t _param;
public:
    ConditionBase(feature_t param = 0) : _param(param) {}
};

/**
 * Implements the true condition.
 **/
struct TrueCondition : public ConditionBase {
    bool useValue(feature_t val) { (void) val; return true; }
};

/**
 * Implements the less than condition.
 **/
struct LessThanCondition : public ConditionBase {
    LessThanCondition(feature_t param) : ConditionBase(param) {}
    bool useValue(feature_t val) { return val < _param; }
};

/**
 * Implements the greater than condition.
 **/
struct GreaterThanCondition : public ConditionBase {
    GreaterThanCondition(feature_t param) : ConditionBase(param) {}
    bool useValue(feature_t val) { return val > _param; }
};


/**
 * Base class for operation template class.
 */
class OperationBase {
protected:
    feature_t _result;
public:
    OperationBase() : _result(0) {}
    feature_t getResult() const { return _result; }
};

/**
 * Implements sum operation.
 **/
struct SumOperation : public OperationBase {
    void reset() { _result = 0; }
    void onValue(feature_t val) { _result += val; }
};

/**
 * Implements product operation.
 **/
struct ProductOperation : public OperationBase {
    void reset() { _result = 1; }
    void onValue(feature_t val) { _result *= val; }
};

/**
 * Implements average operation.
 **/
class AverageOperation : public OperationBase {
private:
    uint32_t _numValues;
public:
    AverageOperation() : OperationBase(), _numValues(0) {}
    void reset() { _result = 0; _numValues = 0; }
    void onValue(feature_t val) { _result += val; ++_numValues; }
    feature_t getResult() const { return _numValues != 0 ? _result / _numValues : 0; }
};

/**
 * Implements max operation.
 **/
struct MaxOperation : public OperationBase {
    void reset() { _result = -std::numeric_limits<feature_t>::max(); }
    void onValue(feature_t val) { _result = std::max(val, _result); }
};

/**
 * Implements min operation.
 **/
struct MinOperation : public OperationBase {
    void reset() { _result = std::numeric_limits<feature_t>::max(); }
    void onValue(feature_t val) { _result = std::min(val, _result); }
};

/**
 * Implements count operation.
 **/
struct CountOperation : public OperationBase {
    void reset() { _result = 0; }
    void onValue(feature_t val) { (void) val; _result += 1; }
};


/**
 * Implements the blueprint for the foreach executor.
 */
class ForeachBlueprint : public fef::Blueprint {
private:
    enum Dimension {
        TERMS,
        FIELDS,
        ATTRIBUTES,
        ILLEGAL
    };
    struct ExecutorCreatorBase {
        virtual fef::FeatureExecutor &create(uint32_t numInputs, vespalib::Stash &stash) const = 0;
        virtual ~ExecutorCreatorBase() {}
    };

    Dimension _dimension;
    std::unique_ptr<ExecutorCreatorBase> _executorCreator;
    size_t _num_inputs;

    bool decideDimension(const vespalib::string & param);
    bool decideCondition(const vespalib::string & condition, const vespalib::string & operation);
    template <typename CO>
    bool decideOperation(CO condition, const vespalib::string & operation);
    template <typename CO, typename OP>
    void setExecutorCreator(CO condition);

public:
    ForeachBlueprint();
    ~ForeachBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().string().feature().string().string();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
