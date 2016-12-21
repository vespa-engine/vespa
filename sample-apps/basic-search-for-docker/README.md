Basic Search Application
==================
Start by [deploying a sample application](http://vespa.corp.yahoo.com/6/documentation/developing-with-vespa.html).

### Find the endpoint

When you have successfully deployed your own compiled version of the application above, you need to find the name of the "endpoint".
The endpoint is used for feeding and searching for data.
**Please allow a few minutes for the endpoint to appear after deployment**
You can find this endpoint by doing:
 ```sh

 mvn vespa:endpoints | grep Endpoints
 ```

You can also find it by looking at the [Hosted Vespa Dashboard](http://dashboard.vespa.corp.yahoo.com).


### Feed and search
 1. **Feed** the data that is to be searched
 ```sh

 # Feeding two documents
 curl -X POST --data-binary  @music-data-1.json <endpoint url>/document/v1/music/music/docid/1 | python -m json.tool
 curl -X POST --data-binary  @music-data-2.json <endpoint url>/document/v1/music/music/docid/2 | python -m json.tool

  ```

For feeding many documents fast and reliable, checkout [feeding example](https://git.corp.yahoo.com/vespa-samples/basic-feeding-client)

 2. **Visit documents

 Since we do not have many documents we can list them all
 ```sh

 # All documents
 curl <endpoint url>/document/v1/music/music/docid | python -m json.tool

 # Document with id 1
 curl <endpoint url>/document/v1/music/music/docid/1 | python -m json.tool

  ```

 3. **Search**
 We can also search for documents:
    ```sh

    curl '<endpoint url>/search/?query=bad' | python -m json.tool


    ```

### Next step: from development to production
See [continuous deployments](http://vespa.corp.yahoo.com/6/documentation/continuous-deployment.html) for how to implement continuous deployments for production.
See [RESTified Document Operation API](http://vespa.corp.yahoo.com/6/documentation/document_api_v1.html) for documentation about the REST API for document operations.
