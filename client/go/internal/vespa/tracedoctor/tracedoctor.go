// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"io"
	"sort"
)

type timing struct {
	queryMs   float64
	summaryMs float64
	totalMs   float64
}

func (t *timing) render(out *output) {
	if t == nil {
		return
	}
	tab := newTable().str("total").str(fmt.Sprintf("%.3f ms", t.totalMs)).commit().line()
	tab.str("query").str(fmt.Sprintf("%.3f ms", t.queryMs)).commit()
	tab.str("summary").str(fmt.Sprintf("%.3f ms", t.summaryMs)).commit()
	tab.str("other").str(fmt.Sprintf("%.3f ms", t.totalMs-t.queryMs-t.summaryMs)).commit()
	tab.render(out)
}

func extractTiming(queryResult slime.Value) *timing {
	obj := queryResult.Field("timing")
	queryTime := obj.Field("querytime")
	summaryTime := obj.Field("summaryfetchtime")
	totalTime := obj.Field("searchtime")
	if !slime.Valid(queryTime, summaryTime, totalTime) {
		return nil
	}
	if totalTime.AsDouble() < 0.0 {
		return nil
	}
	return &timing{
		queryMs:   queryTime.AsDouble() * 1000.0,
		summaryMs: summaryTime.AsDouble() * 1000.0,
		totalMs:   totalTime.AsDouble() * 1000.0,
	}
}

type output struct {
	out io.Writer
	err error
}

func (out *output) fmt(format string, args ...interface{}) {
	if out.err == nil {
		_, out.err = fmt.Fprintf(out.out, format, args...)
	}
}

type Context struct {
	root             slime.Value
	timing           *timing
	selectMedianNode bool
}

func (ctx *Context) SelectMedianNode() {
	ctx.selectMedianNode = true
}

func NewContext(root slime.Value) *Context {
	return &Context{
		root:   root,
		timing: extractTiming(root),
	}
}

func (ctx *Context) analyzeThread(trace protonTrace, thread threadTrace, peer *threadTrace, out *output) {
	overview := []*threadSummary{thread.extractSummary()}
	if peer != nil {
		overview = append(overview, peer.extractSummary())
	}
	renderThreadSummaries(out, overview...)
	out.fmt("looking into thread #%d\n", thread.id)
	thread.timeline().render(out)
	queryPerf := trace.extractQuery()
	queryPerf.importMatchPerf(thread)
	out.fmt("match profiling for thread #%d (total time was %.3f ms)\n", thread.id, thread.matchTimeMs())
	queryPerf.render(out)
	if firstPhasePerf := thread.firstPhasePerf(); firstPhasePerf.impact() != 0.0 {
		out.fmt("first phase rank profiling for thread #%d (total time was %.3f ms)\n", thread.id, thread.firstPhaseTimeMs())
		firstPhasePerf.render(out)
	}
	if secondPhasePerf := thread.secondPhasePerf(); secondPhasePerf.impact() != 0.0 {
		out.fmt("second phase rank profiling for thread #%d (total time was %.3f ms)\n", thread.id, thread.secondPhaseTimeMs())
		secondPhasePerf.render(out)
	}
}

func (ctx *Context) analyzeProtonTrace(trace protonTrace, peer *protonTrace, out *output) {
	overview := []*protonSummary{trace.extractSummary()}
	if peer != nil {
		overview = append(overview, peer.extractSummary())
	}
	renderProtonSummaries(out, overview...)
	out.fmt("looking into node %s\n", trace.desc())
	trace.timeline().render(out)
	if ann := newAnnProbe(trace); ann.impact() != 0.0 {
		ann.render(out)
	}
	if globalFilterPerf := trace.globalFilterPerf(); globalFilterPerf.impact() != 0.0 {
		out.fmt("global filter profiling\n")
		globalFilterPerf.render(out)
	}
	threads := trace.findThreadTraces()
	cnt := len(threads)
	worst, median := selectSlowestThread(threads)
	if worst != nil {
		out.fmt("found %d thread%s\n", cnt, suffix(cnt, "s"))
		out.fmt("slowest matching and ranking was thread #%d: %.3f ms\n", worst.id, worst.profTimeMs())
		if median != worst {
			out.fmt("median matching and ranking was thread #%d: %.3f ms\n", median.id, median.profTimeMs())
		} else {
			median = nil
		}
		ctx.analyzeThread(trace, *worst, median, out)
	}
}

func selectSlowestGroup(groups []protonTraceGroup) int {
	var slowestIndex int
	var maxDuration float64
	for i, group := range groups {
		duration := group.durationMs()
		if duration > maxDuration {
			maxDuration = duration
			slowestIndex = i
		}
	}
	return slowestIndex
}

func selectSlowestNode(traces []protonTrace) (*protonTrace, *protonTrace) {
	sort.Slice(traces, func(i, j int) bool {
		return traces[i].durationMs() > traces[j].durationMs()
	})
	return &traces[0], &traces[len(traces)/2]
}

type searchMeta struct {
	groups []protonTraceGroup
}

func (s searchMeta) render(out *output) {
	tab := newTable().str("search").str("nodes").str("back-end time").str("document type").commit().line()
	for _, group := range s.groups {
		tab.str(fmt.Sprintf("%d", group.id))
		tab.str(fmt.Sprintf("%d", len(group.traces)))
		tab.str(fmt.Sprintf("%.3f ms", group.durationMs()))
		tab.str(group.documentType())
		tab.commit()
	}
	tab.render(out)
}

func (ctx *Context) Analyze(stdout io.Writer) error {
	out := &output{out: stdout}
	ctx.timing.render(out)
	groups := groupProtonTraces(findProtonTraces(ctx.root))
	if len(groups) > 0 {
		out.fmt("found %d search%s\n", len(groups), suffix(len(groups), "es"))
		searchMeta{groups}.render(out)
		idx := selectSlowestGroup(groups)
		out.fmt("looking into search #%d\n", idx)
		worst, median := selectSlowestNode(groups[idx].traces)
		out.fmt("slowest node was: %s: %.3f ms\n", worst.desc(), worst.durationMs())
		if median != worst {
			out.fmt("median node was: %s: %.3f ms\n", median.desc(), median.durationMs())
		} else {
			median = nil
		}
		if ctx.selectMedianNode && median != nil {
			out.fmt("user directive: select median node\n")
			ctx.analyzeProtonTrace(*median, worst, out)
		} else {
			ctx.analyzeProtonTrace(*worst, median, out)
		}
	}
	return out.err
}
