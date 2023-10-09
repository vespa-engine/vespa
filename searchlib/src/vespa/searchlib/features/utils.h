// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/table.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/util/string_hash.h>
#include <limits>

namespace search::features::util {

/**
 * Maximum feature value
 */
const feature_t FEATURE_MAX = std::numeric_limits<feature_t>::max();

/**
 * Minimum feature value
 */
const feature_t FEATURE_MIN = -std::numeric_limits<feature_t>::max();

using ConstCharPtr = const char *;

/**
 * Converts the given string to a numeric value.
 *
 * @param str The string to convert.
 * @return The numeric value.
 */
template <typename T>
T strToNum(vespalib::stringref str);

template <typename T>
feature_t getAsFeature(T value) __attribute__((__always_inline__));

/**
 * Converts the given value to a feature value.
 *
 * @param value The value to convert.
 * @return The feature value.
 */
template <typename T>
inline feature_t getAsFeature(T value)
{
    return static_cast<feature_t>(value);
}

/**
 * Specialization for const char *.
 *
 * @param value The string to convert.
 * @return The feature value.
 */
template <>
inline feature_t getAsFeature<ConstCharPtr>(ConstCharPtr value) {
    return vespalib::hash2d(value, strlen(value));
}

/**
 * Specialization for a string value.
 *
 * @param value The string to convert.
 * @return The feature value.
 */
template <>
inline feature_t getAsFeature<vespalib::stringref>(vespalib::stringref value) {
    return vespalib::hash2d(value);
}


/**
 * This method inputs a value to cap to the range [capFloor, capCeil] and then normalize this
 * value to the unit range [0, 1].
 *
 * @param val      The value to unit normalize.
 * @param capFloor The minimum value of the cap range.
 * @param capCeil  The maximum value of the cap range.
 * @return The unit normalized value.
 */
template <typename T>
T unitNormalize(const T &val, const T &capFloor, const T &capCeil)
{
    return (std::max(capFloor, std::min(capCeil, val)) - capFloor) / (capCeil - capFloor);
}

/**
 * Returns the normalized strength with which the given term is connected to the previous term in the query.
 * Uses the property map of the query environment to lookup this data.
 *
 * @param env      The query environment.
 * @param termId   The term id.
 * @param fallback The value to return if the connectedness was not found in the property map.
 * @return         The connectedness.
 */
feature_t lookupConnectedness(const search::fef::IQueryEnvironment & env, uint32_t termId, feature_t fallback = 0.1f);

/**
 * Returns the normalized strength with which the given current term is connected to the given previous term.
 * Uses the property map of the query environment to lookup this data.
 *
 * @param env          The query environment.
 * @param currUniqueId Unique id of the current term.
 * @param prevUniqueId Unique id of the previous term.
 * @param fallback     The value to return if the connectedness was not found in the property map.
 * @return             The connectedness between the current term and previous term.
 */
feature_t lookupConnectedness(const search::fef::IQueryEnvironment & env,
                              uint32_t currUniqueId, uint32_t prevUniqueId, feature_t fallback = 0.1f);

/**
 * Returns the significance of the given term.
 * Uses the property map of the query environment to lookup this data.
 *
 * @param env          The query environment.
 * @param term         The term data.
 * @param fallback     The value to return if the significance was not found in the property map.
 * @return             The significance.
 */
feature_t lookupSignificance(const search::fef::IQueryEnvironment& env, const search::fef::ITermData& term, feature_t fallback);

/**
 * Returns the significance of the given term.
 * Uses the property map of the query environment to lookup this data.
 *
 * @param env          The query environment.
 * @param termId       The term id.
 * @param fallback     The value to return if the significance was not found in the property map.
 * @return             The significance.
 */
feature_t lookupSignificance(const search::fef::IQueryEnvironment & env, uint32_t termId, feature_t fallback = 0.0f);

/**
 * Returns the Robertson-Sparck-Jones weight based on the given document count
 * (number of documents containing the term) and the number of documents in the corpus.
 * This weight is a variant of inverse document frequency.
 */
double getRobertsonSparckJonesWeight(double docCount, double docsInCorpus);

/**
 * Returns the significance based on the given scaled number of documents containing the term.
 *
 * @param docFreq The scaled number of documents containing the term.
 * @return        The significance.
 */
feature_t getSignificance(double docFreq);

/**
 * Returns the significance based on max known frequency of the term
 *
 * @param  termData Data for the term
 * @return          The significance.
 */
feature_t getSignificance(const search::fef::ITermData &termData);

/**
 * Lookups a table by using the properties and the table manager in the given index environment.
 * The table name is found by looking up the following properties and using the first found:
 * 'featureName.table.fieldName', 'featureName.table'.
 * The table name 'fallback' is used if no properties are found.
 *
 * @param env         the index environment.
 * @param featureName the name of the feature.
 * @param table       the table to be used by the feature.
 * @param fieldName   the name of the field we want to lookup a table for.
 * @param fallback    the actual name of the table to use if we do not find any properties.
 * @return the table pointer or NULL if not found.
 **/
const search::fef::Table *
lookupTable(const search::fef::IIndexEnvironment & env, const vespalib::string & featureName,
            const vespalib::string & table, const vespalib::string & fieldName, const vespalib::string & fallback);

/**
 * Obtain query information for a term/field combination.
 *
 * @return query information for a term/field combination, or 0 if not found
 * @param env query environment
 * @param termId the term id
 * @param fieldId the field id
 **/
inline const search::fef::ITermFieldData *
getTermFieldData(const search::fef::IQueryEnvironment &env, uint32_t termId, uint32_t fieldId) {
    const search::fef::ITermData *td = env.getTerm(termId);
    return (td == nullptr) ? nullptr : td->lookupField(fieldId);
}

/**
 * Obtain the match handle for the given term within the given field.
 *
 * @return match handle, or IllegalHandle if not found
 * @param env query environment
 * @param termId the term id
 * @param fieldId the field id
 **/
inline search::fef::TermFieldHandle
getTermFieldHandle(const search::fef::IQueryEnvironment &env, uint32_t termId, uint32_t fieldId) {
    const search::fef::ITermFieldData *tfd = getTermFieldData(env, termId, fieldId);
    return (tfd == nullptr) ? search::fef::IllegalHandle : tfd->getHandle();
}

/**
 * Obtain the term annotated with the given label. This function will
 * reverse map label to unique id and then traverse the query
 * environment trying to locate the term with the appropriate unique
 * id. If no such term can be found, 0 will be returned.
 *
 * @return term with given label, or 0 if not found
 * @param env query environment
 * @param label query item label
 **/
const search::fef::ITermData *
getTermByLabel(const search::fef::IQueryEnvironment &env, const vespalib::string &label);

}
