# nuxeo-labs-content-intelligence-connector: Knowledge Enrichment

This part of the plugin connects a [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) application to [**Hyland Content Intelligence**](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) and leverages its [**Knowledge Enrichment**](https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment) APIs.

It provides two kinds of operations handling the calls to the service (see details for each operation below):

* For Enrichment and Data Curation, high-level operations (`HylandKnowledgeEnrichment.Enrich` and `HylandKnowledgeEnrichment.Curate`) that perform all the different individual calls required to get the enrichement/curation for a blob: Get a presigned URL, then upload the file, etc. This makes it easy to call the service.
* For Enrichment only, a low-level operation, `HylandKnowledgeEnrichment.Invoke`, that calls the service and returns the JSON response without adding any logic. This is for flexibility: When/if Hyland Content Intelligence adds new endpoints, and/or adds/changes endpoint expected payload, no need to wait for a new version the plugin, just modify the caller (in most of our usages, Nuxeo Studio project and JavaScript Automation).


> [!NOTE]
>In all cases, the plugin handles authentication, you never need to handle it (see below).


> [!TIP]
> Examples of Nuxeo JavaScript Automation using the misc. operations described below can be found in the [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md) file.

<br>

## Usage

See `Common/Shared Usage` in the main [README](/README.md).

To summarize, every call returns a Blob, stringified JSON object that has at least 3 fields:

* `responseCode`: Integer, the HTTP code returned by the service (should be 200)
* `responseMessage`: String, the HTTP message returned by the service. Should be "OK",
* `response`: String. The JSON String as returned by the service, with no alteration.

> [!TIP]
> To get this JSON string, you must first call the getString() method on the returned blob.

<br>

## About Knowledge Enrichment V2

Since versions 2025.9/2023.12, the plugin supports the new API V2 of Knowledge Enrichment, allowing for passing/using "instructions". As this requests to send the json payload in a [different format](https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/Reference/Context%20API/migration-guide-v1-to-v2), the plugin uses the following to decide between v1 or v2 format:

* By default, it sues the v1, original, format
* To use V2 you need to explicitely tell it to do so:
  * Set the `nuxeo.hyland.cic.enrichment.v2` Nuxeo configuration parameter to `true`: `nuxeo.hyland.cic.enrichment.v2=true`. This will make the plugin use V2 for every call.
  * Use the `HylandKnowledgeEnrichment.Configure` operation and set the `useKEV2` parameter to `true`. (see its doc. below)

<br>

## Know Limitation

* The service allows sending/handling files in batch, the plugin, for now, handles sending several files only for the Enrich operation (see below).

<br>

## Nuxeo Configuration Parameters and Service Contributions

The service for calling Enrichment/Data curation is configurable: You can contribute several XML extensions, each with a different `name`, then use this `name` when calling one of the operations. The plugin will then authenticate and connect to a `baseUrl` using the specified configuration.

The plugin provides a `"default"` configuration, that uses the following configuration parameters that you should set in nuxeo.conf if you want to call only one possible application in Hyland Content Intelligence Cloud.

#### Configuration Parameters

* `nuxeo.hyland.cic.auth.baseUrl`: The authentication endpoint. The plugin adds the "/connect/token" final path. So your URL is something like `https://auth.etc.etc.hyland.com/idp` (This is the same parameter as for Knowledge Enrichment)
* For Knowledge Enrichment:
  * `nuxeo.hyland.cic.contextEnrichment.baseUrl`: The enrichment base URL (endpoints, like `"/content/process"` will be added to this URL).
  * `nuxeo.hyland.cic.enrichment.auth.grantType`: The grant type (default `"client_credentials"` in default configuration)
  * `nuxeo.hyland.cic.enrichment.auth.scope`: Tge scope when authenticating.
  * This value changes, sometime abruptly, so check CIC announcment and check this first when your calls start to fail with authentication error.
  * As default value may often change we don't put it in README, see latest default values at `service-enrichment-contrib.xml`
  * `nuxeo.hyland.cic.enrichment.clientId`: Your enrichment clientId
  * `nuxeo.hyland.cic.enrichment.clientSecret`: Your enrichment client secret
* For Data Curation:
  * `nuxeo.hyland.cic.dataCuration.baseUrl`: The Data Curation base URL
  * `nuxeo.hyland.cic.dataCuration.auth.grantType`: The grant type (default `"client_credentials"` in default configuration)
  * `nuxeo.hyland.cic.dataCuration.auth.scope`: Tge scope when authenticating.
  * This value changes, sometime abruptly, so check CIC announcment and check this first when your calls start to fail with authentication error.
  * As default value may often change we don't put it in README, see latest defailt value at `service-dataCuration-contrib.xml`
  * `nuxeo.hyland.cic.datacuration.clientId`: Your data curation clientId
  * `nuxeo.hyland.cic.datacuration.clientSecret`: Your data curation client secret

