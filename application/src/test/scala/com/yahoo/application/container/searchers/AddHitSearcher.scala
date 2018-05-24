// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.searchers

import com.yahoo.search.{Searcher, Result, Query}
import com.yahoo.search.searchchain.Execution
import com.yahoo.search.result.Hit


class AddHitSearcher extends Searcher {

  override def search(query: Query, execution: Execution) : Result = {
    val result = execution.search(query)
    result.hits().add(dummyHit)

    result
  }

  private def dummyHit = {
    val hit = new Hit("dummy")
    hit.setField("title", getId.getName)
    hit
  }
}
