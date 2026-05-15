# nuxeo-labs-content-intelligence-connector

The plugin connects [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) to [Hyland Content Intelligence](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) (CIC) Cloud APIs to use:

* **Knowledge Enrichment** / **Data Curation** (image description, text summarization, classification, named-entity extraction, embeddings, ...)
* **Knowledge Discovery**: ask a question to a LLM grounded on documents and metadata previously sent to your CIC repository (typically via the `nuxeo-hxai-connector` plugin).
* **Agents** (Knowledge Discovery agents, Agentic agents, RAG agents, ...) APIs.

> [!NOTE]
> Starting at version 2025.16, this plugin brings:
> * Buttons to call CIC
> * Schemas, facets, etc. needed to store the results
> * Layouts to display these results
> 
> All operation IDs were preserved from previous versions, so an existing Studio project that calls `HylandKnowledgeEnrichment.*`, `HylandKnowledgeDiscovery.*`, etc. keeps working as-is. If you already have buttons and UI, just disable the ones deployed by the plugin (see below, "Show / hide buttons per project").


Also:

> [!WARNING]
> This documentation references calls to the CIC Ingest API and the Content Lake API: please note that these are currently Work In Progress and are not fully implemented. We are in a Nuxeo presales Sandbox plugin, so this is acceptable, but we discourage prospects/customers from using it "as is".

<br>

## Two levels of features

The plugin offers **two complementary usage levels**. They can be mixed in the same project.

### 1. Low-level — "you call CIC, you handle the result"

This is the historical surface (any version `<= 2025.15`, plus `2025.18`). The plugin exposes one Automation operation per CIC endpoint. Your Studio project calls the operation, inspects the JSON envelope, decides what to write where, handles errors, builds your own UI, etc.

Use this level when you want full control: bespoke document model, custom triggers (event handler, listener, page provider action), custom result rendering, batch processing of selected documents, etc.

The only behavioral change carried forward in the current versions is that **Knowledge Enrichment is v2-only**: the deprecated v1 API is no longer reachable from the plugin (the legacy `useKEV2=false` parameter is ignored and logs a WARN).

For the full list of operations, parameters, JSON envelopes and Automation Script examples, **see [the historical README](/README.md)** and its companion files (`README-Enrichment.md`, `README-Enrichment-JS-Automation-Examples.md`, `README-Discovery.md`, `README-Discovery-JS-Automation-Examples.md`, `README-Agents.md`).

### 2. High-level — "ready-to-use buttons in the Web UI"

Since `2025.18` the plugin also ships:
* **Schemas, facets and document types** to persist CIC results.
* **Web UI buttons** wired to the operations: one click on a document fires the right CIC call, stores the result on the document and shows a success/error notification.
* A **"Ask a question" dialog** that uses Knowledge Discovery on a single document or on a multi-selection, with citation back to the matched Nuxeo documents.
* Two **document types** (`CICAgentAndConfig` and `CICAgenticAgentAndConfig`) to declaratively register CIC agents inside the Nuxeo repository, so end users can pick them from a suggester.

The rest of this README documents this UI level and how to customize it.

<br>

## Configuration

### Endpoints, client IDs, secrets

CIC is reached via OAuth2 client credentials. The plugin needs:
* An **authentication base URL** (usually `nuxeo.hyland.cic.auth.baseUrl`)
* per-service **base URL**, **clientId**, **clientSecret** and (for some services) **environment**

Set these via standard `nuxeo.conf` properties. The default mapping is:

| Service | Base URL | Client ID | Client Secret | Environment |
| --- | --- | --- | --- | --- |
| Knowledge Enrichment | `nuxeo.hyland.cic.contextEnrichment.baseUrl` | `nuxeo.hyland.cic.enrichment.clientId` | `nuxeo.hyland.cic.enrichment.clientSecret` | — |
| Data Curation | `nuxeo.hyland.cic.dataCuration.baseUrl` | `nuxeo.hyland.cic.datacuration.clientId` | `nuxeo.hyland.cic.datacuration.clientSecret` | — |
| Knowledge Discovery | `nuxeo.hyland.cic.discovery.baseUrl` | `nuxeo.hyland.cic.discovery.clientId` | `nuxeo.hyland.cic.discovery.clientSecret` | `nuxeo.hyland.cic.discovery.environment` |
| Agents | `nuxeo.hyland.cic.agents.baseUrl` | `nuxeo.hyland.cic.agents.clientId` | `nuxeo.hyland.cic.agents.clientSecret` | — |
| Ingest | `nuxeo.hyland.cic.ingest.baseUrl` | `nuxeo.hyland.cic.ingest.clientId` | `nuxeo.hyland.cic.ingest.clientSecret` | `nuxeo.hyland.cic.ingest.environment` |
| Content Lake | `nuxeo.hyland.cic.contentlake.baseUrl` | `nuxeo.hyland.cic.contentlake.clientId` | `nuxeo.hyland.cic.contentlake.clientSecret` | `nuxeo.hyland.cic.contentlake.environment` |

