// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"sort"
	"strings"
)

type queryNode struct {
	class       string
	fieldName   string
	queryTerm   string
	strict      string
	count       int64
	selfTimeMs  float64
	totalTimeMs float64
	children    []*queryNode
}

func (q *queryNode) each(f func(q *queryNode)) {
	f(q)
	for _, child := range q.children {
		child.each(f)
	}
}

func (q *queryNode) desc() string {
	if q.queryTerm != "" {
		if q.fieldName != "" {
			return fmt.Sprintf("%s %s:%s", q.class, q.fieldName, q.queryTerm)
		}
		return fmt.Sprintf("%s %s", q.class, q.queryTerm)
	}
	return q.class
}

func perfPad(last, self bool) string {
	if !last && !self {
		return "│   "
	}
	if !last {
		return "├── "
	}
	if self {
		return "└── "
	}
	return "    "
}

func (q *queryNode) makeTable(tab *table, prefix, padSelf, padChild string) {
	count := fmt.Sprintf("%d", q.count)
	totalTimeMs := fmt.Sprintf("%.3f", q.totalTimeMs)
	selfTimeMs := fmt.Sprintf("%.3f", q.selfTimeMs)
	strict := q.strict
	query := fmt.Sprintf(" %s%s%s ", prefix, padSelf, q.desc())
	tab.addRow(count, totalTimeMs, selfTimeMs, strict, query)
	for i, child := range q.children {
		last := i+1 == len(q.children)
		child.makeTable(tab, prefix+padChild, perfPad(last, true), perfPad(last, false))
	}
}

func (q *queryNode) render(output *output) {
	tab := newTable("count", "total_ms", "self_ms", "step", "query tree")
	q.makeTable(tab, "", "", "")
	tab.render(output)
}

func extractQueryNode(obj slime.Value) *queryNode {
	res := &queryNode{}
	if obj.Field("strict").AsBool() {
		res.strict = "S"
	} else {
		res.strict = "N"
	}
	if class := obj.Field("[type]"); class.Valid() {
		res.class = stripClassName(class.AsString())
	} else {
		res.class = "<unknown>"
	}
	res.queryTerm = obj.Field("query_term").AsString()
	if res.queryTerm != "" {
		if attr := obj.Field("attribute"); attr.Valid() {
			res.fieldName = attr.Field("name").AsString()
			if strings.Contains(res.class, "Attribute") {
				caps := "lookup"
				if attr.Field("fast_search").AsBool() {
					caps = "fs"
				}
				res.class = fmt.Sprintf("Attribute{%s,%s}",
					attr.Field("type").AsString(), caps)
			}
		} else {
			res.fieldName = obj.Field("field_name").AsString()
		}
	}
	res.class = strings.TrimSuffix(res.class, "Blueprint")
	childMap := obj.Field("children")
	for i := 0; true; i++ {
		childKey := fmt.Sprintf("[%d]", i)
		if child := childMap.Field(childKey); child.Valid() {
			res.children = append(res.children, extractQueryNode(child))
		} else {
			break
		}
	}
	return res
}

func stripClassName(name string) string {
	end := strings.Index(name, "<")
	if end == -1 {
		end = len(name)
	}
	ns := strings.LastIndex(name[:end], "::")
	var begin int
	if ns == -1 {
		begin = 0
	} else {
		begin = ns + 2
	}
	return name[begin:end]
}

func isMatchSample(sample slime.Value) bool {
	name := sample.Field("name").AsString()
	return strings.HasPrefix(name, "/") &&
		(strings.HasSuffix(name, "/init") ||
			strings.HasSuffix(name, "/seek") ||
			strings.HasSuffix(name, "/unpack") ||
			strings.HasSuffix(name, "/termwise"))
}

