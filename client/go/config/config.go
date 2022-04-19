// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package config

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"sync"

	"gopkg.in/yaml.v3"
)

// Config represents a thread-safe key-value config, which can be marshalled to YAML.
type Config struct {
	values map[string]string
	mu     sync.RWMutex
}

// New creates a new config.
func New() *Config { return &Config{values: make(map[string]string)} }

// Keys returns a sorted slice of keys set in this config.
func (c *Config) Keys() []string {
	var keys []string
	for k := range c.values {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}

// Get returns the value associated with key.
func (c *Config) Get(key string) (string, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	v, ok := c.values[key]
	return v, ok
}

// Set associates key with value.
func (c *Config) Set(key, value string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.values[key] = value
}

// Del removes the value associated with key.
func (c *Config) Del(key string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.values, key)
}

// Write writes config in YAML format to writer w.
func (c *Config) Write(w io.Writer) error {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if err := yaml.NewEncoder(w).Encode(c.values); err != nil {
		return fmt.Errorf("failed to write config: %w", err)
	}
	return nil
}

// WriteFile writes the config to a temporary file in the parent directory of filename, then renames the temporary file
// to filename.
func (c *Config) WriteFile(filename string) error {
	dir := filepath.Dir(filename)
	f, err := os.CreateTemp(dir, "config")
	if err != nil {
		return err
	}
	defer func() {
		f.Close()
		os.Remove(f.Name())
	}()
	if err := c.Write(f); err != nil {
		return err
	}
	if err := f.Close(); err != nil {
		return err
	}
	return os.Rename(f.Name(), filename)
}

// Read configuration in YAML format from reader r.
func Read(r io.Reader) (*Config, error) {
	config := New()
	if err := yaml.NewDecoder(r).Decode(config.values); err != nil {
		return nil, err
	}
	return config, nil
}
