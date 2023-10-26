// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

void forcelink_file_searchlib_aggregation_grouping();
void forcelink_file_searchlib_aggregation_modifiers();
void forcelink_file_searchlib_aggregation_aggregation();
void forcelink_file_searchlib_aggregation_hitlist();
void forcelink_file_searchlib_aggregation_fs4hit();
void forcelink_file_searchlib_aggregation_group();
void forcelink_file_searchlib_aggregation_rawrank();
void forcelink_file_searchlib_aggregation_hit();
void forcelink_file_searchlib_aggregation_vdshit();
void forcelink_file_searchlib_aggregation_hitsaggregationresult();
void forcelink_file_searchlib_aggregation_groupinglevel();

void forcelink_searchlib_aggregation() {
    forcelink_file_searchlib_aggregation_grouping();
    forcelink_file_searchlib_aggregation_modifiers();
    forcelink_file_searchlib_aggregation_aggregation();
    forcelink_file_searchlib_aggregation_hitlist();
    forcelink_file_searchlib_aggregation_fs4hit();
    forcelink_file_searchlib_aggregation_group();
    forcelink_file_searchlib_aggregation_rawrank();
    forcelink_file_searchlib_aggregation_hit();
    forcelink_file_searchlib_aggregation_vdshit();
    forcelink_file_searchlib_aggregation_hitsaggregationresult();
    forcelink_file_searchlib_aggregation_groupinglevel();
}

