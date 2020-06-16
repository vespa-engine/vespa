# Vespa library for data analysis
> Provide data analysis support for Vespa applications


## Install

`pip install pyvespa`

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
query_result.number_documents_retrieved
```

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
