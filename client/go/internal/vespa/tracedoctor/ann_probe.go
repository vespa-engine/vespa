package tracedoctor

type annProbe struct {
	annTime float64
	nodes   []annNode
	trace   protonTrace
}

func (p *annProbe) analyze() {
	p.annTime = p.trace.timeline().durationOf("Handle global filter in query execution plan")
	p.nodes = p.trace.findAnnNodes()
}

func (p *annProbe) impact() float64 {
	if len(p.nodes) > 0 {
		return p.annTime
	}
	return 0.0
}

func (p *annProbe) render(out *output) {
	out.fmt("\nANN during query setup: %.3f ms\n", p.annTime)
	for _, node := range p.nodes {
		node.render(out)
	}
}

func newAnnProbe(trace protonTrace) *annProbe {
	res := &annProbe{trace: trace}
	res.analyze()
	return res
}
