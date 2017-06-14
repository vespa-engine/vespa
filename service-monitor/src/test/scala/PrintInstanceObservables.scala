// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.config.subscription.ConfigSourceSet
import com.yahoo.vespa.applicationmodel.{ApplicationInstance, ApplicationInstanceReference}
import com.yahoo.vespa.service.monitor.config.InstancesObservables

import org.json4s.native.Serialization
import org.json4s.{CustomKeySerializer, NoTypeHints}


/**
 * @author tonytv
 */
object PrintInstanceObservables {
  def main(args: Array[String]): Unit = {
    val sourceSet = new ConfigSourceSet("tcp/test1-node:19070")

    val observables = new InstancesObservables(sourceSet)

    observables.servicesPerInstance.subscribe(prettyPrint _)
    observables.slobroksPerInstance.subscribe(println(_))
    val subscription = observables.connect()

    Thread.sleep(100000)
    subscription.unsubscribe()
  }

  private def prettyPrint(map: Map[ApplicationInstanceReference, ApplicationInstance[Void]]): Unit = {
    implicit val formats = Serialization.formats(NoTypeHints) +
      new CustomKeySerializer[Object](formats => ({case string => ???} , { case ref: AnyRef => ref.toString }))

    println(Serialization.writePretty(map))
  }
}
