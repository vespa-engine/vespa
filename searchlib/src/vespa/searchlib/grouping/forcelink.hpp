// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

void forcelink_file_searchlib_grouping_groupandcollectengine();
void forcelink_file_searchlib_grouping_groupingengine();
void forcelink_file_searchlib_grouping_groupengine();

void forcelink_searchlib_grouping() {
    forcelink_file_searchlib_grouping_groupandcollectengine();
    forcelink_file_searchlib_grouping_groupingengine();
    forcelink_file_searchlib_grouping_groupengine();
}

