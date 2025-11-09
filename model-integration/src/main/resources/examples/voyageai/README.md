# VoyageAI Embedder Example

This example demonstrates how to use the VoyageAI embedder in Vespa for semantic search.

## Setup

### 1. Get VoyageAI API Key

Sign up at [voyageai.com](https://www.voyageai.com/) and get your API key.

### 2. Add API Key to Secret Store

```bash
vespa secret add voyage_api_key --value "pa-xxxxx..."
```

### 3. Deploy Application

```bash
vespa deploy /path/to/this/directory
```

## Example Queries

### Semantic Search

```bash
vespa query \
  'yql=select * from article where {targetHits:10}nearestNeighbor(title_embedding,q_embedding)' \
  'input.query(q_embedding)=embed(voyage-embedder, "machine learning tutorials")' \
  'ranking=semantic'
```

### Hybrid Search (Text + Semantic)

```bash
vespa query \
  'yql=select * from article where userQuery() or {targetHits:10}nearestNeighbor(title_embedding,q_embedding)' \
  'query=machine learning' \
  'input.query(q_embedding)=embed(voyage-embedder, "machine learning tutorials")' \
  'ranking=hybrid'
```

### Filter + Semantic

```bash
vespa query \
  'yql=select * from article where url contains "example.com" and {targetHits:10}nearestNeighbor(title_embedding,q_embedding)' \
  'input.query(q_embedding)=embed(voyage-embedder, "your search query")' \
  'ranking=semantic'
```

## Example Documents

### Feed Document

```json
{
  "put": "id:article:article::1",
  "fields": {
    "title": "Introduction to Machine Learning",
    "body": "Machine learning is a subset of artificial intelligence...",
    "url": "https://example.com/ml-intro"
  }
}
```

```bash
vespa document put article1.json
```

### Feed Multiple Documents

```bash
cat articles.jsonl | vespa document
```

## Configuration Variants

### Use voyage-3-lite (Faster, Lower Cost)

In `services.xml`, change:
```xml
<model>voyage-3-lite</model>
```

And update schema dimensions to `512`:
```sdl
field title_embedding type tensor<float>(d0[512])
```

### Use Code Embedder for Technical Content

```xml
<component id="code-embedder" type="voyage-ai-embedder">
  <model>voyage-code-3</model>
  <api-key-secret-name>voyage_api_key</api-key-secret-name>
</component>
```

## Performance Tuning

### High Throughput

```xml
<max-batch-size>128</max-batch-size>
<cache-size>10000</cache-size>
<pool-size>20</pool-size>
```

### Low Latency

```xml
<max-batch-size>1</max-batch-size>
<cache-size>5000</cache-size>
<timeout>10000</timeout>
```

## Monitoring

Check VoyageAI dashboard for:
- Request count
- Token usage
- Costs
- Rate limits

## Troubleshooting

See [VoyageAI Embedder Documentation](../../VOYAGEAI_EMBEDDER.md) for detailed troubleshooting guide.

## Next Steps

- Experiment with different models (voyage-3, voyage-3-lite, voyage-code-3)
- Tune batch size and cache size for your use case
- Add more fields with embeddings
- Implement hybrid ranking (text + semantic)
