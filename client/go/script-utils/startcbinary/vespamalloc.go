// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func vespaMallocLib(suf string) string {
	prefixes := []string{"lib64", "lib"}
	for _, pre := range prefixes {
		fn := fmt.Sprintf("%s/vespa/malloc/%s", pre, suf)
		existsOk, fileName := vespa.HasFileUnderVespaHome(fn)
		if existsOk {
			trace.Debug("found library:", fileName)
			return fileName
		}
		trace.Debug("bad or missing library:", fn)
	}
	return ""
}

func (p *ProgSpec) configureVespaMalloc() {
	p.shouldUseVespaMalloc = false
	if p.matchesListEnv(ENV_VESPA_USE_NO_VESPAMALLOC) {
		trace.Trace("use no vespamalloc:", p.BaseName)
		return
	}
	if p.shouldUseValgrind && !p.shouldUseCallgrind {
		trace.Trace("use valgrind, so no vespamalloc:", p.BaseName)
		return
	}
	var useFile string
	if p.matchesListEnv(ENV_VESPA_USE_VESPAMALLOC_DST) {
		useFile = vespaMallocLib("libvespamallocdst16.so")
	} else if p.matchesListEnv(ENV_VESPA_USE_VESPAMALLOC_D) {
		useFile = vespaMallocLib("libvespamallocd.so")
	} else if p.matchesListEnv(ENV_VESPA_USE_VESPAMALLOC) {
		useFile = vespaMallocLib("libvespamalloc.so")
	}
	trace.Trace("use file:", useFile)
	if useFile == "" {
		return
	}
	if loadAsHuge := p.getenv(ENV_VESPA_LOAD_CODE_AS_HUGEPAGES); loadAsHuge != "" {
		otherFile := vespaMallocLib("libvespa_load_as_huge.so")
		useFile = fmt.Sprintf("%s:%s", useFile, otherFile)
	}
	p.considerEnvFallback(ENV_VESPA_MALLOC_HUGEPAGES, ENV_VESPA_USE_HUGEPAGES)
	p.vespaMallocPreload = useFile
	p.shouldUseVespaMalloc = true
}
