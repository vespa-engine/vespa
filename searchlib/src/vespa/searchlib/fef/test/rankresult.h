// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace search::fef::test {

class RankResult {
public:
    /**
     * Convenience typedefs.
     */
    using RankScores = std::map<vespalib::string, feature_t>;

public:
    /**
     * Constructs a new rank result.
     */
    RankResult();

    /**
     * Adds a score for the given feature name.
     *
     * @param featureName The name of the feature.
     * @param score       The score of that feature.
     * @return            This, to allow chaining.
     */
    RankResult &addScore(const vespalib::string & featureName, feature_t score);

    /**
     * Returns the score of a given feature.
     *
     * @param featureName The name of the feature.
     * @return            The score of that feature.
     */
    feature_t getScore(const vespalib::string & featureName) const;

    /**
     * Implements equality operator.
     *
     * @param rhs The result to compare to.
     * @return    Whether or not this is equal to the other.
     */
    bool operator==(const RankResult & rhs) const;

    /**
     * Returns whether or not this rank result contains another.
     *
     * @param rhs The result to see if this contains.
     * @return Whether or not this contains the other.
     */
    bool includes(const RankResult & rhs) const;

    /**
     * Clears the content of this map.
     *
     * @return This, to allow chaining.
     */
    RankResult &clear();

    /**
     * Fills the given vector with the key strings of this.
     *
     * @param ret The vector to fill.
     * @return    Reference to the 'ret' param.
     */
    std::vector<vespalib::string> &getKeys(std::vector<vespalib::string> &ret);

    /**
     * Creates and returns a vector with the key strings of this.
     *
     * @return List of all key strings.
     */
    std::vector<vespalib::string> getKeys();

    /**
     * Sets the epsilon used when comparing this rank result to another.
     *
     * @param epsilon The new epsilon.
     * @return        This, to allow chaining.
     */
    RankResult &setEpsilon(double epsilon);

    /**
     * Returns the epsilon used when comparing this rank result to another.
     *
     * @return The epsilon.
     */
    double getEpsilon() const;

    /**
     * Implements streaming operator.
     *
     * @param os  The stream to write to.
     * @param rhs The result to write.
     * @return    The stream, to allow chaining.
     */
    friend std::ostream & operator<<(std::ostream & os, const RankResult & rhs);

private:
    RankScores _rankScores;
    double     _epsilon;
};

}
