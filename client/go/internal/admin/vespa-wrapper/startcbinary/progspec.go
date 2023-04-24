// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
)

func NewProgSpec(argv []string) *prog.Spec {
	progName := argv[0]
	binProg := progName + "-bin"
	p := prog.NewSpec(argv)
	p.Program = binProg
	p.Args[0] = binProg
	return p
}

func prependPath(dirName string, p *prog.Spec) {
	pathList := []string{dirName}
	oldPath := p.Getenv(envvars.PATH)
	if oldPath == "" {
		oldPath = "/usr/bin"
	}
	for _, part := range strings.Split(oldPath, ":") {
		if part != dirName {
			pathList = append(pathList, part)
		}
	}
	newPath := strings.Join(pathList, ":")
	p.Setenv(envvars.PATH, newPath)
	os.Setenv(envvars.PATH, newPath)
}
