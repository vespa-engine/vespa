// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

const profileSummarySchemaVersion = 1
const profileSummaryTopComponents = 10

// ProfileSummary is a stable, machine-readable summary of the profile data
// analyzed by tracedoctor. It intentionally does not expose the full trace tree.
type ProfileSummary struct {
	SchemaVersion int             `json:"schemaVersion"`
	Timing        ProfileTiming   `json:"timing"`
	Searches      []SearchSummary `json:"searches,omitempty"`
	Warnings      []string        `json:"warnings,omitempty"`
}

// ProfileTiming summarizes the top-level timing reported by the query result.
type ProfileTiming struct {
	TotalMs   float64 `json:"totalMs"`
	QueryMs   float64 `json:"queryMs"`
	SummaryMs float64 `json:"summaryMs"`
	OtherMs   float64 `json:"otherMs"`
}

// SearchSummary summarizes one dispatched search across its backend nodes.
type SearchSummary struct {
	ID            int           `json:"id"`
	DocumentType  string        `json:"documentType,omitempty"`
	Nodes         int           `json:"nodes"`
	BackendTimeMs float64       `json:"backendTimeMs"`
	NodeSummaries []NodeSummary `json:"nodeSummaries,omitempty"`
}

// NodeSummary summarizes the most useful profile information for one backend node.
type NodeSummary struct {
	Name                     string             `json:"name"`
	DurationMs               float64            `json:"durationMs"`
	Tasks                    ProfileTasks       `json:"tasks"`
	TopFirstPhaseComponents  []ProfileComponent `json:"topFirstPhaseComponents,omitempty"`
	TopSecondPhaseComponents []ProfileComponent `json:"topSecondPhaseComponents,omitempty"`
}

// ProfileTasks contains the node-level task timings extracted by tracedoctor.
type ProfileTasks struct {
	GlobalFilterMs float64 `json:"globalFilterMs"`
	AnnSetupMs     float64 `json:"annSetupMs"`
	MatchingMs     float64 `json:"matchingMs"`
	FirstPhaseMs   float64 `json:"firstPhaseMs"`
	SecondPhaseMs  float64 `json:"secondPhaseMs"`
}

// ProfileComponent contains accumulated self time for a profiled rank component or function.
type ProfileComponent struct {
	Name       string  `json:"name"`
	Count      int64   `json:"count"`
	SelfTimeMs float64 `json:"selfTimeMs"`
}

// Summary returns a compact machine-readable profile summary.
func (ctx *Context) Summary() ProfileSummary {
	summary := ProfileSummary{
		SchemaVersion: profileSummarySchemaVersion,
	}
	if ctx.timing != nil {
		summary.Timing = ProfileTiming{
			TotalMs:   ctx.timing.totalMs,
			QueryMs:   ctx.timing.queryMs,
			SummaryMs: ctx.timing.summaryMs,
			OtherMs:   ctx.timing.totalMs - ctx.timing.queryMs - ctx.timing.summaryMs,
		}
	}
	for _, group := range groupProtonTraces(findProtonTraces(ctx.root)) {
		search := SearchSummary{
			ID:            group.id,
			DocumentType:  group.documentType(),
			Nodes:         len(group.traces),
			BackendTimeMs: group.durationMs(),
		}
		for _, trace := range group.traces {
			search.NodeSummaries = append(search.NodeSummaries, summarizeNode(trace))
		}
		summary.Searches = append(summary.Searches, search)
	}
	return summary
}

func summarizeNode(trace protonTrace) NodeSummary {
	proton := trace.extractSummary()
	node := NodeSummary{
		Name:       trace.desc(),
		DurationMs: trace.durationMs(),
		Tasks: ProfileTasks{
			GlobalFilterMs: proton.filterMs,
			AnnSetupMs:     proton.annMs,
			MatchingMs:     proton.matchMs,
			FirstPhaseMs:   proton.firstPhaseMs,
			SecondPhaseMs:  proton.secondPhaseMs,
		},
	}
	if thread, _ := selectSlowestThread(trace.findThreadTraces()); thread != nil {
		node.TopFirstPhaseComponents = summarizeComponents(thread.firstPhasePerf())
		node.TopSecondPhaseComponents = summarizeComponents(thread.secondPhasePerf())
	}
	return node
}

func summarizeComponents(perf *topNPerf) []ProfileComponent {
	if perf == nil {
		return nil
	}
	var components []ProfileComponent
	for _, entry := range perf.topN(profileSummaryTopComponents) {
		components = append(components, ProfileComponent{
			Name:       entry.name,
			Count:      entry.count,
			SelfTimeMs: entry.selfTimeMs,
		})
	}
	return components
}