Optional per-service `auth.grantType` and `auth.scope` overrides also exist (see the `service-*-contrib.xml` files); the defaults match Hyland's documented values and rarely need to change.

### Multiple accounts: named contributions (`configName`)

Each service exposes an extension point (`knowledgeEnrichment`, `dataCuration`, `knowledgeDiscovery`, `agent`, `ingest`, `contentLake`). The plugin ships a `default` contribution wired to the `nuxeo.conf` parameters above. Every operation accepts a `configName` parameter (defaults to `"default"`); pass another name to target a different CIC account.

To declare an additional account, add an XML contribution from your Studio project (or another Nuxeo bundle):

```xml
<extension target="org.nuxeo.labs.hyland.content.intelligence.HylandKEService"
           point="knowledgeEnrichment">
  <knowledgeEnrichment>
    <name>tenantA</name>
    <authenticationBaseUrl>...</authenticationBaseUrl>
    <baseUrl>...</baseUrl>
    <clientId>...</clientId>
    <clientSecret>...</clientSecret>
  </knowledgeEnrichment>
</extension>
```

Then call any operation with `configName: "tenantA"`. The list of registered names per service is exposed by `HylandContentIntelligence.GetContributionNames`.

### Optional: where to persist embeddings (KE only)

The plugin **does not** ship an embeddings facet/schema, so the embeddings operations (`CIC.GetTextEmbeddings`, `CIC.GetImageEmbeddings`) only persist the vector when you tell them where. Uncomment and set on the KE descriptor:

```xml
<embeddingsFacet>Embeddings</embeddingsFacet>
<embeddingsImageXpath>embeddings:image</embeddingsImageXpath>
<embeddingsTextXpath>embeddings:text</embeddingsTextXpath>
```

If `embeddingsFacet` is missing, **or** the matching `embeddingsImageXpath` / `embeddingsTextXpath` is missing, the corresponding `CIC.GetImageEmbeddings` / `CIC.GetTextEmbeddings` operation **skips the remote CIC call entirely**, logs a WARN, and returns the document unchanged. This keeps cross-plugin compatibility (e.g. `nuxeo-hxai-connector` keeps owning the storage) and avoids paying for a call whose result has nowhere to land.

