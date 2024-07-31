// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type ObjectTraverser interface {
	Field(name string, value Inspector)
}
