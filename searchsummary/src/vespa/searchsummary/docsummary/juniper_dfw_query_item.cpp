// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_dfw_query_item.h"
#include "juniper_dfw_explicit_item_data.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

namespace search::docsummary {

vespalib::stringref
JuniperDFWQueryItem::get_index() const
{
    return _si != nullptr ? _si->getIndexName() : _data->_index;
}

int
JuniperDFWQueryItem::get_weight() const
{
    return _si != nullptr ? _si->GetWeight().percent() : _data->_weight;
}

juniper::ItemCreator
JuniperDFWQueryItem::get_creator() const
{
    return _si != nullptr ? _si->getCreator() : juniper::ItemCreator::CREA_ORIG;
}

}
