// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * SLIME: 'Schema-Less Interface/Model/Exchange'. Slime is a way to
 * handle schema-less structured data to be used as part of interfaces
 * between components (RPC signatures), internal models
 * (config/parameters) and data exchange between components
 * (documents). The goal for Slime is to be flexible and lightweight
 * and at the same time limit the extra overhead in space and time
 * compared to schema-oriented approaches like protocol buffers and
 * avro. The data model is inspired by JSON and associative arrays
 * typically used in programming languages with dynamic typing.
 **/
@ExportPackage
package com.yahoo.slime;

import com.yahoo.osgi.annotation.ExportPackage;
