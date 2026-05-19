// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "data_pond.h"

#include <string>

namespace search::queryeval::test {

/**
 * Writes the records of a data pond to file.
 */
void write_data_pond_to_file(const std::string& file_path, const DataPond& data_pond);

/**
 * Reads records from file and adds them to the data pond.
 */
void read_file_into_data_pond(const std::string& file_path, DataPond& data_pond);

}