Other parameters are used to tune the behavior (independant to the service configuration):
* As of now, getting the results is asynchronous and we need to poll and check if they are ready. The following parameters are used in a loop, where if the service does not return a "success" HTTP Code, the thread sleeps a certain time then tries again, until a certain number of tries:
  * `nuxeo.hyland.cic.pullResultsMaxTries`, an interger max number of tries. Default value is `10`.
  * `nuxeo.hyland.cic.pullResultsSleepInterval`: an integer, the sleep value in milliseconds. Default value is 3000
  
  So, with these default values, the code will try maximum 10 times and it will take about 30s max.

* `nuxeo.hyland.cic.enrichment.v2`, a boolean that tells the plugin to use the Knowledge Enrichment v2 format for the JSON paylod of all requests. Default is `false`.

#### XML Contribution

You can contribute the `"knowledgeEnrichment"` or the `"dataCuration"` points of the `"org.nuxeo.labs.hyland.content.intelligence.HylandKEService"` service.

> [!TIP]
> If you plan to use only one CIC app, you don't need to contribute XML, you just set the nuxeo.conf parameters values

Here are the two `"default"` contributions for each.

```xml
<!-- Default contributions use configuration parameters -->
<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandKEService"
  point="knowledgeEnrichment">
  <knowledgeEnrichment>
    <name>default</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>${nuxeo.hyland.cic.contextEnrichment.baseUrl:=}</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.enrichment.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>${nuxeo.hyland.cic.enrichment.auth.scope:=environment_authorization}</tokenScope>
    <clientId>${nuxeo.hyland.cic.enrichment.clientId:=}</clientId>
    <clientSecret>${nuxeo.hyland.cic.enrichment.clientSecret:=}</clientSecret>
  </knowledgeEnrichment>
</extension>

<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandKEService"
  point="dataCuration">
  <dataCuration>
    <name>default</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>${nnuxeo.hyland.cic.dataCuration.baseUrl:=}</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.dataCuration.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>${nuxeo.hyland.cic.dataCuration.auth.scope:=environment_authorization}</tokenScope>
    <clientId>${nuxeo.hyland.cic.datacuration.clientId:=}</clientId>
    <clientSecret>${nuxeo.hyland.cic.datacuration.clientSecret:=}</clientSecret>
  </dataCuration>
</extension>
```

You could, for example, add more, like:

```xml
<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandKEService"
  point="knowledgeEnrichment">
  <knowledgeEnrichment>
    <name>otherEnrichmentApp</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>https://some.other.enrichment.base.url.com</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.enrichment.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>${nuxeo.hyland.cic.enrichment.auth.scope:=environment_authorization}</tokenScope>
    <clientId>123456-abcdef-890-...</clientId>
    <clientSecret>098765-jhgfds-etc.</clientSecret>
  </knowledgeEnrichment>
</extension>

<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandKEService"
  point="dataCuration">
  <dataCuration>
    <name>otherDCApp</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>https://yet-another-baseUrl-app.com</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.dataCuration.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>${nuxeo.hyland.cic.dataCuration.auth.scope:=environment_authorization}</tokenScope>
    <clientId>another-client-id-here . . .. . .</clientId>
    <clientSecret>another-secret-here. . .</clientSecret>
  </dataCuration>
</extension>
```
Now, when calling one of the misc.operations, you can pass "otherEnrichmentApp" in the `configName` parameter.

> [!TIP]
> When you pass no value (`null` or `""`), the code uses the `"default"` configuration.

#### Error-Check

At startup, if some parameters are missing, the plugin logs a WARN. For example, if you do not provide an Enrichment clientId:

```
No configuration found for Data Curation. Calls, if any, will fail.
```

<br>

## Authentication to the Service

This part is always handled by the plugin, using the different info provided in the configuration parameters (auth. end point + grantType + scope + clientId + clientSecret).

The service returns a token valid a certain time: The plugin handles this timeout (so as to avoid requesting a token at each call, saving some loads)

<br>

## Operations

> [!TIP]
> As of "today" (July 2025), CIC Knowledge Enrichment service accepts only PDF for text-based files (`text-classification`, `text-summarization`, etc.).
> For text-based files enrichment, do not forget to first convert to PDF (see [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md))

* `HylandKnowledgeEnrichment.Enrich`
* `HylandKnowledgeEnrichment.EnrichSeveral`
* `HylandKnowledgeEnrichment.SendForEnrichment`
* `HylandKnowledgeEnrichment.GetEnrichmentResults`
* `HylandKnowledgeEnrichment.Invoke`
* `HylandKnowledgeEnrichment.Curate`
* `HylandKnowledgeEnrichment.Configure`


## About the "instructions" property of Knowledge Enrichment V2

Since V2, the format of the JSON payload has changed and now an "instructions" field can be passed along with every action (except embeddings). If you want to use this new property:

* It must be in the `"instructions"` property of the `extraJsonPayloadStr` parameter
* It is an object of objects, one per action

