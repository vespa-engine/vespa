package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTopNPerfAddSample(t *testing.T) {
	tp := newTopNPerf()
	tp.addSample("A", 2, 10.5)
	tp.addSample("B", 1, 20.2)
	tp.addSample("A", 3, 5.0)
	tp.addSample("C", 1, 30.0)
	tp.addSample("B", 2, 15.8)

	assert.Equal(t, int64(5), tp.entries["A"].count)
	assert.Equal(t, 15.5, tp.entries["A"].selfTimeMs)
	assert.Equal(t, int64(3), tp.entries["B"].count)
	assert.Equal(t, 36.0, tp.entries["B"].selfTimeMs)
	assert.Equal(t, int64(1), tp.entries["C"].count)
	assert.Equal(t, 30.0, tp.entries["C"].selfTimeMs)
}

func TestTopNPerfTopN(t *testing.T) {
	tp := newTopNPerf()
	tp.addSample("A", 2, 10.5)
	tp.addSample("B", 1, 20.2)
	tp.addSample("A", 3, 5.0)
	tp.addSample("C", 1, 30.0)
	tp.addSample("B", 2, 15.8)

	top2 := tp.topN(2)
	assert.Equal(t, 2, len(top2))
	assert.Equal(t, "B", top2[0].name)
	assert.Equal(t, "C", top2[1].name)

	top5 := tp.topN(5)
	assert.Equal(t, 3, len(top5))
}
