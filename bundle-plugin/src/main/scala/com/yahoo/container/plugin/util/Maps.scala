// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

import collection.mutable.MultiMap

/**
 * @author  tonytv
 */
object Maps {
  def combine[K, V](map1 : Map[K, V], map2 : Map[K, V])(f : (V, V) => V) : Map[K, V] = {
    def logicError : V = throw new RuntimeException("Logic error.")
    def combineValues(key : K) = key -> f(map1.getOrElse(key, logicError), map2.getOrElse(key, logicError))

    val keysInBoth = map1.keySet intersect map2.keySet
    def notInBoth = !keysInBoth.contains(_ : K)

    map1.filterKeys(notInBoth) ++ map2.filterKeys(notInBoth) ++ keysInBoth.map(combineValues)
  }
}
