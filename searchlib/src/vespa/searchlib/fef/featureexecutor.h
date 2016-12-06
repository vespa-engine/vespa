// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/vespalib/util/linkedptr.h>
#include "handle.h"
#include "matchdata.h"
#include <cassert>
#include <memory>

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
    class SharedInputs {
        std::vector<FeatureHandle> _inputs;
    public:
        SharedInputs() : _inputs() {}
        void add(FeatureHandle handle) { _inputs.push_back(handle); }
        size_t size() const { return _inputs.size(); }
        FeatureHandle operator[](size_t idx) const { return _inputs[idx]; }
    };

    class Inputs {
        SharedInputs    *_inputs;
        uint32_t         _offset;
        uint32_t         _size;
        const MatchData *_md;
    public:
        Inputs() : _inputs(nullptr), _offset(0), _size(0), _md(nullptr) {}
        void bind(const MatchData &md) { _md = &md; }
        feature_t get_number(size_t idx) const {
            return *_md->resolveFeature((*this)[idx]);
        }
        vespalib::eval::Value::CREF get_object(size_t idx) const {
            return *_md->resolve_object_feature((*this)[idx]);
        }
        void bind(SharedInputs &inputs) {
            _inputs = &inputs;
            _offset = _inputs->size();
            _size = 0;
        }
        void add(FeatureHandle handle) {
            assert(_inputs != nullptr);
            assert(_inputs->size() == (_offset + _size));
            _inputs->add(handle);
            ++_size;
        }
        bool empty() const { return (_size == 0); }
        size_t size() const { return _size; }
        FeatureHandle operator[](size_t idx) const {
            assert(idx < _size);
            return (*_inputs)[_offset + idx];
        }
    };

    class Outputs {
        FeatureHandle _begin;
        FeatureHandle _end;
        MatchData    *_md;
    public:
        Outputs() : _begin(IllegalHandle), _end(IllegalHandle), _md(nullptr) {}
        void bind(MatchData &md) { _md = &md; }
        void set_number(size_t idx, feature_t value) {
            *_md->resolveFeature((*this)[idx]) = value;
        }
        void set_object(size_t idx, vespalib::eval::Value::CREF value) {
            *_md->resolve_object_feature((*this)[idx]) = value;
        }
        void add(FeatureHandle handle) {
            if (_begin == IllegalHandle) {
                _begin = handle;
                _end = (_begin + 1);
            } else if (handle == _end) {
                ++_end;
            } else {
                assert(handle == _end);
            }
        }
        bool empty() const { return (_end == _begin); }
        size_t size() const { return (_end - _begin); }
        FeatureHandle operator[](size_t idx) const {
            assert(idx < (_end - _begin));
            return (_begin + idx);
        }
    };

private:
    FeatureExecutor(const FeatureExecutor &);
    FeatureExecutor &operator=(const FeatureExecutor &);

    Inputs  _inputs;
    Outputs _outputs;

public:
    /**
     * Create a feature executor that has not yet been bound to neither
     * inputs nor outputs.
     **/
    FeatureExecutor();

    /**
     * Bind shared external storage to this feature executor. The
     * shared storage will be used to store the handle of feature
     * inputs. This function must be called before starting to add
     * inputs.
     *
     * @param shared_inputs shared store for input feature handles
     **/
    void bind_shared_inputs(SharedInputs &shared_inputs) { _inputs.bind(shared_inputs); }

    // Bind inputs and outputs directly to the underlying match data
    // to be able to hide the fact that input and output values are
    // stored in a match data object from the executor itself.
    void bind_match_data(MatchData &md) {
        _inputs.bind(md);
        _outputs.bind(md);
    }

    /**
     * Add an input to this feature executor. All inputs must be added
     * before this object is added to the feature execution manager.
     *
     * @param handle the feature handle of the input to add
     **/
    void addInput(FeatureHandle handle) { _inputs.add(handle); }
    virtual void inputs_done() {} // needed for feature decorators

    /**
     * Access the input features for this executor. Use {@link
     * MatchData#resolveFeature} to resolve these handles.
     *
     * @return const view of input features
     **/
    const Inputs &inputs() const { return _inputs; }

    /**
     * Assign a feature handle to the next unbound output feature.
     * This method will be invoked by the @ref FeatureExecutionManager
     * when new feature executors are added. It may also be used for
     * testing, but should not be invoked directly from application
     * code. Note that this method must be invoked exactly the number
     * of times indicated by the @ref getNumOutputs method.
     *
     * @param handle feature handle to be assigned to the next unbound
     *               output feature.
     **/
    void bindOutput(FeatureHandle handle) { _outputs.add(handle); }
    virtual void outputs_done() {} // needed for feature decorators

    /**
     * Access the output features for this executor. Use {@link
     * MatchData#resolveFeature} to resolve these handles.
     *
     * @return const view of output features
     **/
    const Outputs &outputs() const { return _outputs; }

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
     * @param data data storage
     **/
    virtual void execute(MatchData &data) = 0;

    /**
     * Virtual destructor to allow subclassing.
     **/
    virtual ~FeatureExecutor() {}
};

} // namespace fef
} // namespace search


//  LocalWords:  param