func samplePath(sample slime.Value) []int {
	var res []int
	name := sample.Field("name").AsString()
	if strings.HasPrefix(name, "/") {
		child := 0
		for pos := 1; pos < len(name); pos++ {
			c := name[pos]
			if c == '/' {
				res = append(res, child)
				child = 0
			} else {
				if c < '0' || c > '9' {
					break
				}
				child = child*10 + int(c-'0')
			}
		}
	}
	return res
}

func (q *queryNode) applySample(sample slime.Value) {
	if !isMatchSample(sample) {
		return
	}
	node := q
	path := samplePath(sample)
	for _, child := range path {
		if child < len(node.children) {
			node = node.children[child]
		} else {
			return
		}
	}
	node.count += sample.Field("count").AsLong()
	totalTime := sample.Field("total_time_ms").AsDouble()
	node.totalTimeMs += totalTime
	if selfTime := sample.Field("self_time_ms"); selfTime.Valid() {
		node.selfTimeMs += selfTime.AsDouble()
	} else {
		node.selfTimeMs += totalTime
	}
}

func hasTag(tag string) func(p *slime.Path, v slime.Value) bool {
	return func(p *slime.Path, v slime.Value) bool {
		return v.Field("tag").AsString() == tag
	}
}

func hasType(class string) func(p *slime.Path, v slime.Value) bool {
	return func(p *slime.Path, v slime.Value) bool {
		return v.Field("[type]").AsString() == class
	}
}

func eachSampleList(list slime.Value, f func(sample slime.Value)) {
	list.EachEntry(func(_ int, sample slime.Value) {
		f(sample)
		eachSampleList(sample.Field("children"), f)
	})
}

func eachSample(prof slime.Value, f func(sample slime.Value)) {
	eachSampleList(prof.Field("roots"), f)
}

func (q *queryNode) importMatchPerf(t threadTrace) {
	slime.Select(t.root, hasTag("match_profiling"), func(p *slime.Path, v slime.Value) {
		eachSample(v, func(sample slime.Value) {
			q.applySample(sample)
		})
	})
}

type threadTrace struct {
	root slime.Value
	id   int
}

func (t threadTrace) makeTimeline(trace slime.Value, timeline *timeline) {
	if !trace.Valid() {
		return
	}
	if trace.Type() == slime.ARRAY {
		trace.EachEntry(func(_ int, value slime.Value) {
			t.makeTimeline(value, timeline)
		})
		return
	}
	ms := trace.Field("timestamp_ms").AsDouble()
	if event := trace.Field("event"); event.Valid() {
		timeline.add(ms, event.AsString())
	}
}

func (t threadTrace) timeline() *timeline {
	res := &timeline{}
	t.makeTimeline(t.root.Field("traces"), res)
	return res
}

func (t threadTrace) firstPhasePerf() *topNPerf {
	perf := newTopNPerf()
	slime.Select(t.root, hasTag("first_phase_profiling"), func(p *slime.Path, v slime.Value) {
		eachSample(v, func(sample slime.Value) {
			myTime := sample.Field("total_time_ms").AsDouble()
			if selfTime := sample.Field("self_time_ms"); selfTime.Valid() {
				myTime = selfTime.AsDouble()
			}
			perf.addSample(sample.Field("name").AsString(), sample.Field("count").AsLong(), myTime)
		})
	})
	return perf
}

func (t threadTrace) secondPhasePerf() *topNPerf {
	perf := newTopNPerf()
	slime.Select(t.root, hasTag("second_phase_profiling"), func(p *slime.Path, v slime.Value) {
		eachSample(v, func(sample slime.Value) {
			myTime := sample.Field("total_time_ms").AsDouble()
			if selfTime := sample.Field("self_time_ms"); selfTime.Valid() {
				myTime = selfTime.AsDouble()
			}
			perf.addSample(sample.Field("name").AsString(), sample.Field("count").AsLong(), myTime)
		})
	})
	return perf
}

