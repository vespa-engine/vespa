// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
)

func (opts *Options) AddOpens(module, pkg string) {
	opt := fmt.Sprintf("--add-opens=%s/%s=ALL-UNNAMED", module, pkg)
	opts.AddOption(opt)
}

func (opts *Options) addOpensJB(pkg string) {
	opts.AddOpens("java.base", pkg)
}

func (opts *Options) AddCommonOpens() {
	opts.addOpensJB("java.io")
	opts.addOpensJB("java.lang")
	opts.addOpensJB("java.net")
	opts.addOpensJB("java.nio")
	opts.addOpensJB("jdk.internal.loader")
	opts.addOpensJB("sun.security.ssl")
}
