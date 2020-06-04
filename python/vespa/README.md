# Vespa library for data analysis
> Provide data analysis support for Vespa applications


## Install

`pip install vespa`

## Connect to a Vespa app

> Connect to a running Vespa application

```
from vespa.application import Vespa

app = Vespa(url = "https://api.cord19.vespa.ai")
```

## Define a Query model

> Easily define matching and ranking criteria

```
from vespa.query import Query, Union, WeakAnd, ANN, RankProfile
from random import random

match_phase = Union(
    WeakAnd(hits = 10), 
    ANN(
        doc_vector="title_embedding", 
        query_vector="title_vector", 
        embedding_model=lambda x: [random() for x in range(768)],
        hits = 10,
        label="title"
    )
)

rank_profile = RankProfile(name="bm25", list_features=True)

query_model = Query(match_phase=match_phase, rank_profile=rank_profile)
```

## Query the vespa app

> Send queries via the query API. See the [query page](/vespa/query) for more examples.

```
query_result = app.query(
    query="Is remdesivir an effective treatment for COVID-19?", 
    query_model=query_model
)
```

```
query_result["root"]["fields"]
```




    {'totalCount': 1077}



## Labelled data

> How to structure labelled data

```
labelled_data = [
    {
        "query_id": 0, 
        "query": "Intrauterine virus infections and congenital heart disease",
        "relevant_docs": [{"id": 0, "score": 1}, {"id": 3, "score": 1}]
    },
    {
        "query_id": 1, 
        "query": "Clinical and immunologic studies in identical twins discordant for systemic lupus erythematosus",
        "relevant_docs": [{"id": 1, "score": 1}, {"id": 5, "score": 1}]
    }
]
```

Non-relevant documents are assigned `"score": 0` by default. Relevant documents will be assigned `"score": 1` by default if the field is missing from the labelled data. The defaults for both relevant and non-relevant documents can be modified on the appropriate methods.

## Collect training data

> Collect training data to analyse and/or improve ranking functions. See the [collect training data page](/vespa/collect_training_data) for more examples.

```
training_data_batch = app.collect_training_data(
    labelled_data = labelled_data,
    id_field = "id",
    query_model = query_model,
    number_additional_docs = 2
)
training_data_batch
```




<div>
<style scoped>
    .dataframe tbody tr th:only-of-type {
        vertical-align: middle;
    }

    .dataframe tbody tr th {
        vertical-align: top;
    }

    .dataframe thead th {
        text-align: right;
    }
