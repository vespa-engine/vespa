// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/document_frequency.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/table.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/util/string_hash.h>
#include <limits>
#include <optional>

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
 * Returns the significance based on the given document frequency
 *
 * @param doc_freq The document frequency
 * @return         The significance.
 */
feature_t calculate_legacy_significance(search::fef::DocumentFrequency doc_freq);

/**
 * Returns the significance based on max known frequency of the term
 *
 * @param  termData Data for the term
 * @return          The significance.
 */
feature_t calculate_legacy_significance(const search::fef::ITermData& termData);

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

std::optional<search::fef::DocumentFrequency>
lookup_document_frequency(const search::fef::IQueryEnvironment& env, const search::fef::ITermData& term);

std::optional<search::fef::DocumentFrequency>
lookup_document_frequency(const search::fef::IQueryEnvironment& env, uint32_t termId);

}
