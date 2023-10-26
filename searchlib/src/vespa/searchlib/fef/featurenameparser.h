// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search::fef {

/**
 * Simple parser used to split feature names into components by the
 * framework.
 **/
class FeatureNameParser
{
public:
    using string = vespalib::string;
    using StringVector = std::vector<string>;
private:
    bool                _valid;
    uint32_t            _endPos;
    string              _baseName;
    StringVector        _parameters;
    string              _output;
    string              _executorName;
    string              _featureName;

public:
    /**
     * The constructor parses the given feature name, splitting it
     * into components. If the given string is not a valid feature
     * name, all components will be empty and the @ref valid method
     * will return false.
     *
     * @param featureName feature name
     **/
    FeatureNameParser(const vespalib::string &featureName);
    ~FeatureNameParser();

    /**
     * Does this object represent a valid feature name?
     *
     * @return true if valid, false if invalid
     **/
    bool valid() const { return _valid; }

    /**
     * Obtain the number of bytes from the original feature name that
     * was successfully parsed. If the feature name was valid, this
     * method will simply return the size of the string given to the
     * constructor. If a parse error occurred, this method will return
     * the index of the offending character in the string given to the
     * constructor.
     *
     * @return number of bytes successfully parsed
     **/
    uint32_t parsedBytes() const { return _endPos; }

    /**
     * Obtain the base name from the parsed feature name.
     *
     * @return base name
     **/
    const string &baseName() const { return _baseName; }

    /**
     * Obtain the parameter list from the parsed feature name.
     *
     * @return parameter list
     **/
    const StringVector &parameters() const { return _parameters; }

    /**
     * Obtain the output name from the parsed feature name.
     *
     * @return output name
     **/
    const string &output() const { return _output; }

    /**
     * Obtain a normalized name for the executor making this
     * feature. This includes the parameter list. The @ref
     * FeatureNameBuilder is used to make this name.
     *
     * @return normalized executor name with parameters
     **/
    const string &executorName() const { return _executorName; }

    /**
     * Obtain a normalized full feature name. The @ref
     * FeatureNameBuilder is used to make this name.
     *
     * @return normalized full feature name
     **/
    const string &featureName() const { return _featureName; }
};

}
