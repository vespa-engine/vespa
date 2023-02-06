// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func (p *ProgSpec) configureValgrind() {
	p.shouldUseValgrind = false
	p.shouldUseCallgrind = false
	env := p.getenv(envvars.VESPA_USE_VALGRIND)
	parts := strings.Split(env, " ")
	for _, part := range parts {
		if p.BaseName == part {
			trace.Trace("using valgrind as", p.Program, "has basename in", envvars.VESPA_USE_VALGRIND, "=>", env)
			backticks := util.BackTicksWithStderr
			out, err := backticks.Run("which", "valgrind")
			if err != nil {
				trace.Trace("no valgrind, 'which' fails:", err, "=>", out)
				return
			}
			if opts := p.getenv(envvars.VESPA_VALGRIND_OPT); strings.Contains(opts, "callgrind") {
				p.shouldUseCallgrind = true
			}
			p.shouldUseValgrind = true
			return
		}
		trace.Debug("checking", envvars.VESPA_USE_VALGRIND, ":", p.BaseName, "!=", part)
	}
}

func (p *ProgSpec) valgrindBinary() string {
	return "valgrind"
}

func (p *ProgSpec) valgrindOptions() []string {
	env := p.getenv(envvars.VESPA_VALGRIND_OPT)
	if env != "" {
		return strings.Fields(env)
	}
	result := []string{
		"--num-callers=32",
		"--run-libc-freeres=yes",
		"--track-origins=yes",
		"--freelist-vol=1000000000",
		"--leak-check=full",
		"--show-reachable=yes",
	}
	result = addValgrindSuppression(result, "etc/vespa/valgrind-suppressions.txt")
	result = addValgrindSuppression(result, "etc/vespa/suppressions.txt")
	return result
}

func addValgrindSuppression(r []string, fn string) []string {
	existsOk, fileName := vespa.HasFileUnderVespaHome(fn)
	if existsOk {
		r = append(r, fmt.Sprintf("--suppressions=%s", fileName))
	}
	return r
}

func (p *ProgSpec) valgrindLogOption() string {
	return fmt.Sprintf("--log-file=%s/tmp/valgrind.%s.log.%d", vespa.FindHome(), p.BaseName, os.Getpid())
}

func (p *ProgSpec) prependValgrind(args []string) []string {
	v := util.NewArrayList[string](15 + len(args))
	v.Append(p.valgrindBinary())
	v.AppendAll(p.valgrindOptions()...)
	v.Append(p.valgrindLogOption())
	v.AppendAll(args...)
	return v
}
