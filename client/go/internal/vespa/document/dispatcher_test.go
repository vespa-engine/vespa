package document

import (
	"encoding/json"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
)

type mockFeeder struct {
	failAfterNDocs int
	documents      []Document
	stats          Stats
	mu             sync.Mutex
}

func (f *mockFeeder) failAfterN(docs int) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.failAfterNDocs = docs
}

func (f *mockFeeder) Send(doc Document) Result {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failAfterNDocs > 0 && len(f.documents) >= f.failAfterNDocs {
		return Result{Id: doc.Id, Status: StatusVespaFailure}
	}
	f.documents = append(f.documents, doc)
	return Result{Id: doc.Id}
}

func (f *mockFeeder) Stats() Stats { return f.stats }

func (f *mockFeeder) AddStats(stats Stats) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.stats.Add(stats)
}

func TestDispatcher(t *testing.T) {
	feeder := &mockFeeder{}
	dispatcher := NewDispatcher(feeder, 2)
	docs := []Document{
		{PutId: "id:ns:type::doc1", Fields: json.RawMessage(`{"foo": "123"}`)},
		{PutId: "id:ns:type::doc2", Fields: json.RawMessage(`{"bar": "456"}`)},
	}
	for _, d := range docs {
		dispatcher.Enqueue(d)
	}
	dispatcher.Close()
	if got, want := len(feeder.documents), 2; got != want {
		t.Errorf("got %d documents, want %d", got, want)
	}
}

func TestDispatcherOrdering(t *testing.T) {
	feeder := &mockFeeder{}
	commonId := "id:ns:type::doc1"
	docs := []Document{
		mustParseDocument(Document{PutId: commonId}),
		mustParseDocument(Document{PutId: "id:ns:type::doc2"}),
		mustParseDocument(Document{PutId: "id:ns:type::doc3"}),
		mustParseDocument(Document{PutId: "id:ns:type::doc4"}),
		mustParseDocument(Document{UpdateId: commonId}),
		mustParseDocument(Document{PutId: "id:ns:type::doc5"}),
		mustParseDocument(Document{PutId: "id:ns:type::doc6"}),
		mustParseDocument(Document{RemoveId: commonId}),
		mustParseDocument(Document{PutId: "id:ns:type::doc7"}),
		mustParseDocument(Document{PutId: "id:ns:type::doc8"}),
		mustParseDocument(Document{PutId: "id:ns:type::doc9"}),
	}
	dispatcher := NewDispatcher(feeder, len(docs))
	for _, d := range docs {
		dispatcher.Enqueue(d)
	}
	dispatcher.Close()

	var wantDocs []Document
	for _, d := range docs {
		if d.Id.String() == commonId {
			wantDocs = append(wantDocs, d)
		}
	}
	var gotDocs []Document
	for _, d := range feeder.documents {
		if d.Id.String() == commonId {
			gotDocs = append(gotDocs, d)
		}
	}
	assert.Equal(t, len(docs), len(feeder.documents))
	assert.Equal(t, wantDocs, gotDocs)
	assert.Equal(t, int64(0), feeder.Stats().Errors)
}

func TestDispatcherOrderingWithFailures(t *testing.T) {
	feeder := &mockFeeder{}
	commonId := "id:ns:type::doc1"
	docs := []Document{
		mustParseDocument(Document{PutId: commonId}),
		mustParseDocument(Document{PutId: commonId}),
		mustParseDocument(Document{UpdateId: commonId}), // fails
		mustParseDocument(Document{RemoveId: commonId}), // fails
	}
	feeder.failAfterN(2)
	dispatcher := NewDispatcher(feeder, len(docs))
	for _, d := range docs {
		dispatcher.Enqueue(d)
	}
	dispatcher.Close()
	wantDocs := docs[:2]
	assert.Equal(t, wantDocs, feeder.documents)
	assert.Equal(t, int64(2), feeder.Stats().Errors)

	// Dispatching more documents for same ID fails implicitly
	feeder.failAfterN(0)
	dispatcher.start()
	dispatcher.Enqueue(mustParseDocument(Document{PutId: commonId}))
	dispatcher.Enqueue(mustParseDocument(Document{RemoveId: commonId}))
	// Other IDs are fine
	doc2 := mustParseDocument(Document{PutId: "id:ns:type::doc2"})
	doc3 := mustParseDocument(Document{PutId: "id:ns:type::doc3"})
	dispatcher.Enqueue(doc2)
	dispatcher.Enqueue(doc3)
	dispatcher.Close()
	assert.Equal(t, int64(4), feeder.Stats().Errors)
	assert.Equal(t, 4, len(feeder.documents))
}
