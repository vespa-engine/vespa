// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryenvironment.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <memory>
#include <set>

namespace search::fef::test {

class MatchDataBuilder {
public:
    struct MyElement {
        int32_t  weight;
        uint32_t length;
        MyElement(int32_t w, uint32_t l) : weight(w), length(l) {}
    };
    struct MyField {
        uint32_t fieldLength;
        std::vector<MyElement> elements;
        MyField() : fieldLength(0), elements() {}
        MyElement &getElement(uint32_t eid) {
            while (elements.size() <= eid) {
                elements.push_back(MyElement(0, 0));
            }
            return elements[eid];
        }
        int32_t getWeight(uint32_t eid) const {
            if (eid < elements.size()) {
                return elements[eid].weight;
            }
            return 1;
        }
        uint32_t getLength(uint32_t eid) const {
            if (eid < elements.size()) {
                return elements[eid].length;
            }
            return fieldLength;
        }
    };
    struct Position {
        uint32_t pos;
        uint32_t eid;
        Position(uint32_t p, uint32_t e) : pos(p), eid(e) {}
        bool operator<(const Position &other) const {
            if (eid == other.eid) {
                return pos < other.pos;
            }
            return eid < other.eid;
        }
    };

    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<MatchDataBuilder>;
    using IndexData = std::map<uint32_t, MyField>;      // index data per field
    using Positions = std::set<Position>;      // match information for a single term and field combination
    using FieldPositions = std::map<uint32_t, Positions>; // position information per field for a single term
    using TermMap = std::map<uint32_t, FieldPositions>;        // maps term id to map of position information per field

public:
    /**
     * Constructs a new match data builder. This is what you should use when building match data since there are alot of
     * interconnections that must be set up correctly.
     *
     * @param queryEnv The query environment to build for.
     * @param data     The match data to build in.
     */
    MatchDataBuilder(QueryEnvironment &queryEnv, MatchData &data);
    ~MatchDataBuilder();

    /**
     * Returns the term field match data that corresponds to a given
     * term id and field id combination. This goes by way of the query
     * environment to find the handler of the given term id.
     *
     * @param termId The id of the term whose data to return.
     * @param fieldId The id of the field whose data to return.
     * @return       The corresponding term match data.
     */
    TermFieldMatchData *getTermFieldMatchData(uint32_t termId, uint32_t fieldId);

    /**
     * Sets the length of a named field. This will fail if the named field does not exist.
     *
     * @param fieldName The name of the field.
     * @param length    The length to set.
     * @return          Whether or not the field length could be set.
     */
    bool setFieldLength(const vespalib::string &fieldName, uint32_t length);

    /**
     * Adds an element to a named field. This will fail if the named field does not exist.
     *
     * @param fieldName The name of the field.
     * @param weight    The weight of the element.
     * @param length    The length of the element.
     * @return          Whether or not the element could be added.
     */
    bool addElement(const vespalib::string &fieldName, int32_t weight, uint32_t length);

    /**
     * Adds an occurence of a term to the named field, at the given
     * position. This will fail if the named field does not exist. The
     * list of occurences is implemented as a set, so there is no need
     * to add these in order.
     *
     * @param fieldName The name of the field.
     * @param termId    The id of the term to register an occurence for.
     * @param pos       The position of the occurence.
     * @param element   The element containing the occurence.
     * @return          Whether or not the occurence could be added.
     */
    bool addOccurence(const vespalib::string &fieldName, uint32_t termId, uint32_t pos, uint32_t element = 0);

    /**
     * Sets the weight for an attribute match.
     *
     * @param fieldName The name of the field.
     * @param termId    The id of the term to register an occurence for.
     * @param weight    The weight of the match.
     * @return          Whether or not the occurence could be added.
     **/
    bool setWeight(const vespalib::string &fieldName, uint32_t termId, int32_t weight);

    /**
     * Apply the content of this builder to the underlying match data.
     *
     * @param docId the document id
     * @return Whether or not the content of this could be applied.
     */
    bool apply(uint32_t docId);

private:
    MatchDataBuilder(const MatchDataBuilder &);             // hide
    MatchDataBuilder & operator=(const MatchDataBuilder &); // hide

private:
    QueryEnvironment &_queryEnv;
    MatchData        &_data;
    IndexData         _index;
    TermMap           _match;
};

}