For example, when requesting `textClassification` and `textSummarization`, then `extraJsonPayloadStr` could be:

```
 {
   "instructions": {
     "textClassification": {
       "context": "legal documents",
       . . . more instructions . . .
     },
     "textSummarization: {
       "tone": "professional",
       . . . more instructions . . .
     }
  }
}
```


### `HylandKnowledgeEnrichment.Enrich`

A high level operation that handles all the different calls to the service (get a token -> get a presigned URL -> upload the file -> call for "process actions" -> get the result)

* Input: `blob`
* Output: `Blob`, a JSON blob
* Parameters
  * `actions`: String required. A list of comma separated actions to perform. See KE documentation about available actions
  * `classes`: String, optional.  A list of comma separated classes, to be used with some classification actions (can be ommitted or null for other actions)
  * `similarMetadata`: String, optional.  A JSON Array (as string) of similar metadata (array of key/value pairs). To be used with the misc. "metadata" actions.
  * `extraPayloadJsonStr`: String, optional. A JSON object as string, with extra parameters for the service. For example, use "maxWordCount" to increase or decrease the text-summary. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload.
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

> [!NOTE]
> Again, please, see Knowledge Enrichment API documentation for details on the values that can be used/passed.

The operation calls the service and returns a JSON Blob, that contains the object described above (See Usage).

> [!NOTE]
> Reminder: To get the JSON string from this blob, you must call its `getString()` method (see examples below). Then you can `JSON.parse` this string

> [!TIP]
> For examples of JS Automation: See [JS Automation Examples](/README-Enrichment-JS-Automation-Examples.md)


### `HylandKnowledgeEnrichment.EnrichSeveral`

This operation performs the same features as `HylandKnowledgeEnrichment.Enrich`, but allows for hanlding several files in batch.

* Input: `documents` or `blobs`
* Output: `Blob`, a JSON blob
* Parameters
  * `actions`: String required. A list of comma separated actions to perform. See KE documentation about available actions
  * `classes`: String, optional.  A list of comma separated classes, to be used with some classification actions (can be ommitted or null for other actions)
  * `similarMetadata`: String, optional.  A JSON Array (as string) of similar metadata (array of key/value pairs). To be used with the misc. "metadata" actions.
  * `extraPayloadJsonStr`: String, optional. A JSON object as string, with extra parameters for the service. For example, use "maxWordCount" to increase or decrease the text-summary. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload.
  *  `xpath`: String, optional. When input is `document`, the xpath to use to get the blob. Default "file:content".
  * `sourceIds`: String, required if input is `blobs`. A comma separated list of unique ID, one for each input object (Document of Blob), _in the same order_. If input is `document` and `sourceIds`is not passed, the plugin uses each Document UUID. See below for more details. 
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

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
  * `extraPayloadJsonStr`: String, optional. A JSON object as string, with extra parameters for the service. For example, use "maxWordCount" to increase or decrease the text-summary. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload.
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The `response` property of the result JSON (if succesfull) will hhave a `processingId` property, to be saved and used later with `HylandKnowledgeEnrichment.GetEnrichmentResults`


### `HylandKnowledgeEnrichment.GetEnrichmentResults`

(See `HylandKnowledgeEnrichment.SendForEnrichment` for details)

After calling `HylandKnowledgeEnrichment.SendForEnrichment`, you need to get the results.

* Input: `void``
* output `Blob`, a JSON blob
* Parameters
  * `jobId`: String, required. The value returned in the JSON after a call to `HylandKnowledgeEnrichment.SendForEnrichment`
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The operation gets the results for the job ID. Notice you have to wait for an HTTP response of OK with the status "Done". before this, you may get dirrerent steps ("acceoted", "processing", ...)


### `HylandKnowledgeEnrichment.Invoke`

A low level operation, for which you must provide the correct endpoints, correct headers etc.

* Input: `blob`
* Output: `Blob`, a JSON blob
* Parameters
  * `httpMethod`: String, required, "GET", "PUT" or "POST"
  * `endpoint`: String, required, the endpoint to call. This string will be just appended to the Content Enrichment Endpoint URL you set up in the configuration parameters.
  * `jsonPayload`: String, optjonal. A JSON string for POST/PUT request, depending on the endpoint.
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

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
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.
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
  * `useKEV2`: Boolean, optional. Allows for setting the format/behavior when calling KE (it can also be set with the `nuxeo.hyland.cic.enrichment.v2` configuraiton parameter). Once set, it is used for all and every calls (no need to call this operation before calling other operations)

<br>

## How to build
```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-hyland-knowledge-enrichment-connector
cd nuxeo-hyland-knowledge-enrichment-connector
mvn clean install
```

You can add the `-DskipDocker` flag to skip building with Docker.

Also you can use the `-DskipTests` flag.

> [!IMPORTANT]
> The Marketplace Package ID is `nuxeo-labs-knowledge-enrichment-connector`, not `nuxeo-hyland-knowledge-enrichment-connector`
