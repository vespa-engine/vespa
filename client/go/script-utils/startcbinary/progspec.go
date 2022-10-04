// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
)

type ProgSpec struct {
	Program              string
	Args                 []string
	BaseName             string
	Env                  map[string]string
	numaSocket           int
	shouldUseCallgrind   bool
	shouldUseValgrind    bool
	shouldUseNumaCtl     bool
	shouldUseVespaMalloc bool
	vespaMallocPreload   string
}

func NewProgSpec(argv []string) *ProgSpec {
	progName := argv[0]
	binProg := progName + "-bin"
	p := ProgSpec{
		Program:    binProg,
		Args:       argv,
		BaseName:   baseNameOf(progName),
		Env:        make(map[string]string),
		numaSocket: -1,
	}
	p.Args[0] = binProg
	return &p
}

func baseNameOf(s string) string {
	idx := strings.LastIndex(s, "/")
	idx++
	return s[idx:]
}

func (p *ProgSpec) setenv(k, v string) {
	p.Env[k] = v
}

func (p *ProgSpec) getenv(k string) string {
	if v, ok := p.Env[k]; ok {
		return v
	}
	return os.Getenv(k)
}

func (p *ProgSpec) prependPath(dirName string) {
	pathList := []string{dirName}
	oldPath := p.getenv(ENV_PATH)
	if oldPath == "" {
		oldPath = "/usr/bin"
	}
	for _, part := range strings.Split(oldPath, ":") {
		if part != dirName {
			pathList = append(pathList, part)
		}
	}
	newPath := strings.Join(pathList, ":")
	p.setenv(ENV_PATH, newPath)
	os.Setenv(ENV_PATH, newPath)
}

func (p *ProgSpec) matchesListEnv(envVarName string) bool {
	return p.matchesListString(p.getenv(envVarName))
}

func (p *ProgSpec) matchesListString(env string) bool {
	if env == "all" {
		trace.Debug(p.Program, "always matching in:", env)
		return true
	}
	parts := strings.Fields(env)
	for _, part := range parts {
		if p.BaseName == part {
			trace.Debug(p.Program, "has basename matching in:", env)
			return true
		}
		trace.Debug("checking matching:", p.BaseName, "!=", part)
	}
	return false
}

func (p *ProgSpec) valueFromListEnv(envVarName string) string {
	return p.valueFromListString(p.getenv(envVarName))
}

func (p *ProgSpec) valueFromListString(env string) string {
	parts := strings.Fields(env)
	for _, part := range parts {
		idx := strings.Index(part, "=")
		if idx <= 0 {
			trace.Trace("expected key=value, but got:", part)
			continue
		}
		partName := part[:idx]
		idx++
		partValue := part[idx:]
		if p.BaseName == partName || partName == "all" {
			trace.Debug(p.Program, "has basename matching in:", env)
			return partValue
		}
		trace.Debug("checking matching:", p.BaseName, "!=", part)
	}
	return ""
}

func (spec *ProgSpec) effectiveEnv() []string {
	env := make(map[string]string)
	for _, entry := range os.Environ() {
		addInMap := func(kv string) bool {
			for idx, elem := range kv {
				if elem == '=' {
					k := kv[:idx]
					env[k] = kv
					return true
				}
			}
			return false
		}
		if !addInMap(entry) {
			env[entry] = ""
		}
	}
	for k, v := range spec.Env {
		trace.Trace("add to environment:", k, "=", v)
		env[k] = k + "=" + v
	}
	envv := make([]string, 0, len(env))
	for _, v := range env {
		envv = append(envv, v)
	}
	return envv
}
