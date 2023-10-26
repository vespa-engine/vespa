// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/exception.h>
#include "iindexenvironment.h"
#include "parameter.h"
#include "parameterdescriptions.h"

namespace search::fef {

/**
 * This class is a validator for a string parameter list given an index environment and a set of parameter descriptions.
 * The string parameter list is valid if it is matched with one of the parameter descriptions.
 * In case of a match the string parameter list is converted into a parameter list with type information.
 */
class ParameterValidator {
public:
    using string = vespalib::string;
    using StringVector = std::vector<string>;
    /**
     * This class contains the result after running a validation for a given parameter description.
     * If the result is valid the parameter description matched the string parameter list
     * and the converted parameter list is stored.
     * If the result is not valid the reason for this is found in the error string.
     */
    class Result {
    private:
        ParameterList _params;
        size_t        _tag;
        string        _errorStr;
        bool          _valid;

    public:
        /**
         * Creates a result for the parameter description with the given tag.
         */
        Result(size_t tag = 0);
        Result(const Result &);
        Result & operator=(const Result &);
        Result(Result &&) = default;
        Result & operator=(Result &&) = default;
        ~Result();
        Result & addParameter(const Parameter & param) { _params.push_back(param); return *this; }
        Result & setError(vespalib::stringref str) {
            _errorStr = str;
            _params.clear();
            _valid = false;
            return *this;
        }
        const ParameterList & getParameters() const { return _params; }
        size_t getTag() const { return _tag; }
        const string & getError() const { return _errorStr; }
        bool valid() const { return _valid; }
    };
private:
    const IIndexEnvironment        & _indexEnv;
    const StringVector             & _params;
    const ParameterDescriptions    & _descs;

    void validateField(ParameterType::Enum type, ParameterDataTypeSet dataTypeSet, ParameterCollection::Enum collection,
                       size_t i, Result & result);
    void validateNumber(ParameterType::Enum type, size_t i, Result & result);
    Result validate(const ParameterDescriptions::Description & desc);

public:
    /**
     * Creates a new validator.
     *
     * @param indexEnv the index environment used to lookup fields.
     * @param params   the string parameter list to validate.
     * @param descs    the parameter descriptions to use during validation.
     */
    ParameterValidator(const IIndexEnvironment & indexEnv,
                       const StringVector & params,
                       const ParameterDescriptions & descs);
    /**
     * Runs the validator and returns the result.
     * The result object for the first parameter description that match is returned.
     * In case of no match the result object for the first registered parameter description is returned.
     */
    Result validate();
};

}
