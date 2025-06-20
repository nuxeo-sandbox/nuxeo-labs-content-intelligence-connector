# nuxeo-labs-content-intelligence-connector

> [!WARNING]
> This is Work In Progress. Using Github mainly as backup.
> => **Do not use this plugin for now**

The plugin connects to Hyland Content Intelligence Cloud APIs to use:
* Knowledge Enrichment/data curation (get description of an image, summarization of text, classification, ...)
* Knowledge Discovery: Ask a question to a LLM, based on documents and metadata you previously sent to your repository. This requires to use nuxeo-hxai-connector to send the documents there. We will explain in details once we are ready :-)

For now a connector to [Knowledge Enrichment](https://github.com/nuxeo-sandbox/nuxeo-labs-knowledge-enrichment-connector) is ready. All its code has been moved here, but the operations will keep the same IDs so you will not have to change your configuration once you switch to nuxeo-labs-content-intelligence-connector.

<hr>
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