</style>
<table border="1" class="dataframe">
  <thead>
    <tr style="text-align: right;">
      <th></th>
      <th>attributeMatch(authors.first)</th>
      <th>attributeMatch(authors.first).averageWeight</th>
      <th>attributeMatch(authors.first).completeness</th>
      <th>attributeMatch(authors.first).fieldCompleteness</th>
      <th>attributeMatch(authors.first).importance</th>
      <th>attributeMatch(authors.first).matches</th>
      <th>attributeMatch(authors.first).maxWeight</th>
      <th>attributeMatch(authors.first).normalizedWeight</th>
      <th>attributeMatch(authors.first).normalizedWeightedWeight</th>
      <th>attributeMatch(authors.first).queryCompleteness</th>
      <th>...</th>
      <th>textSimilarity(results).queryCoverage</th>
      <th>textSimilarity(results).score</th>
      <th>textSimilarity(title).fieldCoverage</th>
      <th>textSimilarity(title).order</th>
      <th>textSimilarity(title).proximity</th>
      <th>textSimilarity(title).queryCoverage</th>
      <th>textSimilarity(title).score</th>
      <th>document_id</th>
      <th>query_id</th>
      <th>relevant</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <th>0</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.000000</td>
      <td>0.0</td>
      <td>0.000000</td>
      <td>0.000000</td>
      <td>0.000000</td>
      <td>0</td>
      <td>0</td>
      <td>1</td>
    </tr>
    <tr>
      <th>1</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>1.000000</td>
      <td>1.0</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>56212</td>
      <td>0</td>
      <td>0</td>
    </tr>
    <tr>
      <th>2</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.187500</td>
      <td>0.5</td>
      <td>0.617188</td>
      <td>0.428571</td>
      <td>0.457087</td>
      <td>34026</td>
      <td>0</td>
      <td>0</td>
    </tr>
    <tr>
      <th>3</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.000000</td>
      <td>0.0</td>
      <td>0.000000</td>
      <td>0.000000</td>
      <td>0.000000</td>
      <td>3</td>
      <td>0</td>
      <td>1</td>
    </tr>
    <tr>
      <th>4</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>1.000000</td>
      <td>1.0</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>56212</td>
      <td>0</td>
      <td>0</td>
    </tr>
    <tr>
      <th>5</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.187500</td>
      <td>0.5</td>
      <td>0.617188</td>
      <td>0.428571</td>
      <td>0.457087</td>
      <td>34026</td>
      <td>0</td>
      <td>0</td>
    </tr>
    <tr>
      <th>6</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.071429</td>
      <td>0.0</td>
      <td>0.000000</td>
      <td>0.083333</td>
      <td>0.039286</td>
      <td>1</td>
      <td>1</td>
      <td>1</td>
    </tr>
    <tr>
      <th>7</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>1.000000</td>
      <td>1.0</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>29774</td>
      <td>1</td>
      <td>0</td>
    </tr>
    <tr>
      <th>8</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.500000</td>
      <td>1.0</td>
      <td>1.000000</td>
      <td>0.333333</td>
      <td>0.700000</td>
      <td>22787</td>
      <td>1</td>
      <td>0</td>
    </tr>
    <tr>
      <th>9</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.058824</td>
      <td>0.0</td>
      <td>0.000000</td>
      <td>0.083333</td>
      <td>0.036765</td>
      <td>5</td>
      <td>1</td>
      <td>1</td>
    </tr>
    <tr>
      <th>10</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>1.000000</td>
      <td>1.0</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>1.000000</td>
      <td>29774</td>
      <td>1</td>
      <td>0</td>
    </tr>
    <tr>
      <th>11</th>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>...</td>
      <td>0.0</td>
      <td>0.0</td>
      <td>0.500000</td>
      <td>1.0</td>
      <td>1.000000</td>
      <td>0.333333</td>
      <td>0.700000</td>
      <td>22787</td>
      <td>1</td>
      <td>0</td>
    </tr>
  </tbody>
</table>
<p>12 rows × 984 columns</p>
</div>



## Evaluating a query model

> Define metrics and evaluate query models. See the [evaluation page](/vespa/evaluation) for more examples.

We will define the following evaluation metrics:
* % of documents retrieved per query
* recall @ 10 per query
* MRR @ 10 per query

```
from vespa.evaluation import MatchRatio, Recall, ReciprocalRank

eval_metrics = [MatchRatio(), Recall(at=10), ReciprocalRank(at=10)]
```

Evaluate:

```
evaluation = app.evaluate(
    labelled_data = labelled_data,
    eval_metrics = eval_metrics, 
    query_model = query_model, 
    id_field = "id",
)
evaluation
```




<div>
<style scoped>
    .dataframe tbody tr th:only-of-type {
        vertical-align: middle;
    }

    .dataframe tbody tr th {
        vertical-align: top;
    }

    .dataframe thead th {
        text-align: right;
    }
</style>
<table border="1" class="dataframe">
  <thead>
    <tr style="text-align: right;">
      <th></th>
      <th>query_id</th>
      <th>match_ratio_retrieved_docs</th>
      <th>match_ratio_docs_available</th>
      <th>match_ratio_value</th>
      <th>recall_10_value</th>
      <th>reciprocal_rank_10_value</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <th>0</th>
      <td>0</td>
      <td>1267</td>
      <td>62529</td>
      <td>0.020263</td>
      <td>0</td>
      <td>0</td>
    </tr>
    <tr>
      <th>1</th>
      <td>1</td>
      <td>887</td>
      <td>62529</td>
      <td>0.014185</td>
      <td>0</td>
      <td>0</td>
    </tr>
  </tbody>
</table>
</div>