func (t threadTrace) matchTimeMs() float64 {
	p := slime.Find(t.root, hasTag("match_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

func (t threadTrace) firstPhaseTimeMs() float64 {
	p := slime.Find(t.root, hasTag("first_phase_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

func (t threadTrace) secondPhaseTimeMs() float64 {
	p := slime.Find(t.root, hasTag("second_phase_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

func (t threadTrace) profTimeMs() float64 {
	return t.matchTimeMs() + t.firstPhaseTimeMs() + t.secondPhaseTimeMs()
}

type threadSummary struct {
	id            int
	matchMs       float64
	firstPhaseMs  float64
	secondPhaseMs float64
}

func renderThreadSummaries(out *output, threads ...*threadSummary) {
	headers := []string{"task"}
	for _, thread := range threads {
		headers = append(headers, fmt.Sprintf("thread #%d", thread.id))
	}
	tab := newTable(headers...)
	addRow := func(task string, get func(thread *threadSummary) float64) {
		cells := []string{task}
		for _, thread := range threads {
			cells = append(cells, fmt.Sprintf("%.3f ms", get(thread)))
		}
		tab.addRow(cells...)
	}
	addRow("matching", func(thread *threadSummary) float64 { return thread.matchMs })
	addRow("first phase", func(thread *threadSummary) float64 { return thread.firstPhaseMs })
	addRow("second phase", func(thread *threadSummary) float64 { return thread.secondPhaseMs })
	tab.render(out)
}

func (t threadTrace) extractSummary() *threadSummary {
	return &threadSummary{
		id:            t.id,
		matchMs:       t.matchTimeMs(),
		firstPhaseMs:  t.firstPhaseTimeMs(),
		secondPhaseMs: t.secondPhaseTimeMs(),
	}
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

type protonTrace struct {
	root slime.Value
	path *slime.Path
}

func (p protonTrace) globalFilterPerf() *topNPerf {
	var maxTime float64
	maxPerf := slime.Invalid
	slime.Select(p.root, hasTag("global_filter_profiling"), func(p *slime.Path, v slime.Value) {
		myTime := v.Field("total_time_ms").AsDouble()
		if myTime > maxTime {
			maxTime = myTime
			maxPerf = v
		}
	})
	perf := newTopNPerf()
	eachSample(maxPerf, func(sample slime.Value) {
		myTime := sample.Field("total_time_ms").AsDouble()
		if selfTime := sample.Field("self_time_ms"); selfTime.Valid() {
			myTime = selfTime.AsDouble()
		}
		perf.addSample(sample.Field("name").AsString(), sample.Field("count").AsLong(), myTime)
	})
	return perf
}

func (p protonTrace) findThreadTraces() []threadTrace {
	var traces []threadTrace
	slime.Select(p.root, hasTag("query_execution"), func(p *slime.Path, v slime.Value) {
		id := 0
		v.Field("threads").EachEntry(func(idx int, v slime.Value) {
			traces = append(traces, threadTrace{root: v, id: id})
			id++
		})
	})
	return traces
}

func (p protonTrace) extractQuery() *queryNode {
	query := slime.Invalid
	plan := slime.Find(p.root, hasTag("query_execution_plan"))
	if len(plan) == 1 {
		query = plan[0].Apply(p.root).Field("optimized")
	}
	return extractQueryNode(query)
}

func (p protonTrace) makeTimeline(trace slime.Value, t *timeline) {
	if !trace.Valid() {
		return
	}
	if trace.Type() == slime.ARRAY {
		trace.EachEntry(func(_ int, value slime.Value) {
			p.makeTimeline(value, t)
		})
		return
	}
	tag := trace.Field("tag").AsString()
	ms := trace.Field("timestamp_ms").AsDouble()
	if event := trace.Field("event"); event.Valid() {
		t.add(ms, event.AsString())
	}
	if tag == "query_setup" {
		p.makeTimeline(trace.Field("traces"), t)
	}
	if tag == "query_execution" {
		t.addComment("(query execution happens here, analyzed below)")
	}
}

func (p protonTrace) timeline() *timeline {
	res := &timeline{}
	p.makeTimeline(p.root.Field("traces"), res)
	return res
}

func (p protonTrace) distributionKey() int64 {
	return p.root.Field("distribution-key").AsLong()
}

func (p protonTrace) documentType() string {
	return p.root.Field("document-type").AsString()
}

func (p protonTrace) durationMs() float64 {
	return p.root.Field("duration_ms").AsDouble()
}

func (p protonTrace) desc() string {
	return fmt.Sprintf("%s[%d]", p.documentType(), p.distributionKey())
}

type protonSummary struct {
	name          string
	filterMs      float64
	annMs         float64
	matchMs       float64
	firstPhaseMs  float64
	secondPhaseMs float64
}

func renderProtonSummaries(out *output, nodes ...*protonSummary) {
	headers := []string{"task"}
	for _, node := range nodes {
		headers = append(headers, node.name)
	}
	tab := newTable(headers...)
	addRow := func(task string, get func(node *protonSummary) float64) {
		cells := []string{task}
		for _, node := range nodes {
			cells = append(cells, fmt.Sprintf("%.3f ms", get(node)))
		}
		tab.addRow(cells...)
	}
	addRow("global filter", func(node *protonSummary) float64 { return node.filterMs })
	addRow("ann setup", func(node *protonSummary) float64 { return node.annMs })
	addRow("matching", func(node *protonSummary) float64 { return node.matchMs })
	addRow("first phase", func(node *protonSummary) float64 { return node.firstPhaseMs })
	addRow("second phase", func(node *protonSummary) float64 { return node.secondPhaseMs })
	tab.render(out)
}

func (p protonTrace) extractSummary() *protonSummary {
	res := &protonSummary{name: p.desc()}
	timeline := p.timeline()
	res.filterMs = timeline.durationOf("Calculate global filter")
	res.annMs = timeline.durationOf("Handle global filter in query execution plan")
	if thread, _ := selectSlowestThread(p.findThreadTraces()); thread != nil {
		res.matchMs = thread.matchTimeMs()
		res.firstPhaseMs = thread.firstPhaseTimeMs()
		res.secondPhaseMs = thread.secondPhaseTimeMs()
	}
	return res
}

type protonTraceGroup struct {
	traces []protonTrace
	id     int
}

func (p protonTraceGroup) durationMs() float64 {
	var res float64
	for _, trace := range p.traces {
		if trace.durationMs() > res {
			res = trace.durationMs()
		}
	}
	return res
}

func (p protonTraceGroup) documentType() string {
	if len(p.traces) > 0 {
		return p.traces[0].documentType()
	}
	return ""
}

func groupProtonTraces(traces []protonTrace) []protonTraceGroup {
	groupMap := make(map[string]*protonTraceGroup)
	for _, trace := range traces {
		tag := trace.path.Clone().Trim(3).Field(trace.documentType()).String()
		if group, exists := groupMap[tag]; exists {
			group.traces = append(group.traces, trace)
		} else {
			groupMap[tag] = &protonTraceGroup{traces: []protonTrace{trace}, id: len(groupMap)}
		}
	}
	res := make([]protonTraceGroup, 0, len(groupMap))
	for _, group := range groupMap {
		res = append(res, *group)
	}
	sort.Slice(res, func(i, j int) bool {
		return res[i].id < res[j].id
	})
	return res
}

func findProtonTraces(root slime.Value) []protonTrace {
	var traces []protonTrace
	slime.Select(root.Field("trace"), func(p *slime.Path, v slime.Value) bool {
		return slime.Valid(v.Field("distribution-key"), v.Field("document-type"), v.Field("duration_ms"))
	}, func(p *slime.Path, v slime.Value) {
		traces = append(traces, protonTrace{root: v, path: p.Clone()})
	})
	return traces
}
