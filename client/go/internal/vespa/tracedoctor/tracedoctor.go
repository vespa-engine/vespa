package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"io"
	"sort"
)

type threadSummary struct {
	matchMs       float64
	firstPhaseMs  float64
	secondPhaseMs float64
}

func (p *threadSummary) render(out *output) {
	tab := newTable("", "")
	tab.addRow("matching", fmt.Sprintf("%.3f ms", p.matchMs))
	tab.addRow("first phase", fmt.Sprintf("%.3f ms", p.firstPhaseMs))
	tab.addRow("second phase", fmt.Sprintf("%.3f ms", p.secondPhaseMs))
	tab.render(out)
}

type timing struct {
	queryMs   float64
	summaryMs float64
	totalMs   float64
}

func (t *timing) render(out *output) {
	if t == nil {
		return
	}
	tab := newTable("total", fmt.Sprintf("%.3f ms", t.totalMs))
	tab.addRow("query", fmt.Sprintf("%.3f ms", t.queryMs))
	tab.addRow("summary", fmt.Sprintf("%.3f ms", t.summaryMs))
	tab.addRow("other", fmt.Sprintf("%.3f ms", t.totalMs-t.queryMs-t.summaryMs))
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
	root   slime.Value
	timing *timing
}

func NewContext(root slime.Value) *Context {
	return &Context{
		root:   root,
		timing: extractTiming(root),
	}
}

func selectSlowestSearch(traces []protonTrace) (*protonTrace, *protonTrace) {
	sort.Slice(traces, func(i, j int) bool {
		return traces[i].durationMs() > traces[j].durationMs()
	})
	return &traces[0], &traces[len(traces)/2]
}

func selectSlowestThread(threads []threadTrace) (*threadTrace, *threadTrace) {
	if len(threads) == 0 {
		return nil, nil
	}
	sort.Slice(threads, func(i, j int) bool {
		return threads[i].profTimeMs() > threads[j].profTimeMs()
	})
	return &threads[0], &threads[len(threads)/2]
}

func (ctx *Context) analyzeThread(trace protonTrace, thread threadTrace, out *output) {
	thread.timeline().render(out)
	threadSummary := threadSummary{
		matchMs:       thread.matchTimeMs(),
		firstPhaseMs:  thread.firstPhaseTimeMs(),
		secondPhaseMs: thread.secondPhaseTimeMs(),
	}
	threadSummary.render(out)
	queryPerf := trace.extractQuery()
	queryPerf.importMatchPerf(thread)
	out.fmt("\nmatch profiling for thread #%d (total time was %f ms)\n", thread.id, thread.matchTimeMs())
	queryPerf.render(out)
	if firstPhasePerf := thread.firstPhasePerf(); firstPhasePerf.impact() != 0.0 {
		out.fmt("\nfirst phase rank profiling for thread #%d (total time was %f ms)\n", thread.id, thread.firstPhaseTimeMs())
		firstPhasePerf.render(out)
	}
	if secondPhasePerf := thread.secondPhasePerf(); secondPhasePerf.impact() != 0.0 {
		out.fmt("\nsecond phase rank profiling for thread #%d (total time was %f ms)\n", thread.id, thread.secondPhaseTimeMs())
		secondPhasePerf.render(out)
	}
}

func (ctx *Context) analyzeProtonTrace(trace protonTrace, peer *protonTrace, out *output) {
	threads := trace.findThreadTraces()
	cnt := len(threads)
	worst, median := selectSlowestThread(threads)
	trace.timeline().render(out)
	if worst != nil {
		out.fmt("found %d thread%s\n", cnt, suffix(cnt, "s"))
		out.fmt("slowest matching and ranking was thread #%d: %.3f ms\n", worst.id, worst.profTimeMs())
		if median != nil {
			out.fmt("median matching and ranking was thread #%d: %.3f ms\n", median.id, median.profTimeMs())
		}
		out.fmt("looking into thread #%d\n", worst.id)
		ctx.analyzeThread(trace, *worst, out)
		if ann := newAnnProbe(trace); ann.impact() != 0.0 {
			ann.render(out)
		}
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

type searchMeta struct {
	groups []protonTraceGroup
}

func (s searchMeta) render(out *output) {
	tab := newTable("search", "nodes", "back-end time", "document type")
	for _, group := range s.groups {
		groupID := group.id
		nodes := len(group.traces)
		docType := group.documentType()
		duration := group.durationMs()
		tab.addRow(fmt.Sprintf("%d", groupID), fmt.Sprintf("%d", nodes), fmt.Sprintf("%.3f ms", duration), docType)
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
		worst, median := selectSlowestSearch(groups[idx].traces)
		out.fmt("slowest node was: %s: %.3f ms\n", worst.desc(), worst.durationMs())
		var peer *protonTrace
		if median != worst {
			out.fmt("median node was: %s: %.3f ms\n", median.desc(), median.durationMs())
			if median.durationMs()*1.25 < worst.durationMs() && worst.durationMs()-median.durationMs() > 5.0 {
				peer = median
			}
		}
		out.fmt("looking into node %s\n", worst.desc())
		ctx.analyzeProtonTrace(*worst, peer, out)
	}
	return out.err
}
