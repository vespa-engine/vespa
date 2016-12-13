// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/vespalib/util/linkedptr.h>
#include "handle.h"
#include "matchdata.h"
#include <cassert>
#include <memory>
#include <vespa/vespalib/util/array.h>

namespace search {
namespace fef {

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
        vespalib::ConstArrayRef<const NumberOrObject *> _inputs;
    public:
        Inputs() : _inputs(nullptr, 0) {}
        void bind(vespalib::ConstArrayRef<const NumberOrObject *> inputs) { _inputs = inputs; }
        feature_t get_number(size_t idx) const {
            return _inputs[idx]->as_number;
        }
        vespalib::eval::Value::CREF get_object(size_t idx) const {
            return _inputs[idx]->as_object;
        }
        const NumberOrObject *get_raw(size_t idx) const {
            return _inputs[idx];
        }
        size_t size() const { return _inputs.size(); }
    };

    class Outputs {
        vespalib::ArrayRef<NumberOrObject> _outputs;
    public:
        Outputs() : _outputs(nullptr, 0) {}
        void bind(vespalib::ArrayRef<NumberOrObject> outputs) { _outputs = outputs; }
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
        size_t size() const { return _outputs.size(); }
    };

private:
    FeatureExecutor(const FeatureExecutor &);
    FeatureExecutor &operator=(const FeatureExecutor &);

    Inputs  _inputs;
    Outputs _outputs;

protected:
    virtual void handle_bind_inputs(vespalib::ConstArrayRef<const NumberOrObject *> inputs);
    virtual void handle_bind_outputs(vespalib::ArrayRef<NumberOrObject> outputs);
    virtual void handle_bind_match_data(MatchData &md);

public:
    /**
     * Create a feature executor that has not yet been bound to neither
     * inputs nor outputs.
     **/
    FeatureExecutor();

    // bind order per executor: inputs, outputs, match_data
    void bind_inputs(vespalib::ConstArrayRef<const NumberOrObject *> inputs);
    void bind_outputs(vespalib::ArrayRef<NumberOrObject> outputs);
    void bind_match_data(MatchData &md);

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
     * Execute this feature executor on the given data.
     *
     * @param docid the local document id being evaluated
     **/
    virtual void execute(uint32_t docId) = 0;

    /**
     * Virtual destructor to allow subclassing.
     **/
    virtual ~FeatureExecutor() {}
};

} // namespace fef
} // namespace search


//  LocalWords:  param
