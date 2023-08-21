package toolkit

import (
	"context"
	"net/http"
	"path/filepath"
	"strconv"
)

type FrontendServer struct {
	server *http.Server
	Port   int
}

// NewFrontendServer creates a new instance of FrontendServer.
func NewFrontendServer() *FrontendServer {
	port := 3000

	// Set up routes
	mux := http.NewServeMux()

	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html")
		_, err := w.Write([]byte(IndexHTML))
		if err != nil {
			http.Error(w, "Failed to write response", http.StatusInternalServerError)
			return
		}
	})

	mux.HandleFunc("/assets/", func(w http.ResponseWriter, r *http.Request) {
		path := "dashboard/assets" + r.URL.Path[len("/assets"):]

		data, err := Assets.ReadFile(path)
		if err != nil {
			http.Error(w, "Failed to read asset", http.StatusInternalServerError)
			return
		}

		switch filepath.Ext(r.URL.Path) {
		case ".js":
			w.Header().Set("Content-Type", "application/javascript")
		case ".svg":
			w.Header().Set("Content-Type", "image/svg+xml")
		}

		_, err = w.Write(data)
		if err != nil {
			http.Error(w, "Failed to write asset", http.StatusInternalServerError)
			return
		}
	})

	// Initialize the server
	server := &http.Server{
		Addr:    ":" + strconv.Itoa(port),
		Handler: mux,
	}

	return &FrontendServer{server: server, Port: port}
}

// Start begins serving HTTP requests.
func (f *FrontendServer) Start() error {
	return f.server.ListenAndServe()
}

// Shutdown stops the server gracefully.
func (f *FrontendServer) Shutdown(ctx context.Context) error {
	return f.server.Shutdown(ctx)
}
