# nuxeo-labs-content-intelligence-connector

## TL;DR

Version **2025.16** of this pluging **ships a full Web UI on top of the existing CIC automation operations.** Out of the box you get:

- **Document action buttons** for the common Knowledge Enrichment calls (summarize, classify, extract entities, get metadata, image description, embeddings) — results stored in dedicated schemas/doctypes shipped with the plugin.
- **Knowledge Discovery dialogs**: an "Ask a Question" popup (single doc and multi-select) and a `kd-conversation` chat panel with citations linked back to Nuxeo documents.
- **Multi-document `CIC.*` ops** with batching, per-doc error markers, and inter-batch commits — safe for listeners, BAF, and bulk actions.
- **Easy to override** for you nown usage. There even is an **Admin "CIC UI Config" page** that lists every `cic-*` slot-content and generates ready-to-paste Studio override snippets (demo build/tests utility).
- **Connection to CIC** stays easy: Add nuxeo.conf parameters (URLs, secrets, ...)


> [!IMPORTANT]
> * Low-Level operations are available at [docs/low-level/README.md](docs/low-level/README.md) and fully functional.
> * KE v1 is gone (v2-only)

## The Nuxeo Labs Content Intelligence Connector

The plugin connects [Nuxeo](https://www.hyland.com/solutions/products/nuxeo-platform) to [Hyland Content Intelligence](https://www.hyland.com/en/solutions/products/hyland-content-intelligence) (CIC) Cloud APIs to use:

* **Knowledge Enrichment** / **Data Curation** (image description, text summarization, classification, named-entity extraction, embeddings, ...)
* **Knowledge Discovery**: ask a question to a LLM grounded on documents and metadata previously sent to your CIC repository (typically via the `nuxeo-hxai-connector` plugin).
* **Agents** (Knowledge Discovery agents, Agentic agents, RAG agents, ...) APIs.

> [!NOTE]
> Starting at version 2025.16, this plugin brings:
> * Buttons to call CIC for a specific action
> * Schemas, facets, etc. needed to store the results
> * Layouts to display these results
> 
> All operation IDs were preserved from previous versions, so an existing Studio project that calls `HylandKnowledgeEnrichment.*`, `HylandKnowledgeDiscovery.*`, etc. keeps working as-is. If you already have buttons and UI, just disable the ones deployed by the plugin (see below, "Show / hide buttons per project").


Also:

> [!WARNING]
> This documentation references calls to the CIC Ingest API and the Content Lake API: please note that these are currently Work In Progress and are not fully implemented. We are in a Nuxeo presales Sandbox plugin, so this is acceptable, but we discourage prospects/customers from using them "as is".

<br>

## Two levels of features

The plugin offers **two complementary usage levels**. They can be mixed in the same project.

### 1. Low-level — "you call CIC, you handle the result"

This is the historical surface (any version `<= 2025.15`, plus `2023.18`). The plugin exposes a couple Automation operations for low-level calls to CIC. Your Studio project calls the operation, inspects the JSON envelope, decides what to write where, handles errors, builds your own UI, etc.

> [!NOTE]
> "historical surface" does not mean these are deprecated, they are not at all, and all the underlying code calling CIC is used intensively.

Use this level when you want full control: bespoke document model, custom triggers (event handler, listener, page provider action), custom result rendering, batch processing of selected documents, etc.

The only behavioral change carried forward in the current versions is that **Knowledge Enrichment is v2-only**: the deprecated v1 API is no longer reachable from the plugin (the legacy `useKEV2=false` parameter is ignored and logs a WARN).

For the full list of operations, parameters, JSON envelopes and Automation Script examples, **see [the low-level README](docs/low-level/README.md)** and its companion files (`docs/low-level/README-Enrichment.md`, `docs/low-level/README-Enrichment-JS-Automation-Examples.md`, `docs/low-level/README-Discovery.md`, `docs/low-level/README-Discovery-JS-Automation-Examples.md`, `docs/low-level/README-Agents.md`).

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
* An **authentication base URL** (`nuxeo.hyland.cic.auth.baseUrl`)
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

> [!TIP]
> If you need to __add__ fields or control the behavior or the display of these schemas, just create resources with the exact same IDs in your Nuxeo Studio Project.
> For example, if you want the `CICSummary` facet to contain another extra custom schema of yours, then:
> * Declare the corresponding `cic_summary` schema in the Studio registries
> * Create the `CICSummary` facet
> * Add the `cic_summary` schema and your own schema(s) as needed.
>
> **or** use the low-level operations and handle the result in your own schemas.

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

> [!IMPORTANT]
> **Customizing `CIC.GetTextMetadata` / `CIC.GetImageMetadata`.** KE v2 **requires** a `kSimilarMetadata` example object on both metadata-generation actions (it tells the model which fields to extract and which values are plausible — see the [Hyland v2 docs](https://hyland.github.io/ContentIntelligence-Docs/KnowledgeEnrichment/Reference/Context%20API/v2-api-examples#text-metadata-generation)). Accordingly, the `kSimilarMetadataJsonStr` parameter on both operations is **required** (`required = true`); calling them without it fails fast with a `NuxeoException`. No Java-side default is provided.
>
> So that the out-of-the-box Web UI buttons still work, the plugin ships a **deliberately generic example value directly in the two slot-contents** (`cic-ke-text-metadata`, `cic-ke-image-metadata`) inside `nuxeo-labs-content-intelligence-connector-bundle.html`:
>
> - `cic-ke-text-metadata` → `[{"document:category":"Business|Personal|Technical|Legal|Financial|Other","keywords:tags":"general|reference|draft|final|important|other"}]`
> - `cic-ke-image-metadata` → `[{"image:category":"Photo|Screenshot|Diagram|Scan|Illustration|Other","keywords:tags":"general|reference|product|person|landscape|document"}]`
>
> **These defaults are intentionally meaningless** and will produce poor results on real content. They exist only so the buttons do not error out before you customize them. For any real deployment:
>
> 1. **Design your own `kSimilarMetadata` JSON array** that matches your business (format: `"category:field": "Value1|Value2|..."` — multiple example objects encouraged, see the Hyland docs).
> 2. **Wrap the operation in a Studio Automation chain** that injects your value as `kSimilarMetadataJsonStr` and calls `CIC.GetTextMetadata` (or `CIC.GetImageMetadata`).
> 3. **Override the slot-content** in your Studio project (`cic-ke-text-metadata` / `cic-ke-image-metadata`) so the Web UI button calls your chain instead of the raw operation. The Admin → CIC UI Config drawer (`CIC.GetUIBundleConfig`) generates a copy / edit / paste-ready snippet of the current slot-content layout.
>
> Calling these operations from Automation / REST without `kSimilarMetadataJsonStr` will fail-fast with an explicit error pointing at this section.

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

> [!TIP]
> You will most likely override these vocabularies to use your own values.
> The easiest way for doing that is to just create, in you Nuxeo Studio project, vocabularies with the same id.
> Do not forget to handle translation keys, if needed.

<br>

## Web UI buttons

All buttons are contributed in `nuxeo-labs-content-intelligence-connector-bundle.html` as `<nuxeo-slot-content>` entries. Names are prefixed `cic-` and i18n keys are namespaced under `label.ui.cic.*` (English + `fr` / `fr-FR` shipped).

### Knowledge Enrichment — image actions

Visible on documents with the `Picture` facet, with a `file:content` blob, that are not a version or a proxy, and on which the user has `ReadWrite`. Each button calls one operation (_synchronously_), persists the result on the document and shows a notification.

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

#### Adding the Conversation button to the Home page

The conversation is not bound to a current document or a selection, so it also makes sense to expose it from the Home page.

* In **Studio Designer > UI**, click **Dashboard** — this opens the full default `nuxeo-home.html` file.
* Import the element at the top, then drop `<kd-conversation>` wherever you want. In the example below it is centered in the page header:

```html
<link rel="import" href="nuxeo-labs-content-intelligence-connector/elements/kd-conversation.html">
<dom-module id="nuxeo-home">
  <template>
    <style include="nuxeo-styles">
      . . .

      /* Center our conversation button */
      [slot="header"] {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .header-center {
        flex: 1;
        display: flex;
        justify-content: center;
        margin-right: 45px;
      }
    </style>

    <nuxeo-connection id="nx"></nuxeo-connection>
    <nuxeo-page>
      <div slot="header">
        <div> <!-- Add this div around nuxeo-repositories -->
          <nuxeo-repositories></nuxeo-repositories>
          [[i18n('home.dashboard')]]
        </div>
        <!-- conversation -->
        <div class="header-center">
          <kd-conversation icon="icons:question-answer" show-label="true"></kd-conversation>
        </div>
      </div>
      . . .
```

The `show-label="true"` attribute makes the button display its localized label (`label.ui.cic.kd-conversation`) next to the icon — useful in a header where there is room for it (the default in `DOCUMENT_ACTIONS` is icon-only).

### Agents

| Slot-content name | Slot | Icon | Operation | Visible on |
| --- | --- | --- | --- | --- |
| `cic-agents-lookupAgent` | `DOCUMENT_ACTIONS` | `icons:announcement` | `CIC.AgenticAgentLookup` | `CICAgenticAgentAndConfig` documents (`ReadWrite`) |

> Polymer `iron-icons` are part of the standard Web UI icon set; previews live at https://www.webcomponents.org/element/@polymer/iron-icons/demo/demo/index.html.

<br>

## Configuring which CIC buttons are visible (presales helper page)

The plugin ships a small admin page that lets an administrator quickly customize which CIC buttons are visible (and in which order), without writing XML by hand. It is meant primarily for **presales demos** — opening a prospect-specific scenario where only some buttons should be shown.

### Where to find it

* Log in as administrator.
* Open the **Administration drawer** (top-right user menu → Administration).
* In the left navigation, click **CIC UI Config** (entry registered at `ADMINISTRATION_MENU` order=100).

### What it does

The page calls the `CIC.GetUIBundleConfig` operation, which reads **this plugin's own bundle file** (`nuxeo.war/ui/nuxeo-labs-content-intelligence-connector/nuxeo-labs-content-intelligence-connector-bundle.html`) and returns the list of CIC slot-contents declared there, with their slot, order, disabled state, icon, label key, and full template body.

The page renders them grouped by category (KE Image, KE Text, KD, Agents) with one row per button:

| Icon | Slot-content name | Label | Slot | Default order | Enabled | Order |

You can:

* toggle **Enabled** to disable a button;
* type a new integer in **Order** to reorder buttons within the same slot;
* tick / untick **Minimal output (no template body)** to control the verbosity of the generated snippet (see below);
* click **Generate (all buttons)** to produce a Studio Web UI override snippet covering every row;
* click **Generate (changed only)** to produce a snippet only for the rows you actually modified;
* click **Copy to clipboard** to paste the snippet into your Studio project's Web UI Resources.

> ⚠️ **The page reads only this plugin's bundle.** It does NOT detect overrides coming from your Studio project, third-party plugins, or hand-edited bundles. The page intentionally keeps things simple: it shows you the plugin defaults, not the merged runtime state. A warning banner at the top of the page makes this explicit. If your runtime UI does not match what the page shows, another contribution is overriding the plugin's defaults — which is exactly what the generated snippet is meant to express.

### What the generated snippet looks like

By default the **Minimal output** checkbox is ticked. With minimal output, a row that is only disabled (or only re-emitted unchanged) produces a body-less tag — enough to override the plugin's default thanks to Web UI's same-`name` slot-content patch semantics:

```html
<nuxeo-slot-content name="cic-ke-image-named-entities" slot="DOCUMENT_ACTIONS" order="4" disabled>
</nuxeo-slot-content>
```

**Rows whose `order` was changed always emit the full `<template>` body**, regardless of the checkbox — so the reorder is unambiguous. Untick **Minimal output** to force the full body on every row (useful if you want a self-contained snippet to copy into a custom bundle that does not inherit from this plugin's defaults).

A header comment in the snippet records the plugin version, generation timestamp, source bundle path, and whether the snippet covers all buttons or only the changed ones. Paste the snippet as-is into a Studio Designer Web UI Resources HTML file (or any custom-bundle.html) — no further editing required.

### Nothing is persisted

The page does not save its state on the server. Your changes only exist in the browser until you click Generate and copy the result. This is intentional: the workflow is "open the page → tweak for this demo → copy → paste in Studio → forget".

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

### Tip: generate the snippet from the admin page

Instead of typing the imports + tags by hand, open **Administration > CIC UI Config**, switch to the **Form snippets** tab, tick the forms you want, and copy the generated block. The block includes a header comment reminding you to adapt the relative `../../` path to where you paste it.

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

## Automatic Enrichment (via event handlers in your Studio project)

The plugin intentionally ships only **synchronous, on-demand buttons** (visibility and filters tunable from your Studio project — see [Show / hide buttons per project](#show--hide-buttons-per-project) and [Configuring which CIC buttons are visible (presales helper page)](#configuring-which-cic-buttons-are-visible-presales-helper-page)). It does NOT ship listeners that automatically push every `File` or `Picture` to CIC. The reason is simple: deciding **which** documents to enrich is a business decision (cost, throughput, lifecycle, ACLs, metadata flags, container path, user, document subtype, …) and varies per project. Calling CIC for every newly created document is rarely what you want.

That said, wiring automatic enrichment yourself is straightforward. Every operation the plugin exposes (`CIC.*`, `HylandKnowledgeEnrichment.*`, `HylandKnowledgeDiscovery.*`, etc.) is a regular Nuxeo automation operation, so a few minutes in Studio are enough:

1. Create an **Event Handler** in your Studio project.
2. Bind it to the right event (see below), we recommend to set it **asynchronous** since calls to CIC can take time, and add the usual rule filters (`hasType`, `hasFacet`, lifecycle, not immutable, …).
3. Point it at an automation chain (regular or JS) that applies **your** business rule (folder, metadata, user, …) and, when it matches, calls the plugin's operation(s).

> [!TIP]
> Do not forget to set the `saveDocument` parameter to `true` when it makes sense.

Recommended event hooks:

* **Non rich-media documents** (`File`, or your custom `Contract`, etc...) → typically bind to **`documentCreated`** (filter: `hasType == Contract`, is not a version, …).
* **`Picture`-facet documents** → bind to **`pictureViewsGenerationDone`** (avoid `documentCreated`). By the time this event fires, `picture:views` is populated and you can pass any rendition (typically the **`FullHD`** view) to CIC — useful when the original is huge, RAW, or otherwise unsuitable.

Shape of such a chain (illustrative):

```js
// Bound to pictureViewsGenerationDone, async, filter: hasFacet == Picture, regular document
function run(input, params) {
  if (!shouldEnrich(input)) {        // your business rule
    return input;
  }
  // picture:views is now populated — pass the FullHD rendition to CIC
  // instead of the original (which may be too large or unsupported).
  input = CIC.GetImageDescription(input, {
    //"configName" => using default
    //"renditionName" => not passed => plugin uses the XML config of FullHD if nothing set
    //"instructionsV2JsonStr" => if you need special instructions
    "saveDocument": true
  });
  return input;
}

function shouldEnrich(doc) {
  // In this very simple example, we enrich automatically only documents that are at this hardcoded path
  return doc && doc.path.indexOf("/default-domain/workspaces/ACME/Import/" === 0);
}
```

<br>

## Multi-Document Enrichment (DocumentModelList input)

> Since plugin 2025.16.

All ten `CIC.*` document-oriented Knowledge Enrichment operations accept **either** a single `DocumentModel` **or** a `DocumentModelList`:

- `CIC.SummarizeText`, `CIC.GetTextMetadata`, `CIC.GetTextEmbeddings`, `CIC.GetNamedEntitiesFromText`, `CIC.ClassifyTextFile`
- `CIC.GetImageDescription`, `CIC.GetImageMetadata`, `CIC.GetImageEmbeddings`, `CIC.GetNamedEntitiesFromImage`, `CIC.ClassifyImage`

When a `DocumentModelList` is passed, the operation processes the documents in **sequential batches** and returns the same list reference, with each document mutated in place — success populates the action-specific schema and clears any prior `CICError`; failure populates the `CICError` facet (full upstream envelope JSON included in `cic_error:fullResponseJson`).

### Batch size

A new `batchSize` parameter controls how many documents are sent to CIC per HTTP call:

- `batchSize <= 0` (default) → falls back to the configured default `nuxeo.hyland.cic.enrichment.batchSize` (defaults to `10`).
- `batchSize > default` → honored, but a single WARN is logged.

Between batches (only when more batches remain) the plugin commiots the transaction.

> [!CAUTION]
> Processing is strictly sequential. To avoid time-out, we strongly recommend running these operations asynchronously when passing them a list of documents.


### `saveDocument` strongly recommended for multi-doc

- `saveDocument=false` (default): callers own persistence — including any `CICError` markers written in memory. Errors are also logged so no information is silently lost, but the in-memory mutations on documents past the first un-saved batch may be lost if the caller forgets to save.
- `saveDocument=true`: each modified doc is reassigned via `doc = session.saveDocument(doc)` immediately after every per-doc mutation (success or error).

For **multi-doc calls, pass `saveDocument=true`** unless you have a specific reason not to.

### Per-document error semantics (best-effort)

| Condition | Outcome |
|---|---|
| Doc has no blob | `CICError("No blob")` on that doc, skipped from the CIC payload |
| Batch HTTP call fails (IOException, non-2xx, `status != SUCCESS`, unparseable envelope) | `CICError` on every payload-eligible doc in the batch |
| Per-result error or missing result key in the response | `CICError` on the matching doc |
| Result entry whose `sourceId` is not in the current batch | logged WARN, no doc mutation |
| Doc was sent in the payload but absent from `response.results` | `CICError("Missing in CIC response")` on that doc |
| Per-doc success | `applyResult` writes the schema + `clearCICError` removes the facet |

### Configuration parameter

- `nuxeo.hyland.cic.enrichment.batchSize` (int, default `10`) — default batch size used when the operation `batchSize` parameter is `<= 0`.

### Example (JS Automation)

```javascript
function run(input, params) {
  // input is a DocumentList (query result, multi-select, ...)
  var enriched = CIC.SummarizeText(input, {
    'configName': 'default',
    'batchSize': 25,
    'saveDocument': true
  });
  return enriched;
}
```

<br>

## Async execution (`runAsynchronously`)

Every `CIC.*` document operation accepts an optional `runAsynchronously` boolean parameter (default `false`). When set to `true`, the call to Hyland CIC is scheduled as a background [Nuxeo Work](https://doc.nuxeo.com/nxdoc/work-and-workmanager/) and the operation returns the input document(s) immediately, unchanged. The Web UI buttons shipped by this plugin use this mode so the user is not blocked while CIC processes (which can take several seconds).

Key points:

- Persistence is forced to `saveDocument=true` inside the Work — async callers have no way to see the resulting document(s). Passing `saveDocument=false` together with `runAsynchronously=true` logs a single WARN per call.
- Errors land on each document via the standard `CICError` facet (same code path as synchronous calls), so failures are inspectable from the document's metadata view.
- Single-doc input → one Work for one doc. List input → one Work that processes the list in the same batched code path as the synchronous variant (the existing `batchSize` param still applies).
- The embeddings operations (`CIC.GetImageEmbeddings`, `CIC.GetTextEmbeddings`) still short-circuit *before* scheduling when the descriptor has no `embeddingsFacet` / `embeddings{Image,Text}Xpath` configured.
- All Works run under the `cicEnrichment` category. Cap concurrency in `nuxeo.conf` via `nuxeo.works.queue.cicEnrichment.maxThreads` (Nuxeo's default is `1`).

Example (JS Automation):

```javascript
CIC.SummarizeText(input, {
  'configName': 'default',
  'runAsynchronously': true
});
// returns immediately; CIC call runs in the background
```

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
