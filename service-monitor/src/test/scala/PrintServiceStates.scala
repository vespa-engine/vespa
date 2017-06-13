// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.config.subscription.ConfigSourceSet
import com.yahoo.vespa.config.GenerationCounter
import com.yahoo.vespa.applicationmodel.HostName
import com.yahoo.vespa.service.monitor.SlobrokAndConfigIntersector

/**
 * Quick hack to just see some results.
 *
 * @author bakksjo
 */
object PrintServiceStates {
  def main(args: Array[String]): Unit = {
    val intersector = new SlobrokAndConfigIntersector(
      new ConfigSourceSet("tcp/test1-node:19070"),
      multiTenantConfigServerHostNames = Set("config-server1", "config-server2").map(s => new HostName(s)),
      new GenerationCounter {
        override def increment = ???
        override def get = 1L
      })
    Thread.sleep(100000)
    intersector.deconstruct()
  }
}
