// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/juniper/query_item.h>

namespace search { class SimpleQueryStackDumpIterator; }

namespace search::docsummary {

struct JuniperDFWExplicitItemData;

/**
 * This struct is used to point to the traversal state located on
 * the stack of the IQuery Traverse method. This is needed because
 * the Traverse method is const.
 **/
class JuniperDFWQueryItem : public juniper::QueryItem
{
    search::SimpleQueryStackDumpIterator *_si;
    const JuniperDFWExplicitItemData *_data;
public:
    JuniperDFWQueryItem() : _si(nullptr), _data(nullptr) {}
    ~JuniperDFWQueryItem() override = default;
    explicit JuniperDFWQueryItem(search::SimpleQueryStackDumpIterator *si) : _si(si), _data(nullptr) {}
    explicit JuniperDFWQueryItem(const JuniperDFWExplicitItemData *data) : _si(nullptr), _data(data) {}
    JuniperDFWQueryItem(const QueryItem&) = delete;
    JuniperDFWQueryItem& operator= (const QueryItem&) = delete;

    vespalib::stringref get_index() const override;
    int get_weight() const override;
    juniper::ItemCreator get_creator() const override;
};

}
