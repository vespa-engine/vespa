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

func (p *ProgSpec) setup() {
	p.BaseName = baseNameOf(p.Program)
	p.Env = make(map[string]string)
	p.numaSocket = -1
}

func baseNameOf(s string) string {
	idx := strings.LastIndex(s, "/")
	idx++
	return s[idx:]
}

func (p *ProgSpec) setenv(k, v string) {
	p.Env[k] = v
}

func (p *ProgSpec) matchesListEnv(envVarName string) bool {
	return p.matchesListString(os.Getenv(envVarName))
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
	return p.valueFromListString(os.Getenv(envVarName))
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
