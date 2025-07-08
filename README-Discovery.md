# nuxeo-labs-content-intelligence-connector: Knowledge Discovery

This part of the plugin connects a [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) application to [**Hyland Content Intelligence**](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) and leverages its [**Knowledge Discovery**](https://hyland.github.io/ContentIntelligence-Docs/KnowledgeDiscovery) APIs.

It provides two kinds of operations handling the calls to the service (see details for each operation below):

* For ease of use, High-level operations (`HylandKnowledgeDiscovery.getAllAgents` and `HylandKnowledgeDiscovery.askQuestionAndGetAnswer`) that perform all the different individual calls required to get the answer from the service. No need to know the exact endpoints and JSON payload, etc.
* A low-level operation, `HylandKnowledgeDiscovery.Invoke`, that calls the service and returns the JSON response without adding any logic. This is for flexibility: When/if Hyland Content Intelligence adds new endpoints, and/or adds/changes endpoint expected payload, no need to wait for a new version of the plugin, just modify the caller (in most of our usages, Nuxeo Studio project and JavaScript Automation).

> [!NOTE]
>In all cases, the plugin handles authentication, you never need to handle it (see below).

> [!TIP]
> Examples of Nuxeo JavaScript Automation using the misc. operations describeb below can be found in the [JS Automation Examples](/README-Discovery-JS-Automation-Examples.md) file.

<br>

## Usage

See `Usage` in the main [README](/README.md).

To summarize, every call returns a Blob, stringified JSON object that has at least 3 fields:

* `responseCode`: Integer, the HTTP code returned by the service (should be 200)
* `responseMessage`: String, the HTTP message returned by the service. Should be "OK",
* `response`: String. The JSON String as returned by the service, with no alteration.

> [!TIP]
> To get this JSON string, you must first call the getString() method on the returned blob.

<br>


## Nuxeo Configuration Parameters

For calling the CIC Discovery service, you need to setup the following configuration parameters in nuxeo.conf.

* `nuxeo.hyland.cic.endpoint.auth`: The authentication endpoint. The plugin adds the "/connect/token" final path. So your URL is something like https://auth.etc.etc.hyland.com/idp
* `nuxeo.hyland.cic.endpoint.contextEnrichment`: The enrichment endpoint.
* `nuxeo.hyland.cic.endpoint.dataCuration`: The Data Curation endpoint.
* `nuxeo.hyland.cic.enrichment.clientId`: Your enrichment clientId
* `nuxeo.hyland.cic.enrichment.clientSecret`: Your enrichment client secret
* `nuxeo.hyland.cic.datacuration.clientId`: Your data curation clientId
* `nuxeo.hyland.cic.datacuration.clientSecret`: Your data curation client secret

Other parameters are used to tune the behavior:
* As of now, getting the results is asynchronous and we need to poll and check if they are ready. The following parameters are used in a loop, where if the service does not return a "success" HTTP Code, the thread sleeps a certain time then tries again, until a certain number of tries:
  * `nuxeo.hyland.cic.pullResultsMaxTries`, an interger max number of tries. Default value is `10`.
  * `nuxeo.hyland.cic.pullResultsSleepInterval`: an integer, the sleep value in milliseconds. Default value is 3000
  
  So, with these default values, the code will try maximum 10 times and it will take about 30s max.

At startup, if some parameters are missing, the plugin logs a WARN. For example, if you do not provide a Data Curation clientId:

```
WARN  [main] [org.nuxeo.labs.hyland.knowledge.enrichment.service.HylandKEServiceImpl] No CIC Data Curation ClientId provided (nuxeo.hyland.cic.datacuration.clientId), calls to the service will fail.
```

## Authentication to the Service

This part is always handled by the plugin, using the different info provided in the configuration parameters (auth. end point + clientId + clientSecret).

The service returns a token valid a certain time: The plugin handles this timeout (so as to avoid requesting a token at each call, saving some loads)


## Operations

> [!TIP]
> As of "today" (June 2025), CIC Knowledge Enrichment service accepts only PDF for text-based files (`text-classification`, `text-summarization`, etc.).
> For text-based files enrichment, do not forget to first convert to PDF (see [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md))

* `HylandKnowledgeEnrichment.Enrich`
* `HylandKnowledgeEnrichment.EnrichSeveral`
* `HylandKnowledgeEnrichment.SendForEnrichment`
* `HylandKnowledgeEnrichment.GetEnrichmentResults`
* `HylandKnowledgeEnrichment.Invoke`
* `HylandKnowledgeEnrichment.Curate`
* `HylandKnowledgeEnrichment.Configure`


### `HylandKnowledgeEnrichment.Enrich`

A high level operation that handles all the different calls to the service (get a token -> get a presigned URL -> upload the file -> call for "process actions" -> get the result)

* Input: `blob`
* Output: `Blob`, a JSON blob
* Parameters
  * `actions`: String required. A list of comma separated actions to perform. See KE documentation about available actions
  * `classes`: String, optional.  A list of comma separated classes, to be used with some classification actions (can be ommitted or null for other actions)
  * `similarMetadata`: String, optional.  A JSON Array (as string) of similar metadata (array of key/value pairs). To be used with the misc. "metadata" actions.
  * `extraJsonPayloadStr`: String, optional. A JSON object as string, with extra parameters for the service. For example, use "maxWordCount" to increase or decrease the text-summary. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload. 

> [!NOTE]
> Again, please, see Knowledge Enrichment API documentation for details on the values that can be used/passed.

The operation calls the service and returns a JSON Blob, that contains the object described above (See Usage).

> [!NOTE]
> Reminder: To get the JSON string from this blob, you must call its `getString()` method (see examples below). Then you can `JSON.parse` this string

> [!TIP]
> For examples of JS Automation: See [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md)


### `HylandKnowledgeEnrichment.EnrichSeveral`

This operation performs the same featieres as `HylandKnowledgeEnrichment.Enrich`, but allows for hanlding several files in batch.

* Input: `documents` or `blobs`
* Output: `Blob`, a JSON blob
* Parameters
  * `actions`: String required. A list of comma separated actions to perform. See KE documentation about available actions
  * `classes`: String, optional.  A list of comma separated classes, to be used with some classification actions (can be ommitted or null for other actions)
  * `similarMetadata`: String, optional.  A JSON Array (as string) of similar metadata (array of key/value pairs). To be used with the misc. "metadata" actions.
  * `extraJsonPayloadStr`: String, optional. A JSON object as string, with extra parameters for the service. For example, use "maxWordCount" to increase or decrease the text-summary. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload.
  *  `xpath`: String, optional. When input is `document`, the xpath to use to get the blob. Default "file:content".
  * `sourceIds`: String, required if input is `blobs`. A comma separated list of unique ID, one for each input object (Document of Blob), _in the same order_. If input is `document` and `sourceIds`is not passed, the plugin uses each Document UUID. See below for more details. 

> [!IMPORTANT]
> Make sure the files are of the same kind, supporting the `actions` request. For example, do not mix images and PDFs if you ask for image-description. Or do not pass images and PDFs ans ask for both image-description and text-summarization. This is because the service, in this case, will return a global PARTIAL_FAILURE, and for each file, a failure for the requested action when the file is not of the good type.

#### About `sourceIds`
When calling the service for several files, it returns an array of results. Each single result holds a property, `objectKey`, that is unique. The plugin also returns an array used for the mapping. This arrays is stored in the `objectKeysMapping` property and contains objects with 2 fields:

* `sourceId`: The value as it was passed
* `objectKey`:  The corresponding `objectKey`.

This way, when looping the results, for each result you can:

1. Get the `objectId` of the content that was processed
2. Find this value in the `objectKeysMapping`
3. Act accordingly (typically, get a the corresponding document, store values in fields)

> [!TIP]
> For example(s) of JS Automation: See [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md).

### `HylandKnowledgeEnrichment.SendForEnrichment`

`HylandKnowledgeEnrichment.Enrich` performs all the tasks and calls required to send a file and pull the results. Sometimes, it maybe interesting to split these actions in 2 parts:

1. Send a file and request action(s)
2. Then pull the result and check status from Nuxeo.

This can be interesting when you need more fine tuning or when you know the processing could lead to a time out from the plugin (not the srevice. See `nuxeo.hyland.cic.pullResultsMaxTries`, `nuxeo.hyland.cic.pullResultsSleepInterval` and the `HylandKnowledgeEnrichment.Configure` operation)

`HylandKnowledgeEnrichment.Enrich` acts as `HylandKnowledgeEnrichment.Enrich`, excepts it:
* Accepts an extra optional parameter (`sourceId`)
* Returns before pulling result. The returned JSON contains a processingId field to be used with call(s) to `HylandKnowledgeEnrichment.GetEnrichmentResults`.

> [!WARNING]
> See CIC KnowledgeEnricgment documentation: File and results are ephemeral in the service, and destroyed after a delay (o24h at the time this documentaiton is written).

* Input: `blob`
* Output: `Blob`, a JSON blob
* Parameters
  * `sourceId`: String, optional. See explanation of sourceId with `HylandKnowledgeEnrichment.EnrichSeveral`.
    * Typically, if you are building a background process that loops on results to fetch, you will pass the UUID of a document, so you can retrieve it via the use of the `objectKeysMapping` property.
    * If this parameter is empty, a cusotm UUID will be created by the plugin. It starts with `CUSTOM_ID-`, so you can make the difference between this UUID and the UUID of a document.
  * `actions`: String required. A list of comma separated actions to perform. See KE documentation about available actions
  * `classes`: String, optional.  A list of comma separated classes, to be used with some classification actions (can be ommitted or null for other actions)
  * `similarMetadata`: String, optional.  A JSON Array (as string) of similar metadata (array of key/value pairs). To be used with the misc. "metadata" actions.
  * `extraJsonPayloadStr`: String, optional. A JSON object as string, with extra parameters for the service. For example, use "maxWordCount" to increase or decrease the text-summary. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload. 

The `response` property of the result JSON (if succesfull) will hhave a `processingId` property, to be saved and used later with `HylandKnowledgeEnrichment.GetEnrichmentResults`


### `HylandKnowledgeEnrichment.GetEnrichmentResults`

(See `HylandKnowledgeEnrichment.SendForEnrichment` for details)

After calling `HylandKnowledgeEnrichment.SendForEnrichment`, you need to get the results.

* Input: `void``
* output `Blob`, a JSON blob
* Parameters
  * `jobId`: String, required. The value returned in the JSON after a call to `HylandKnowledgeEnrichment.SendForEnrichment`

The operation gets the results for the job ID. Notice you have to wait for an HTTP response of OK with the status "Done". before this, you may get dirrerent steps ("acceoted", "processing", ...)


### `HylandKnowledgeEnrichment.Invoke`

A low level operation, for which you must provide the correct endpoints, correct headers etc.

* Input: `blob`
* Output: `Blob`, a JSON blob
* Parameters
  * `httpMethod`: String, required, "GET", "PUT" or "POST"
  * `endpoint`: String, required, the endpoint to call. This string will be just appended to the Content Enrichment Endpoint URL you set up in the configuration parameters.
  * `jsonPayload`: String, optjonal. A JSON string for POST/PUT request, depending on the endpoint.

The operation calls the Enrichment service (after handling authentication) and returns the result. See above for the structure of returned JSON.

To uplao a fgile to the service, you will first get a presigned URL then use the `HylandKnowledgeEnrichment.UploadFile` operation.

### `HylandKnowledgeEnrichment.UploadFile``

A low level operation to be used after a succesful call to `HylandKnowledgeEnrichment.Invoke`, using the endpoint returning a presigned URL.

* Input: `blob`
* Output: `blob`, a JSON blob
* Parameters
  * presignedUrl: `string`, required. The presigned URL where to send the file. You previousely used `HylandKnowledgeEnrichment.Invoke` with the correct HTTP method, endpoint, etc.
  * mimeType: `string`, optionnal. If not passed, we get it from the blob

Return the usual JSON result, but `response` is always an empty object. You must check the `responseCode`, that must be 200.


### `HylandKnowledgeEnrichment.Curate`

A high-level operation that handles all he flow to call Data Curation for a file and get the results.

A high level operation that handles all the different calls to the service (get a token -> get a presigned URL -> upload the file -> call for "process actions" -> get the result)

* Input: `blob`
* Output: `Blob`, a JSON blob
* Parameters
  * `jsonOptions`: String optional. A JSON string holding the options for calling the service. See the Data Curation API documentation for a list of possible values. If the parameter is not passed (or `null`), default values are applied, getting every info and using the `MDAST` JSON schema:

```JSON
{
  "normalization": {
    "quotations": true
  },
  "chunking": true,
  "embedding": false,
  "json_schema": "MDAST"
}
```

The difference between the misc. `json_schema` can be [checked here](https://hyland.github.io/DocumentFilters-Docs/latest/getting_started_with_document_filters/about_json_output.html#json_output_schema). 

Also check the Data Curation API documentation for the JSON result. As of May 2025, with the above JSON Options, it will be somethign like (after uploading a sample example of contract as pdf):

```javascript
// We used the MDAST json_schema.
{
  "responseCode": 200,
  "responseMessage": "OK"
  "response": {
    "markdown": {
      "output": "[here the text of the input blob]",
      "chunks": [
        . . . // An array of text, split from the input blob
      ]
    },
    "json": {
      "type": "root",
      "children": [
        {
          "type": "paragraph",
          "children": [
            {
              "type": "strong",
              "children": [
                {
                  "type": "text",
                  "value": "Samples are provided for reference only" // etc., extract from the sample contact
                }
              ]
            }
          ]
        },
        {
          "type": "heading",
          "children": [
            {
              "type": "strong",
              "children": [
                {
                  "type": "text",
                  "value": "SAMPLE AGREEMENT" // etc., extract from the sample contact
                }
              ]
            }
          ]
        },
        . . . // etc.
      ]
    }
```

> [!NOTE]
> Again, please, see Knowledge Enrichment API documentation for details on the values that can be used/passed.

The operation calls the service and returns a JSON Blob, that contains the object described above (See Usage).

> [!NOTE]
> Reminder: To get the JSON string from this blob, you must call its `getString()` method (see examples below). Then you can `JSON.parse` this string

> [!TIP]
> For example(s) of JS Automation: See [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md).

### `HylandKnowledgeEnrichment.Configure`

This operation allows for dynamically configuring some properties used by the plugin to call the service. The changes, if any, are immediate and apply for all the calls.

See _Nuxeo Configuration Parameters_ above for explanation on the values.

* Input: `void`
* Output: `void`
* Parameters
  * `maxTries`: Integer, optional. Set the max number of tries when pulling results.
    * If 0 => reset to configuration parameter. If no config. param is set, use default value.
    * If -1 => Do not change (same effect as not passing the parameter)
    * Other values set the parameter (make sure you don't pass a negative value)
  * `sleepIntervalMS`: Integer, optional. Set the sleep interval between 2 tries when pulling results.
    * If 0 => reset to configuration parameter. If no config. param is set, use default value.
    * If -1 => Do not change (same effect as not passing the parameter)
    * Other values set the parameter (make sure you don't pass a negative value)





## Support
**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning
resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be
useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.


## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)


## About Nuxeo
Nuxeo Platform is an open source Content Services platform, written in Java. Data can be stored in both SQL & NoSQL
databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions
for [document management](https://www.nuxeo.com/solutions/document-management/), [case management](https://www.nuxeo.com/solutions/case-management/),
and [digital asset management](https://www.nuxeo.com/solutions/dam-digital-asset-management/), use cases. It uses
schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.nuxeo.com).
