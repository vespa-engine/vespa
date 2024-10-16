// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_wire_service.h"

namespace proton {

DummyWireService::DummyWireService() = default;
DummyWireService::~DummyWireService() = default;

void
DummyWireService::set_attributes(AttributeMetrics&, std::vector<std::string>)
{
}

void
DummyWireService::set_index_fields(IndexMetrics&, std::vector<std::string>)
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
