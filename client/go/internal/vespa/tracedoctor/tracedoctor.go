package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"io"
)

type threadSummary struct {
	matchMs       float64
	firstPhaseMs  float64
	secondPhaseMs float64
}

func (p *threadSummary) render(out *output) {
	out.fmt("+---------------+---------------+\n")
	out.fmt("| Matching      | %10.3f ms |\n", p.matchMs)
	out.fmt("| First phase   | %10.3f ms |\n", p.firstPhaseMs)
	out.fmt("| Second phase  | %10.3f ms |\n", p.secondPhaseMs)
	out.fmt("+---------------+---------------+\n")
}

type timing struct {
	queryMs   float64
	summaryMs float64
	totalMs   float64
}

func (t *timing) percentageOfQuery(parameter float64) string {
	if t == nil || parameter >= t.queryMs {
		return ""
	}
	percentage := (parameter / t.queryMs) * 100
	return fmt.Sprintf(" (%.2f%% of query time)", percentage)
}

func (t *timing) render(out *output) {
	if t == nil {
		return
	}
	out.fmt("+---------+---------------+\n")
	out.fmt("| Total   | %10.3f ms |\n", t.totalMs)
	out.fmt("+---------+---------------+\n")
	out.fmt("| Query   | %10.3f ms |\n", t.queryMs)
	out.fmt("| Summary | %10.3f ms |\n", t.summaryMs)
	out.fmt("| Other   | %10.3f ms |\n", t.totalMs-t.queryMs-t.summaryMs)
	out.fmt("+---------+---------------+\n")
}

func extractTiming(queryResult slime.Value) *timing {
	obj := queryResult.Field("timing")
	if !obj.Valid() {
		return nil
	}
	return &timing{queryMs: obj.Field("querytime").AsDouble() * 1000.0,
		summaryMs: obj.Field("summaryfetchtime").AsDouble() * 1000.0,
		totalMs:   obj.Field("searchtime").AsDouble() * 1000.0}
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
	return &Context{root: root,
		timing: extractTiming(root)}
}

func (ctx *Context) Analyze(stdout io.Writer) error {
	out := &output{out: stdout}
	ctx.timing.render(out)
	list := findProtonTraces(ctx.root)
	if len(list) == 0 {
		return fmt.Errorf("could not locate any back-end searches")
	}
	var mp = -1
	for i, p := range list {
		if mp < 0 || p.durationMs() > list[mp].durationMs() {
			mp = i
		}
	}
	out.fmt("found %d searches, slowest search was: %s[%d]: %10.3f ms%s\n",
		len(list), list[mp].documentType(), list[mp].distributionKey(),
		list[mp].durationMs(), ctx.timing.percentageOfQuery(list[mp].durationMs()))
	list[mp].renderSummary(out)
	threads := list[mp].findThreadTraces()
	if len(threads) == 0 {
		return fmt.Errorf("search thread information is missing")
	}
	var mt = -1
	for i, t := range threads {
		if mt < 0 || t.profTimeMs() > threads[mt].profTimeMs() {
			mt = i
		}
	}
	out.fmt("found %d threads, slowest matching/ranking was thread #%d: %10.3f ms\n",
		len(threads), mt, threads[mt].profTimeMs())
	threadSummary := threadSummary{matchMs: threads[mt].matchTimeMs(),
		firstPhaseMs:  threads[mt].firstPhaseTimeMs(),
		secondPhaseMs: threads[mt].secondPhaseTimeMs()}
	threadSummary.render(out)
	queryPerf := list[mp].extractQuery()
	queryPerf.importMatchPerf(threads[mt])
	queryPerf.render(out)
	return out.err
}
