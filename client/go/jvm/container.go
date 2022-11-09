// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

type Container interface {
	ServiceName() string
	ConfigId() string
	ArgForMain() string
	Exec()
}
