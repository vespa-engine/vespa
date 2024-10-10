// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_wire_service.h"

namespace proton {

DummyWireService::DummyWireService() = default;
DummyWireService::~DummyWireService() = default;

void
DummyWireService::addAttribute(AttributeMetrics&, const std::string&)
{
}

void
DummyWireService::removeAttribute(AttributeMetrics&, const std::string&)
{
}

void
DummyWireService::cleanAttributes(AttributeMetrics&)
{
}

void
DummyWireService::add_index_field(IndexMetrics&, const std::string&)
{
}

void
DummyWireService::remove_index_field(IndexMetrics&, const std::string&)
{
}

void
DummyWireService::clean_index_fields(IndexMetrics&)
{
}

void
DummyWireService::addRankProfile(DocumentDBTaggedMetrics&, const std::string&, size_t)
{
}

void DummyWireService::cleanRankProfiles(DocumentDBTaggedMetrics&)
{
}

}
