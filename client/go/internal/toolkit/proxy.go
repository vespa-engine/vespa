package toolkit

import (
	"context"
	"crypto/tls"
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strconv"
)

type ProxyServer struct {
	server *http.Server
	Port   int
}

// NewProxyServer creates a new instance of ProxyServer.
func NewProxyServer(service *vespa.Service, target vespa.Target) (*ProxyServer, error) {
	port := 8081

	endpointUrl, err := url.Parse(service.BaseURL)
	if err != nil {
		return nil, fmt.Errorf("failed to parse Vespa endpoint: %w", err)
	}

	// Create the reverse proxy
	proxy := httputil.NewSingleHostReverseProxy(endpointUrl)

	if target.IsCloud() {
		// Load the certificate and key only for cloud targets
		cert, err := tls.LoadX509KeyPair(service.TLSOptions.CertificateFile, service.TLSOptions.PrivateKeyFile)
		if err != nil {
			return nil, fmt.Errorf("failed to load Vespa certificate and key: %w", err)
		}
		proxy.Transport = &http.Transport{
			TLSClientConfig: &tls.Config{
				Certificates:       []tls.Certificate{cert},
				InsecureSkipVerify: false,
			},
		}
	}

	// Set up routes
	mux := http.NewServeMux()

	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		proxy.ServeHTTP(w, r)
	})

	corsHandler := CORSMiddleware(mux)

	// Initialize the server
	server := &http.Server{
		Addr:    ":" + strconv.Itoa(port),
		Handler: corsHandler,
	}

	return &ProxyServer{server: server, Port: port}, nil
}

// Start begins serving HTTP requests.
func (p *ProxyServer) Start() error {
	return p.server.ListenAndServe()
}

// Shutdown stops the server gracefully.
func (p *ProxyServer) Shutdown(ctx context.Context) error {
	return p.server.Shutdown(ctx)
}

// CORSMiddleware sets CORS headers for every HTTP request.
func CORSMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		setCORSHeaders(w)
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// setCORSHeaders sets the necessary CORS headers.
func setCORSHeaders(w http.ResponseWriter) {
	w.Header().Set("Access-Control-Allow-Origin", "http://localhost:3000")
	w.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE")
	w.Header().Set("Access-Control-Allow-Headers", "Accept, Content-Type, Content-Length, Accept-Encoding, Authorization")
}
