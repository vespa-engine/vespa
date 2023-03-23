package document

import (
	"encoding/json"
	"sync"
	"testing"
)

type mockFeeder struct {
	documents []Document
	mu        sync.Mutex
}

func (f *mockFeeder) Send(doc Document) Result {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.documents = append(f.documents, doc)
	return Result{Id: doc.Id}
}

func (f *mockFeeder) Stats() Stats { return Stats{} }

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
