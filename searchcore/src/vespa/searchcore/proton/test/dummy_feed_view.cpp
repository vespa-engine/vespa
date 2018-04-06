// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_feed_view.h"

namespace proton::test {

DummyFeedView::DummyFeedView()
    : _docTypeRepo()
{
}

DummyFeedView::DummyFeedView(const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo)
    : _docTypeRepo(docTypeRepo)
{
}

DummyFeedView::~DummyFeedView() = default;

}
