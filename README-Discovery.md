# nuxeo-labs-content-intelligence-connector: Knowledge Discovery

This part of the plugin connects a [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) application to [**Hyland Content Intelligence**](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) and leverages its [**Knowledge Discovery**](https://hyland.github.io/ContentIntelligence-Docs/KnowledgeDiscovery) APIs.

It provides two kinds of operations handling the calls to the service (see details for each operation below):

* For ease of use, High-level operations (`HylandKnowledgeDiscovery.getAllAgents` and `HylandKnowledgeDiscovery.askQuestionAndGetAnswer`) that perform all the different individual calls required to get the answer from the service. No need to know the exact endpoints and JSON payload, etc.
* A low-level operation, `HylandKnowledgeDiscovery.Invoke`, that calls the service and returns the JSON response without adding any logic. This is for flexibility: When/if Hyland Content Intelligence adds new endpoints, and/or adds/changes endpoint expected payload, no need to wait for a new version of the plugin, just modify the caller (in most of our usages, Nuxeo Studio project and JavaScript Automation).

> [!NOTE]
>In all cases, the plugin handles authentication, you never need to handle it (see below).

<br>

## Usage

See `Common Usage (Both Enrichment and Discovery)` in the main [README](/README.md).

To summarize, every call returns a Blob, stringified JSON object that has at least 3 fields:

* `responseCode`: Integer, the HTTP code returned by the service (should be 200)
* `responseMessage`: String, the HTTP message returned by the service. Should be "OK",
* `response`: String. The JSON String as returned by the service, with no alteration.

> [!TIP]
> To get this JSON string, you must first call the getString() method on the returned blob.

<br>

## Sending Documents to CIC Knowledge Discovery: About Nuxeo HxAI connector

Before asking a quesiton or handling agents from within Nuxeo, you must:

* Have an application ID/Secret and at least one source repository and one agent already setup, all with the correct permissions
* Install on your Nuxeo server the [**Nuxeo HxAI connector**](https://doc.nuxeo.com/nxdoc/2023/nuxeo-connector-content-intelligence/) plugin, which you use for sending documents to the service?

> [!TIP]
> The UUID of the documents sent is stored as the `objectId` in the service and can be used in the `contextObjectIds` optional parameter in some operations.

> [!IMPORTANT]
> This plugin (fully supported) is a bit misnamed, as all it does (an this is a lot, see the documentation with all the available options) is sending your nuxeo documents (binaries/metadata) to the service, but the whole Discovery part of `nuxeo-labs-content-intelligence-connector` has no meaning without files being sent there, to be used for answering questions.
> 
> All the examples assume you have an application, a repository, at least an agent, and at least some files have been sent to CIC.

<br>

## Nuxeo Configuration Parameters and Service Contributions

The service for calling the CIC Discovery service is configurable: You can contribute several XML extensions, each with a different `name`, then use this `name` when calling one of the operations. The plugin will then authenticate and connect to a `baseUrl` using the specified configuration.

The plugin provides a `"default"` configuration, that uses the following configuration parameters that you should set in nuxeo.conf if you want to call only one possible application in Hyland Content Intelligence Cloud.

#### Configuration Parameters

* `nuxeo.hyland.cic.auth.baseUrl`: The authentication endpoint. The plugin adds the "/connect/token" final path. So your URL is something like `https://auth.etc.etc.hyland.com/idp` (This is the same parameter as for Knowledge Enrichment)
* `nuxeo.hyland.cic.discovery.baseUrl`: The Discovery base URL.
* `nuxeo.hyland.cic.discovery.auth.grantType`: The grant type (default `"client_credentials"` in default configuration)
* `nuxeo.hyland.cic.discovery.auth.scope`: Tge scope when authenticating.
  * This value changes, sometime abruptly, so check CIC announcment and check this first when your calls start to fail with authentication error.
  * As default value may often change we don't put it in README, see latest defailt value at `service-discovery-contrib.xml`
* `nuxeo.hyland.cic.discovery.clientId`: Your Discovery clientId
* `nuxeo.hyland.cic.discovery.clientSecret`: Your Discovery client secret
* `nuxeo.hyland.cic.discovery.environment`: The environment

The following are not part of the configurable service:
* `nuxeo.hyland.cic.discovery.default.sourceId`: The source ID to use when none is passed as a parameter
* `nuxeo.hyland.cic.discovery.default.agentId`: The Agent ID to use when none is passed as a parameter

Other parameters are used to tune the behavior:
* Somae calls to Kn owledge Discovery require to poll and check if a result is ready (typically, get the answer to a questionà). The following parameters are used in a loop, where if the service does not return a "success" HTTP Code, the thread sleeps a certain time then tries again, until a certain number of tries:
  * `nuxeo.hyland.cic.discovery.pullResultsMaxTries`, an interger max number of tries. Default value is `10`.
  * `nuxeo.hyland.cic.discovery.pullResultsSleepInterval`: an integer, the sleep value in milliseconds. Default value is 3000
  
  So, with these default values, the code will try maximum 10 times and it will take about 30s max.

#### XML Contribution

You can contribute the `"knowledgeEnrichment"` or the `"dataCuration"` points of the `"org.nuxeo.labs.hyland.content.intelligence.HylandKEService"` service.

> [!TIP]
> If you plan to use only one CIC app, youdon't need to contribute XML, you just set the nuxeo.conf paramerets values

Here are the two `"default"` contributions for each.

```xml
<!-- Default contributions use configuration parameters -->
<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandKDService"
  point="knowledgeDiscovery">
  <knowledgeDiscovery>
    <name>default</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>${nuxeo.hyland.cic.discovery.baseUrl:=}</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.discovery.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>${nuxeo.hyland.cic.discovery.auth.scope:=hxp hxp.integrations environment_authorization}</tokenScope>
    <clientId>${nuxeo.hyland.cic.discovery.clientId:=}</clientId>
    <clientSecret>${nuxeo.hyland.cic.discovery.clientSecret:=}</clientSecret>
    <environment>${nuxeo.hyland.cic.discovery.environment:=}</environment>
  </knowledgeDiscovery>
</extension>
```

You could, for example, add more, like:

```xml
<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandKDService"
  point="knowledgeDiscovery">
  <knowledgeDiscovery>
    <name>myOtherApp</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>https://other.app.for.hyland.discovery.com</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.discovery.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>scope1 scope2 otherNewScope</tokenScope>
    <clientId>456123-abcdef-etc. . .</clientId>
    <clientSecret>765839-rtuklj-etc. . .</clientSecret>
    <environment>someenv-abcdef-123456-etc. . .</environment>
  </knowledgeDiscovery>
</extension>
```
Now, when calling one of the misc.operations, you can pass "myOtherApp" in the `configName` parameter.

> [!TIP]
> When you pass no value (`null` or `""`), the code uses the `"default"` configuration.


#### Error-Check
At startup, if some key parameters are missing (client ID, base URL, …), the plugin logs a WARN. For example, if you do not provide a Data Curation clientId:

```
WARN  [main] [org.nuxeo.labs.hyland.knowledge.enrichment.service.HylandKEServiceImpl] No CIC Data Curation ClientId provided (nuxeo.hyland.cic.datacuration.clientId), calls to the service will fail.
```

## Authentication to the Service

This part is always handled by the plugin, using the different info provided in the configuration parameters (auth. end point + grantType + scope + clientId + clientSecret + environement).

The service returns a token valid a certain time: The plugin handles this timeout (so as to avoid requesting a token at each call, saving some loads)


## Operations

> [!TIP]
> Check in CIC Discovery documentation the type of files accepted by the service (pdf, ...), and convert if needed
> See [JS Automation Examples](/README-Discovery-JS-Automation-Examples.md))

* `HylandKnowledgeDiscovery.getAllAgents`
* `HylandKnowledgeDiscovery.askQuestionAndGetAnswer`
* `HylandKnowledgeDiscovery.Invoke`


### `HylandKnowledgeDiscovery.getAllAgents`

A high level operation that gets a list of all agents

* Input: `void`
* Output: `Blob`, a JSON blob
* Parameters
  * `extraHeadersJsonStr`: String optional. A JSON object as string, with more headers than the one sent byt the plugin, allowing for extra tuning if needed
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The operation calls the service and returns a JSON Blob, that contains the object described in `Common Usage (Both Enrichment and Discovery)` in the main [README](/README.md)

> [!NOTE]
> Reminder: To get the JSON string from this blob, you must call its `getString()` method (see example below). Then you can `JSON.parse` this string

#### Example:
```javascript
// input type: void
// output type: blob
function run(input, params) {
  var result = HylandKnowledgeDiscovery.getAllAgents(null, {});
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);
  if(json.responseCode === 200) {
    // Example: loop on all agents. In this call, "response" is an array of objects
    json.response.forEach(function(oneAgent) {
      // . . . do something with oneAgent . . .
    });
  }

  Console.log("\n\n" + result.getString());
}
```

The returned JSON is something like:

```
{
  "responseCode": 200,
  "responseMessage": "OK",
  "response": [{
      "modelName": "llama3211b",
      "name": "Nuxeo HR Policies",
		  "instructions": null,
      "description": ". . ."
      //. . . more properties . . .
    },
    //. . . more agents . . .
  ]
}
```

### `HylandKnowledgeDiscovery.askQuestionAndGetAnswer`

A high level operation that sends a question, waits for the answer, then returns it.

* Input: `void`
* Output: `Blob`, a JSON blob
* Parameters
  * `question`: String required. The question to ask the agent
  * `agentId`: String, optional. The ID of the agent to ask the question. If not passed, the plugin uses the value of `nuxeo.hyland.cic.discovery.default.agentId`
  * `contextObjectIdsJsonArrayStr`: String, optional.  A stringified JSON Array of Document UUIDs which were sent to the service previously, and will be used for the context of the question.
  * `extraPayloadJsonStr`: String, optional. A JSON object as string, with extra parameters for the service. Check the Knowledge Discovery docmentation. This parameter is also useful in case the service adds more tuning in the misc. calls => no need to wait for a plugin update, just change your payload.
  * `extraHeadersJsonStr`: String optional. A JSON object as string, with more headers than the one sent byt the plugin, allowing for extra tuning if needed
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The plugin sends the question (and the optional `contextObjectIds`) to the service and wait for the answer. This means, with current implementation of the Discovery Service, the plugin _pulls_ the answer. It does it max `nuxeo.hyland.cic.discovery.pullResultsMaxTries` time, and sleeps `nuxeo.hyland.cic.discovery.pullResultsSleepInterval` milliseconds between 2 tries. No error is thrown in case of timeout, the plugin just return the latest response from the service.


#### Example:

```javascript
// In this example, the input is documents, we receive a list of documents, that were already
// sent to CIC and we want to use them as context for the question.
// input type: documents
// output type: blob
function run(input, params) {

  var objKeys, result;

  // Create the array of object Ids
  objKeys = [];
  if(input && input.length) {
    for(i = 0; i < input.length; i++) {
      objKeys.push(input.get(i).id);
    }
  }

  // No custom agent ID (using nuxeo.hyland.cic.discovery.default.agentId), no extraJsonPayload no extraheaders
  result = HylandKnowledgeDiscovery.askQuestionAndGetAnswer(
    null, {
      "question": "How many contracts are in EMEA for the ACME company?",
      "contextObjectIdsJsonArrayStr": objKeys
    }
  );
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);

  Console.log(JSON.stringify(json, null, 2));

  return result;

}
```

The returned JSON is something like:

```
{
  "responseCode": 200,
  "responseMessage": "OK",
  "response": {
    "responseCompleteness": "Complete",
    "question": "How many contracts are in EMEA for the ACME company?",
    "answer": "\n\nThere is only one document about ACME in EMEA, which is document [3].",
     "objectReferences": [ an array of references, document [3] is in there ],
    //. . . more properties . . .
  },
}
```

<br>

### `HylandKnowledgeDiscovery.Invoke`

A hilowgh level operation that calls CIC Knowledge Discovery Service API

* Input: `void`
* Output: `Blob`, a JSON blob
* Parameters
  * `httpMethod`: String required. the method to use ("GET", "POST", etc.)
  * `endpoint`: String, required. The endpoint to call. This will be added to `nuxeo.hyland.cic.discovery.baseUrl`. "/agent/agents" for example
  * `jsonPayloadStr`: String, optional. A JSON object as string, containing the JSON payload as expected by the endpoint
  * `extraHeadersJsonStr`: String optional. A JSON object as string, with more headers than the one sent byt the plugin, allowing for extra tuning if needed
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The operation calls the endpoint service and returns the raw result. You have to handle the HTTP return code (202 - not 200 - when sending a quesiton for example), check all is OK, process the result, maybe do another call, etc.

This operation allows for maximum flexibility, to adapt to the service in case new API is added, the API for an endpoint changes, its payload or headers change, etc.: No need to wait for the plugin to be updated.

See the example of asking a question and getting an answer using `HylandKnowledgeDiscovery.Invoke` in the [JS Automation Examples](/README-Discovery-JS-Automation-Examples.md) file.

<br>


## Support
**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning
resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be
useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

<br>

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

<br>

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
