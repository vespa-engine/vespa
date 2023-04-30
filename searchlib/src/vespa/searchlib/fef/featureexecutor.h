// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "matchdata.h"
#include "number_or_object.h"
#include <vespa/vespalib/util/arrayref.h>

namespace search::fef {

class FeatureExecutor;

/**
 * A LazyValue is a reference to a value that can be calculated by a
 * FeatureExecutor when needed. Actual Values and FeatureExecutors are
 * owned by a RankProgram. LazyValue objects are used when resolving
 * value dependencies between FeatureExecutors inside a RankProgram
 * and when a client wants to access values from the outside,
 * typically during ranking and when producing summary features.
 **/
class LazyValue {
private:
    const NumberOrObject *_value;
    FeatureExecutor *_executor;
public:
    explicit LazyValue(const NumberOrObject *value) : _value(value), _executor(nullptr) {}
    LazyValue(const NumberOrObject *value, FeatureExecutor *executor)
        : _value(value), _executor(executor) {}
    bool is_const() const { return (_executor == nullptr); }
    bool is_same(const LazyValue &rhs) const {
        return ((_value == rhs._value) && (_executor == rhs._executor));
    }
    inline double as_number(uint32_t docid) const;
    inline vespalib::eval::Value::CREF as_object(uint32_t docid) const;
};

/**
 * A feature executor is a general component that calculates one or
 * more feature values. It may take multiple features as input. A
 * feature executor may also use term match data as input, or whatever
 * it has access to regarding the index.
 **/
class FeatureExecutor
{
public:
    class Inputs {
        uint32_t _docid;
        vespalib::ConstArrayRef<LazyValue> _inputs;
    public:
        Inputs() : _docid(-1), _inputs() {}
        void set_docid(uint32_t docid) { _docid = docid; }
        uint32_t get_docid() const { return _docid; }
        void bind(vespalib::ConstArrayRef<LazyValue> inputs) { _inputs = inputs; }
        inline feature_t get_number(size_t idx) const;
        inline vespalib::eval::Value::CREF get_object(size_t idx) const;
        size_t size() const { return _inputs.size(); }
    };

    class Outputs {
    public:
        using OutputArray = vespalib::ArrayRef<NumberOrObject>;
        Outputs() : _outputs() {}
        void bind(OutputArray  outputs) { _outputs = outputs; }
        void set_number(size_t idx, feature_t value) {
            _outputs[idx].as_number = value;
        }
        void set_object(size_t idx, vespalib::eval::Value::CREF value) {
            _outputs[idx].as_object = value;
        }
        feature_t *get_number_ptr(size_t idx) {
            return &_outputs[idx].as_number;
        }
        vespalib::eval::Value::CREF *get_object_ptr(size_t idx) {
            return &_outputs[idx].as_object;
        }
        feature_t get_number(size_t idx) const {
            return _outputs[idx].as_number;
        }
        vespalib::eval::Value::CREF get_object(size_t idx) const {
            return _outputs[idx].as_object;
        }
        const NumberOrObject *get_raw(size_t idx) const {
            return &_outputs[idx];
        }
        OutputArray get_bound() const {
            return _outputs;
        }
        size_t size() const { return _outputs.size(); }
    private:
        vespalib::ArrayRef<NumberOrObject> _outputs;
    };

private:
    Inputs  _inputs;
    Outputs _outputs;

protected:
    virtual void handle_bind_inputs(vespalib::ConstArrayRef<LazyValue> inputs);
    virtual void handle_bind_outputs(vespalib::ArrayRef<NumberOrObject> outputs);
    virtual void handle_bind_match_data(const MatchData &md);

    /**
     * Execute this feature executor for the given document.
     *
     * @param docid the local document id being evaluated
     **/
    virtual void execute(uint32_t docId) = 0;

public:
    /**
     * Create a feature executor that has not yet been bound to neither
     * inputs nor outputs.
     **/
    FeatureExecutor();
    FeatureExecutor(const FeatureExecutor &) = delete;
    FeatureExecutor &operator=(const FeatureExecutor &) = delete;

    /**
    * Obtain the fully qualified name of the concrete class for this object.
    *
    * @return fully qualified class name
    **/
    vespalib::string getClassName() const;

    // bind order per executor: inputs, outputs, match_data
    void bind_inputs(vespalib::ConstArrayRef<LazyValue> inputs);
    void bind_outputs(vespalib::ArrayRef<NumberOrObject> outputs);
    void bind_match_data(const MatchData &md);

    const Inputs &inputs() const { return _inputs; }
    const Outputs &outputs() const { return _outputs; }
    Outputs &outputs() { return _outputs; }

    /**
     * Check if this feature executor is pure. A feature executor
     * claiming to be pure must satisfy the requirement that its
     * output feature values only depend on the values of its input
     * features (in other words: if the input features does not change
     * in value, neither does the outputs). This method is implemented
     * to return false by default, but may be overridden by feature
     * executors that are pure. Whether a feature executor is pure or
     * not may be used by the framework to optimize feature
     * execution. It is always safe to let this method return false,
     * but letting pure executors return true may increase
     * performance.
     *
     * @return true if this feature executor is pure
     **/
    virtual bool isPure();

    /**
     * Make sure this executor has been executed for the given
     * document.
     *
     * @param docid the local document id being evaluated
     **/
    void lazy_execute(uint32_t docid) {
        if (_inputs.get_docid() != docid) {
            _inputs.set_docid(docid);
            execute(docid);
        }
    }

    /**
     * Virtual destructor to allow subclassing.
     **/
    virtual ~FeatureExecutor() {}
};

double LazyValue::as_number(uint32_t docid) const {
    if (_executor != nullptr) {
        _executor->lazy_execute(docid);
    }
    return _value->as_number;
}

vespalib::eval::Value::CREF LazyValue::as_object(uint32_t docid) const {
    if (_executor != nullptr) {
        _executor->lazy_execute(docid);
    }
    return _value->as_object;
}

feature_t FeatureExecutor::Inputs::get_number(size_t idx) const {
    return _inputs[idx].as_number(_docid);
}

vespalib::eval::Value::CREF FeatureExecutor::Inputs::get_object(size_t idx) const {
    return _inputs[idx].as_object(_docid);
}

}

//  LocalWords:  param
