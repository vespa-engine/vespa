// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import java.util.List;
import java.util.function.Supplier;

record FunEvalSpec(Supplier<Evaluator> evalSource,
                   List<String> fromQuery,
                   List<MatchFeatureInput> fromMF)
{}
