// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey

import javax.inject.Singleton

import com.yahoo.container.di.config.{ResolveDependencyException, RestApiContext}
import com.yahoo.container.jaxrs.annotation.Component
import org.glassfish.hk2.api.{ServiceHandle, Injectee, InjectionResolver}

/**
 * Resolves jdisc container components for jersey 2 components.
 * Similar to Gjoran's ComponentGraphProvider for jersey 1.
 * @author tonytv
 */
@Singleton //jersey2 requirement: InjectionResolvers must be in the Singleton scope
class ComponentGraphProvider(injectables: Traversable[RestApiContext.Injectable]) extends InjectionResolver[Component] {
  override def resolve(injectee: Injectee, root: ServiceHandle[_]): AnyRef = {
    val wantedClass = injectee.getRequiredType match {
      case c: Class[_] => c
      case unsupported => throw new UnsupportedOperationException("Only classes are supported, got " + unsupported)
    }

    val componentsWithMatchingType = injectables.filter{ injectable =>
        wantedClass.isInstance(injectable.instance) }

    val injectionDescription =
      s"class '$wantedClass' to inject into Jersey resource/provider '${injectee.getInjecteeClass}')"

    if (componentsWithMatchingType.size > 1)
      throw new ResolveDependencyException(s"Multiple components found of $injectionDescription: " +
        componentsWithMatchingType.map(_.id).mkString(","))

    componentsWithMatchingType.headOption.map(_.instance).getOrElse {
      throw new ResolveDependencyException(s"Could not find a component of $injectionDescription.")
    }
  }

  override def isMethodParameterIndicator: Boolean = true
  override def isConstructorParameterIndicator: Boolean = true
}
