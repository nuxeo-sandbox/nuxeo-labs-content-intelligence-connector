# nuxeo-labs-content-intelligence-connector: Agents

This part of the plugin connects a [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) application to [**Hyland Content Intelligence**](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) and leverages its [**Agents**](https://hyland.github.io/ContentIntelligence-Docs/AgentBuilderPlatform) APIs.

It provides two kinds of operations handling the calls to the service (see details for each operation below):

* For ease of use, High-level operations (`HylandKnowledgeDiscovery.getAllAgents` and `HylandKnowledgeDiscovery.askQuestionAndGetAnswer`) that perform all the different individual calls required to get the answer from the service. No need to know the exact endpoints and JSON payload, etc.
* A low-level operation, `HylandKnowledgeDiscovery.Invoke`, that calls the service and returns the JSON response without adding any logic. This is for flexibility: When/if Hyland Content Intelligence adds new endpoints, and/or adds/changes endpoint expected payload, no need to wait for a new version of the plugin, just modify the caller (in most of our usages, Nuxeo Studio project and JavaScript Automation).

> [!NOTE]
>In all cases, the plugin handles authentication, you never need to handle it (see below).

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

## Nuxeo Configuration Parameters and Service Contributions

The service for calling the CIC Discovery service is configurable: You can contribute several XML extensions, each with a different `name`, then use this `name` when calling one of the operations. The plugin will then authenticate and connect to a `baseUrl` using the specified configuration.

The plugin provides a `"default"` configuration, that uses the following configuration parameters that you should set in nuxeo.conf if you want to call only one possible application in Hyland Content Intelligence Cloud.

#### Configuration Parameters

* `nuxeo.hyland.cic.auth.baseUrl`: The authentication endpoint. The plugin adds the "/connect/token" final path. So your URL is something like `https://auth.etc.etc.hyland.com/idp` (This is the same parameter as for Knowledge Enrichment)
* `nuxeo.hyland.cic.agents.baseUrl`: The Discovery base URL.
* `nuxeo.hyland.cic.agents.auth.grantType`: The grant type (default `"client_credentials"` in default configuration)
* `nuxeo.hyland.cic.agents.auth.scope`: The scope when authenticating.
  * This value changes, sometime abruptly, so check CIC announcment and check this first when your calls start to fail with authentication error.
  * As default value may often change we don't put it in README, see latest default values at `service-agents-contrib.xml`
* `nuxeo.hyland.cic.agents.clientId`: The clientId
* `nuxeo.hyland.cic.agents.clientSecret`: The client secret

#### XML Contribution

You can contribute the `"agent"` point of the `"org.nuxeo.labs.hyland.content.intelligence.HylandAgentsService"` service.

> [!TIP]
> If you plan to use only one CIC app, you don't need to contribute XML, you just set the nuxeo.conf parameters values

Here is the `"default"` contribution.

```xml
<!-- Default contributions use configuration parameters -->
<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandAgentsService"
  point="agent">
  <agent>
    <name>default</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>${nuxeo.hyland.cic.agents.baseUrl:=}</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.agents.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>${nuxeo.hyland.cic.agents.auth.scope:=hxp hxp.integrations environment_authorization}</tokenScope>
    <clientId>${nuxeo.hyland.cic.agents.clientId:=}</clientId>
    <clientSecret>${nuxeo.hyland.cic.agents.clientSecret:=}</clientSecret>
  </agent>
</extension>
```

You could, for example, add more, like:

```xml
<extension
  target="org.nuxeo.labs.hyland.content.intelligence.HylandAgentsService"
  point="agent">
  <agent>
    <name>myOtherApp</name>
    <authenticationBaseUrl>${nuxeo.hyland.cic.auth.baseUrl:=}</authenticationBaseUrl>
    <baseUrl>https://other.app.for.agents.com</baseUrl>
    <tokenGrantType>${nuxeo.hyland.cic.agents.auth.grantType:=client_credentials}</tokenGrantType>
    <tokenScope>scope1 scope2 otherNewScope</tokenScope>
    <clientId>456123-abcdef-etc. . .</clientId>
    <clientSecret>765839-rtuklj-etc. . .</clientSecret>
  </agent>
</extension>
```
Now, when calling one of the misc.operations, you can pass "myOtherApp" in the `configName` parameter.

> [!TIP]
> When you pass no value (`null` or `""`), the code uses the `"default"` configuration.


#### Error-Check
At startup, if some key parameters are missing (client ID, base URL, …), the plugin logs a WARN. For example, if you do not provide an Agents clientId:

```
No configuration found for Agents Service. Calls, if any, will fail
```

## Authentication to the Service

This part is always handled by the plugin, using the different info provided in the configuration parameters (auth. end point + grantType + scope + clientId + clientSecret + environement).

The service returns a token valid a certain time: The plugin handles this timeout (so as to avoid requesting a token at each call, saving some loads)


## Operations

> [!IMPORTANT]
> Check the CIC Agent Builder documentation what is the expected JSON input of the agent, and what it returns in the result.

* `HylandAgents.getAllAgents`
* `HylandAgents.LookupAgent`
* `HylandAgents.InvokeTask`


### `HylandAgents.getAllAgents`

A high level operation that gets a list of all agents linked linked to the account.

* Input: `void`
* Output: `Blob`, a JSON blob
* Parameters
  * `extraHeadersJsonStr`: String optional. A JSON object as string, with more headers than the one sent byt the plugin, allowing for extra tuning if needed
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The operation calls the service and returns a JSON Blob, that contains the object described in `Common/Shared Usage` in the main [README](/README.md). The original unmodified response from the ser vice in the `response` property.

> [!NOTE]
> Reminder: To get the JSON string from this blob, you must call its `getString()` method (see example below). Then you can `JSON.parse` this string and find the full response form the service in the `response` property.



As of JAN 2026 the returned JSON is something like:

```
{
  "responseCode": 200,
  "responseMessage": "OK",
  "response": {
    "agents": [
      {
        "id": "(here the UUID)",
        "type": "task",
        "name": "Agent for Nuxeo Unit Tests",
        . . .
      },
      {
        "id": "(here the UUID)",
        "type": "task",
        "name": "Polite Translator",
        . . .
      },
      . . .
    ],
    "pagination": {
      "totalItems": 10,
      "offset": 0,
      "limit": 50,
      "hasMore": false
    }
  }
}
```

#### Example:
```javascript
// input type: void
// output type: blob
function run(input, params) {
  var result = HylandAgents.getAllAgents(null, {});
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);
  if(json.responseCode === 200) {
    // Example: loop on all agents. In this call, "response" is an array of objects
    json.response.agents.forEach(function(oneAgent) {
      // . . . do something with oneAgent.id, oneAgent.name . . .
    });
  }
}
```

### `HylandAgents.LookupAgent`

Returns all the info available for an agent.

* Input: `void`
* Output: `Blob`, a JSON blob
* Parameters
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.
  * `agentId`: String, required. The ID of the agent to invoke.
  * `agentVersion`, String, optional. If not used, latest version is looked up.
  * `extraHeadersJsonStr`: String optional. A JSON object as string, with more headers than the one sent byt the plugin, allowing for extra tuning if needed

See CIC documentation for the detailed results. The JSON returned (in the `reponse` object of the returned blob) will change depending on the agent. It contains information like the ID, the description, the llm model used etc. It also contains informaiton about the expected input(s) and the output(s).

We recommand calling this operation and loging the full result to check all the available fields:

```javascript
// input type: void
// output type: blob
function run(input, params) {
  var result = HylandAgents.LookupAgent(null, {
    "agentId": "12345678-here-the-agent-id"
  });
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);
  if(json.responseCode === 200) {
    Console.log(JSON.stringify(json, null, 2));
  }
}
```



### `HylandAgents.InvokeTask`

A high level operation that runs an agent and returns the result.

* Input: `void`
* Output: `Blob`, a JSON blob
* Parameters
  * `agentId`: String, required. The ID of the agent to invoke.
  * `jsonPayloadStr`: String, required. A JSON object as string, holding the input for the agent.
  * `extraHeadersJsonStr`: String optional. A JSON object as string, with more headers than the one sent byt the plugin, allowing for extra tuning if needed
  * `configName`: String, optional. The name of the XML contribution to use for baseUrl, clientId, etc. If not passed, the plugin uses `"default"`.

The plugin invokes the `agentId`, passing it its expected parameters in `jsonPayloadStr`. It returns aJSON Blob, that contains the object described in `Common/Shared Usage` in the main [README](/README.md). The original unmodified response from the ser vice in the `response` property.

> [!NOTE]
> Reminder: To get the JSON string from this blob, you must call its `getString()` method (see example below). Then you can `JSON.parse` this string and find the full response form the service in the `response` property.

> [!TIP]
> See CIC Agent Builder documentation about the expected format of this JSON input.


#### Example:

In this example, we invoke an agent that expects a "targetLanguage" and a "sourceText" parameters. It returns the translated text in a "translated" property. The result from the service is (check the doc) something like:

```json
{
  "object": "response",
  . . . other properties . . .
  "output": [
    {
      "type": "message",
      "status": "completed",
      "content": [
        "type": "output_text",
        "text": "{\"translated\": \"the translated value \"}"
      ]
    }
  ]
}
```

Here we go:

```javascript
function run(input, params) {
  // As expected by the agent
  var input = {
    "inputs": {
      "targetLanguage": "french",
      "sourceText": "Everything's fine, everyting's gonna be ok."
    }
  };

  // Simple call, default config, no extra header
  var result = HylandAgents.InvokeTask(null, {
    "agentId": "12345678-abcd-ef12-etc",
    "jsonPayloadStr": JSON.stringify(input)
  });
  var jsonStr = result.getString();
  var json = JSON.parse(jsonStr);
  if(json.responseCode === 200) {
    var translatedJsonStr = json.response.output[0].text;
    var translatedJson = JSON.parse(translatedJsonStr);

    Console.log("Translated text:\n" + translatedJson.translated);
  }

}
```

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
