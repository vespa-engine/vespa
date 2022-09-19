// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumfieldspec.h"
#include <vespa/searchsummary/docsummary/slime_filler_filter.h>

namespace vsm {

DocsumFieldSpec::FieldIdentifier::FieldIdentifier() :
    _id(StringFieldIdTMap::npos),
    _path()
{ }

DocsumFieldSpec::FieldIdentifier::FieldIdentifier(FieldIdT id, FieldPath path) :
    _id(id),
    _path(std::move(path))
{ }

DocsumFieldSpec::FieldIdentifier::FieldIdentifier(FieldIdentifier &&) noexcept = default;
DocsumFieldSpec::FieldIdentifier & DocsumFieldSpec::FieldIdentifier::operator=(FieldIdentifier &&) noexcept = default;
DocsumFieldSpec::FieldIdentifier::~FieldIdentifier() = default;

DocsumFieldSpec::DocsumFieldSpec() :
    _struct_or_multivalue(false),
    _command(VsmsummaryConfig::Fieldmap::Command::NONE),
    _outputField(),
    _inputFields(),
    _filter()
{ }

DocsumFieldSpec::DocsumFieldSpec(VsmsummaryConfig::Fieldmap::Command command) :
    _struct_or_multivalue(false),
    _command(command),
    _outputField(),
    _inputFields(),
    _filter()
{ }

DocsumFieldSpec::DocsumFieldSpec(DocsumFieldSpec&&) noexcept = default;

DocsumFieldSpec::~DocsumFieldSpec() = default;

void
DocsumFieldSpec::set_filter(std::unique_ptr<search::docsummary::SlimeFillerFilter> filter)
{
    _filter = std::move(filter);
}

const search::docsummary::SlimeFillerFilter *
DocsumFieldSpec::get_filter() const noexcept
{
    return _filter.get();
}

}
