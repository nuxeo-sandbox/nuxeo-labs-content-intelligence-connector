# nuxeo-labs-content-intelligence-connector

The plugin connects [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) to [Hyland Content Intelligence](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) Cloud APIs to use:
* **Knowledge Enrichment**/**data curation** (get description of an image, summarization of text, classification, ...)
* **Knowledge Discovery**: Ask a question to a LLM, based on documents and metadata you previously sent to your repository. Sending documents to CIC requires to use nuxeo-hxai-connector.

> [!NOTE]
> A connector to [Knowledge Enrichment](https://github.com/nuxeo-sandbox/nuxeo-labs-knowledge-enrichment-connector) only was written previously, and should not be used anymore (already obsolete :-))
> All its code has been moved here, and the operations will keep the same IDs so you will not have to change your configuration once you switch to `nuxeo-labs-content-intelligence-connector`. Just make sure you don't deploy both plugins.

The documentation is split in two parts:

* One for [Knowledge Enrichment](/README-Enrichment.md),
* And one for [Knowledge Discovery](/README-Discovery.md).

<br>

## Common Usage (Both Enrichment and Discovery)

1. Have a valid application on Content Intelligence Cloud/Content Innovation Cloud. Also look at its documentation. You need valid endpoints (authentication, content intelligence, data curation, knowledge discovery), and valid clientId and clientSecret for each service.
2. Setup the configuration parameters required by the plugin

> [!TIP]
> Each service is configurable and can connect using several clientIDs, clientSecrets, baseUrls, etc.

3. From Nuxeo Studio, create an Automation Script that calls the operation(s), then handle the JSON result. From this result, you will typically save values in fields.

The returned JSON is always formated as follow. It encapsulates a couple information about the call to the service (HTTP Result code) and the response as returned by the service. This response is returned "as is", no modification is applied by the plugin, and it is stored in the `"response"` property of the result.

The `"response"` changes depending on the API call, of course, so please check the documentation (or do some testing and output the whole JSON to look at it). For example, the object is not the same for Enrichment than for for Data Curation or Knowledge Discovery, and it is normal, but in all cases the response always provides at least the three following fields:

* `responseCode`: Integer, the HTTP code returned by the service (should be 200 for moist usages. Sometime 202 Accepted is returned)
* `responseMessage`: String, the HTTP message returned by the service. Should be "OK",
* `response`: String. The JSON String as returned by the service, with no alteration.

For example, after call to the Enrichment API, the return JSON will be like:

```javascript
{
    "responseCode": 200, // The HTTP status code
    "responseMessage": "OK", // The HTTP status message
    "objectKeysMapping": null, // null or [] when service called for a single file (see EnrichSeveral operation as an example)
    "response":// A JSON object with the following fields (see Knowledge Enrichment API doumentation)
    {
        "id": String, // The ID of the response
        "timestamp": String, // The date of the response
        "status": String, // "SUCCESS", "FAILURE" or "PARTIAL_FAILURE"
        "results": // An array of responses, one for each file processed
        [
            {
                "objectKey": String, // The object key (as returned by the getPresignedUrl endpoint),
                "imageDescription": {
                    "isSuccess": boolean,
                    "result": String // The description
                },
                "imageEmbeddings": {
                    "isSuccess": boolean,
                    "result": array of doubles
                },
                . . . // Other responses, null if they were not requested. For example:
                "metadata": null,
                "textSummary": null,
                . . .

            }
        ]
    }
}
```

For details about each result, please see the corresponding API and schemas. You can also look at some unit tests in the plugin and to the examples we give.


> [!IMPORTANT]
> **You should always check the `responseCode` is a success (200 <= resultCode < 300)** before trying to get other fields.

See examples of Automation Script.

<br>

## Installation/Deployment
The plug is available in the [Public Nuxeo MarketPlace](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-labs-content-intelligence-connector) and can be added as a dependency to a Nuxeo Studio project, or installed with Docker (added to `NUXEO_PACKAGES`), or installed via:

```
nuxeoctl mp-install nuxeo-labs-content-intelligence-connector
```

<br>

## How to build
```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-content-intelligence-connector
cd nuxeo-labs-content-intelligence-connector
# Build with no unit test
mvn clean install -DskipTests
```

<br>

### How to UnitTest

Please, see documentation of the [`ConfigCheckerFeature`](/nuxeo-labs-content-intelligence-connector-core/src/test/java/org/nuxeo/labs/hyland/content/intelligence/test/ConfigCheckerFeature.java) class. Basically, you can setup environment variables to be used as configuration parameters: `CIC_AUTH_BASE_URL`, `CIC_ENRICHMENT_BASE_URL`, `CIC_ENRICHMENT_CLIENT_ID`, etc.

Then run the unit tests (or the full build).

Tips on Mac OS for Eclipse:
1. Add these env. variables to your .bash_profile (or whatever starter script you have)
2. Open a new terminal and start eclipse from there. Just do a `/path/to/Eclipse.app/Contents/MacOS/eclipse`.

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
[Nuxeo Platform](https://www.hyland.com/solutions/products/nuxeo-platform) is an open source Content Services platform, written in Java. Data can be stored in both SQL & NoSQL
databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions
for [document management](https://www.nuxeo.com/solutions/document-management/), [case management](https://www.nuxeo.com/solutions/case-management/),
and [digital asset management](https://www.nuxeo.com/solutions/dam-digital-asset-management/), use cases. It uses
schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.nuxeo.com).