> [!TIP]
> Storing embeddings is typically used together with the [Nuxeo Custom Page Providers](https://github.com/nuxeo-sandbox/nuxeo-custom-page-providers) plugin, which enables Vector Search with OpenSearch (so, based on embeddings, you can set up similarity search or semantic search, for example).
> Nuxeo Custom Page Providers ships several Configuration Templates that use these names.

<br>

## Document model added by the plugin

### Facets / schemas (storage of CIC results)

The following **facets** are declared and added **dynamically at runtime** by the operations on the target document the first time a successful call is made — you do **not** need to attach them up-front to `File`, `Picture` or any custom doctype:

| Facet | Schema | Filled by |
| --- | --- | --- |
| `CICSummary` | `cic_summary` | `CIC.SummarizeText` |
| `CICClassification` | `cic_classification` | `CIC.ClassifyImage`, `CIC.ClassifyTextFile` |
| `CICNamedEntities` | `cic_named_entities` | `CIC.GetNamedEntitiesFromImage`, `CIC.GetNamedEntitiesFromText` |
| `CICMetadataDetection` | `cic_metadata_detection` | `CIC.GetImageMetadata` |
| `CICTextMetadata` | `cic_text_metadata` | `CIC.GetTextMetadata` |
| `CICImageDescription` | `cic_image_description` | `CIC.GetImageDescription` |
| `CICError` | `cic_error` | All `CIC.*` enrichment ops, on failure |

`CICError` carries `service`, `responseCode`, `responseMessage`, `message` and `fullResponseJson` (the full upstream `ServiceCallResult` envelope, JSON-serialized). It is automatically cleared on the next successful call.

### Document types (CIC agent registry)

Used by the KD "Ask a question" dialog (the agent picker is a `nuxeo-document-suggestion` backed by the `SelectCICAgentAndConfig` page provider):

| Doctype | Extends | Purpose |
| --- | --- | --- |
| `CICAgentAndConfig` | `Document` | Stores a KD/Discovery agent definition (id, configName, ...). |
| `CICAgenticAgentAndConfig` | `Document` | Stores an Agentic agent definition (id, input schema, tools — populated by `CIC.AgenticAgentLookup`). |
| `CICAgentsAndConfigs` | `Folder` | Container restricted to `CICAgentAndConfig` children. |
| `CICAgenticAgentsAndConfigs` | `Folder` | Container restricted to `CICAgenticAgentAndConfig` children. |

### Vocabularies

Loaded on first start from CSV files shipped with the plugin. Used by the classification ops as the default class list when none is passed explicitly:

* `cicImageClassification` — default labels for `CIC.ClassifyImage`
* `cicTextClassification` — default labels for `CIC.ClassifyTextFile`
* `cicSecurityLevel` — for security-level metadata extraction

Edit the CSVs in your Studio project (or contribute new entries to the same directory names) to change the available classes.

<br>

## Web UI buttons

All buttons are contributed in `nuxeo-labs-content-intelligence-connector-bundle.html` as `<nuxeo-slot-content>` entries. Names are prefixed `cic-` and i18n keys are namespaced under `label.ui.cic.*` (English + `fr` / `fr-FR` shipped).

### Knowledge Enrichment — image actions

Visible on documents with the `Picture` facet, with a `file:content` blob, that are not a version or a proxy, and on which the user has `ReadWrite`. Each button calls one operation, persists the result on the document and shows a notification.

| Slot-content name | Icon (Polymer `iron-icons`) | Operation | Stored on |
| --- | --- | --- | --- |
| `cic-ke-image-description` | `icons:description` | `CIC.GetImageDescription` | `CICImageDescription` facet |
| `cic-ke-image-classify` | `icons:class` | `CIC.ClassifyImage` | `CICClassification` facet |
| `cic-ke-image-metadata` | `icons:note-add` | `CIC.GetImageMetadata` | `CICMetadataDetection` facet |
| `cic-ke-image-named-entities` | `icons:view-list` | `CIC.GetNamedEntitiesFromImage` | `CICNamedEntities` facet |
| `cic-ke-image-embeddings` | `icons:fingerprint` | `CIC.GetImageEmbeddings` | configured embeddings xpath (see above) |

### Knowledge Enrichment — text/file actions

Visible on documents of type `File`, with a `file:content` blob, not version/proxy, `ReadWrite`:

| Slot-content name | Icon | Operation | Stored on |
| --- | --- | --- | --- |
| `cic-ke-textfile-summarize` | `icons:account-balance-wallet` | `CIC.SummarizeText` | `CICSummary` facet |
| `cic-ke-textfile-classify` | `icons:class` | `CIC.ClassifyTextFile` | `CICClassification` facet |
| `cic-ke-text-metadata` | `icons:note-add` | `CIC.GetTextMetadata` | `CICTextMetadata` facet |
| `cic-ke-textfile-named-entities` | `icons:view-list` | `CIC.GetNamedEntitiesFromText` | `CICNamedEntities` facet |
| `cic-ke-textfile-embeddings` | `icons:fingerprint` | `CIC.GetTextEmbeddings` | configured embeddings xpath (see above) |

### Knowledge Discovery — Ask a question

| Slot-content name | Slot | Icon | Element |
| --- | --- | --- | --- |
| `cic-kd-ask-question` | `DOCUMENT_ACTIONS` | `icons:question-answer` | `<kd-ask-question>` |
| `cic-kd-ask-question-with-selected` | `RESULTS_SELECTION_ACTIONS` | `icons:question-answer` | `<kd-ask-question-with-selected>` |

The dialog lets the user:
* pick a `CICAgentAndConfig` document (via the `SelectCICAgentAndConfig` page provider);
* type a question;
* see the answer + the **Nuxeo documents** matching the agent's `objectReferences` (resolved via `CIC.KDAskQuestionForUI`, which extracts the trailing UUID and runs an NXQL `IN (...)` lookup).

### Knowledge Discovery — Conversation

| Slot-content name | Slot | Icon | Element |
| --- | --- | --- | --- |
| `cic-kd-conversation` | `DOCUMENT_ACTIONS` | `icons:forum` | `<kd-conversation>` |

The dialog lets the user have a multi-turn conversation with a Knowledge Discovery agent:
* pick an agent from the picker (populated by `HylandKD.AvailableAgents`);
* send a first question (calls `HylandKnowledgeDiscovery.startConversation`);
* keep typing follow-up questions in the same conversation (calls `HylandKnowledgeDiscovery.continueConversation`);
* optionally toggle "Show References" to see the Nuxeo documents the agent cited.

> **Agent picker = local CICAgentAndConfig documents.** `HylandKD.AvailableAgents` runs an NXQL query on `CICAgentAndConfig` using the **current user's** session, so each user only sees agents on which they have READ permission. This lets a Nuxeo administrator control who can use which agent simply by granting/revoking READ on the corresponding `CICAgentAndConfig` documents — no need to declare every CIC platform agent in Nuxeo. (The full CIC platform agent list is available via `HylandKnowledgeDiscovery.getAllAgents`, but is intentionally NOT what drives this UI.)

### Agents

| Slot-content name | Slot | Icon | Operation | Visible on |
| --- | --- | --- | --- | --- |
| `cic-agents-lookupAgent` | `DOCUMENT_ACTIONS` | `icons:announcement` | `CIC.AgenticAgentLookup` | `CICAgenticAgentAndConfig` documents (`ReadWrite`) |

> Polymer `iron-icons` are part of the standard Web UI icon set; previews live at https://www.webcomponents.org/element/@polymer/iron-icons/demo/demo/index.html.

<br>

## Reusable form elements (`forms/cic-*-view.html`, `forms/cic-*-edit.html`)

For every CIC schema/facet the plugin persists, a small Polymer element is shipped under `web/nuxeo.war/ui/nuxeo-labs-content-intelligence-connector/forms/`. Drop one into any document layout (your Studio project, this plugin's layouts, or anywhere else) to render — or edit — the CIC field without rewriting the markup yourself.

### Available elements

| File | Tag | Reads / writes | Notes |
| --- | --- | --- | --- |
| `forms/cic-summary-view.html` | `<cic-summary-view>` | `cic_summary:summary` | Renders the text summary if the `CICSummary` facet is present. |
| `forms/cic-image-description-view.html` | `<cic-image-description-view>` | `cic_image_description:description` | Renders the image description if the `CICImageDescription` facet is present. |
| `forms/cic-classification-view.html` | `<cic-classification-view>` | `cic_classification:imageClass` / `:textClass` | Read-only label of the chosen class. |
| `forms/cic-classification-edit.html` | `<cic-classification-edit>` | same | Editable picker bound to the `cicImageClassification` / `cicTextClassification` vocabulary. |
| `forms/cic-named-entities-view.html` | `<cic-named-entities-view>` | `cic_named_entities:entities` | Renders the entities list. |
| `forms/cic-metadata-detection-view.html` | `<cic-metadata-detection-view>` | `cic_metadata_detection:metadata` | Renders the `field/value` items extracted from images. |
| `forms/cic-metadata-detection-edit.html` | `<cic-metadata-detection-edit>` | same | Editable variant. |
| `forms/cic-text-metadata-view.html` | `<cic-text-metadata-view>` | `cic_text_metadata:*` | Renders `company`, `owner`, `security`, `keywords`, `moreMetadata`. |
| `forms/cic-text-metadata-edit.html` | `<cic-text-metadata-edit>` | same | Editable variant. |

All elements take a single `document` property (the standard Nuxeo Web UI `document` object) and silently render nothing when the underlying facet/field is missing — safe to drop unconditionally into a layout.

### How to use them in a Studio layout

Example: display the text summary in the metadata layout of the `File` document type.

1. In **Studio Designer**, go to **Layouts > Built-in Document Types > File** and configure the `nuxeo-file-metadata-layout`.
2. Then go to **Resources > document > file** and open `nuxeo-file-metadata-layout.html`.
3. In this file:
   * import the CIC plugin form you need;
   * use it where you want it to appear.

```html
<!-- nuxeo-file-metadata-layout -->
<!-- Display the CIC summary if available -->
<link rel="import" href="../../nuxeo-labs-content-intelligence-connector/forms/cic-summary-view.html">

<dom-module id="nuxeo-file-metadata-layout">
  <template>
    ...
    <cic-summary-view document="[[document]]"></cic-summary-view>
    ...
  </template>
</dom-module>
```

The same pattern applies to every form listed above. For example, on a `Picture` document:

```html
<link rel="import" href="../../nuxeo-labs-content-intelligence-connector/forms/cic-image-description-view.html">
<link rel="import" href="../../nuxeo-labs-content-intelligence-connector/forms/cic-classification-view.html">
<link rel="import" href="../../nuxeo-labs-content-intelligence-connector/forms/cic-named-entities-view.html">
...
<cic-image-description-view document="[[document]]"></cic-image-description-view>
<cic-classification-view document="[[document]]"></cic-classification-view>
<cic-named-entities-view document="[[document]]"></cic-named-entities-view>
```

### Tips

* Use `*-view.html` in `view` / `metadata` layouts; use `*-edit.html` in `edit` layouts when you want users to override the value computed by CIC.
* Combine with the per-button override mechanism (see "Show / hide buttons per project") if you want to wire your own automation chain instead of the default `CIC.*` operation while keeping the same UI.
* The plugin's own document layouts (under `document/<doctype>/nuxeo-<doctype>-<mode>-layout.html`) use these same form elements — read them as live examples.

<br>

## Customization patterns

### Override the default behavior from Studio

Every artifact the plugin contributes (operations, page provider, schemas, doctypes, directories, slot-contents, i18n keys) is identified by a stable name. Contributing **the same id** from your Nuxeo Studio project takes precedence and overrides the plugin's version. Common cases:

* **Replace the operation called by a button**: write a new automation chain/script (or a different operation) and override the `cic-ke-*` slot-content with the same `name`/`slot`, just change the `operation="..."` attribute on the inner `<nuxeo-operation-button-with-spinner>`.
* **Change the default classes** used by `CIC.ClassifyImage` / `CIC.ClassifyTextFile`: edit the `cicImageClassification` / `cicTextClassification` directory entries in your Studio project (same directory name, your CSV wins).
* **Change the page provider** that lists agents in the KD dialog: contribute a `SelectCICAgentAndConfig` page provider with the same name and your own NXQL.

### Show / hide buttons per project

If your Nuxeo App only needs Knowledge Enrichment and not Knowledge Discovery (or the opposite), don't fork the plugin — disable the buttons you don't want from your Studio project.

1. Open `nuxeo-labs-content-intelligence-connector-bundle.html` (in this repo) and copy the `<nuxeo-slot-content>` entries you want to tune into your Studio project under your **Designer > Resources > YOUR-PROJECT-ID-custom-bundle.html**.
2. Keep the **same `name` and `slot`**, then add the `disabled` attribute (or change anything else, e.g. `order`, `icon`, `operation`...).

For example, to **hide** the "Ask a question" button on documents:

```html
<nuxeo-slot-content name="cic-kd-ask-question" slot="DOCUMENT_ACTIONS" order="1" disabled>
  <template>
    <kd-ask-question icon="icons:question-answer"></kd-ask-question>
  </template>
</nuxeo-slot-content>
```

To hide all KD entries: do the same for `cic-kd-ask-question-with-selected`. To hide all KE image actions: do the same for the five `cic-ke-image-*` entries. Etc.

The Studio bundle is loaded **after** the plugin bundle, so a same-name `<nuxeo-slot-content>` in `YOUR-PROJECT-ID-custom-bundle.html` wins.

<br>

## Response envelope (still applies in both usage levels)

All `CIC.*` and `Hyland*.*` operations return a `Blob` containing a JSON object with this canonical shape:

```json
{
  "responseCode": 200,
  "responseMessage": "OK",
  "objectKeysMapping": null,
  "response": { "...": "raw upstream payload, never altered by the plugin" }
}
```

Always check `responseCode` is in 200-299 before reading `response`. On failure, the high-level UI buttons populate the `CICError` facet (including `cic_error:fullResponseJson` with the full envelope above) — useful for diagnostics from the document's metadata view.

<br>

## Installation / Deployment

The plugin is published on the [Nuxeo MarketPlace](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-labs-content-intelligence-connector). Add it as a Studio project dependency, set it in a Docker `NUXEO_PACKAGES` setup, or install it manually:

```
nuxeoctl mp-install nuxeo-labs-content-intelligence-connector
```

<br>

## Build

```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-content-intelligence-connector
cd nuxeo-labs-content-intelligence-connector
mvn clean install -DskipTests
```

Tests require live CIC credentials. See [`ConfigCheckerFeature`](/nuxeo-labs-content-intelligence-connector-core/src/test/java/org/nuxeo/labs/hyland/content/intelligence/test/ConfigCheckerFeature.java) for the full env-var list; without those, the corresponding test groups are silently skipped.

<br>

## Support

**These features are not part of the Nuxeo Production platform.** They are provided for inspiration and as code samples. This is a moving project (no API maintenance, no deprecation process). If a solution proves broadly useful, it will be moved into the platform proper, not maintained here.

<br>

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

<br>

## About Nuxeo

Nuxeo Platform is an open source highly scalable, cloud-native, enterprise content management product with rich multimedia support, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

More information is available at [Hyland/Nuxeo](https://www.hyland.com/en/solutions/products/nuxeo-platform).
