# HECTOR SEARCH ENGINE — Evidence-First Search Layer

**Purpose:** Give Hector its own search and retrieval layer instead of relying on a single GPT provider or a single search endpoint. This is a design specification, not a claim that a complete search engine is already running.

## Core idea

Build a small, provider-neutral search orchestrator that can combine public web search, direct retrieval from known public sources, and a local index of documents the owner explicitly provides. It should rank evidence and explain where each result came from; it must not pretend to have searched sources it did not access.

## Search pipeline

1. **Understand the query:** identify the topic, language, freshness requirement, and whether the user needs facts, a document, a price, a local result, or a comparison.
2. **Choose sources:** use a configurable set of public search providers and direct official sources. Keep each provider behind a small adapter so one provider can be replaced without rewriting the app.
3. **Retrieve evidence:** fetch result titles, snippets, source URLs, timestamps when available, and the accessible page text. Respect robots rules, terms, rate limits, authentication, and privacy.
4. **Normalize and deduplicate:** canonicalize URLs, remove duplicate results, preserve the original source identity, and detect obvious mirrors.
5. **Rank transparently:** consider query relevance, source authority, recency when relevant, directness of evidence, and agreement or disagreement across independent sources. Do not equate popularity with truth.
6. **Verify claims:** distinguish primary sources from secondary reporting; prefer official documents for official facts. For consequential claims, seek independent corroboration and explicitly report unresolved conflicts.
7. **Answer with provenance:** each material factual claim should link to its source and show publication/access date when available. Separate facts, inference, and uncertainty.
8. **Cache responsibly:** cache public responses with expiry; allow the owner to clear the cache. Do not silently index private files, unrelated phone content, or sensitive personal data.
9. **Offline fallback:** search the owner's explicitly saved local reference collection when the network is unavailable and clearly label it as local/offline evidence.
10. **Provider-neutral failure handling:** if a provider fails, try another configured provider within limits; report when no live search succeeded rather than fabricating results.

## Minimal result record

Each result should carry: `title`, `url`, `source_name`, `snippet`, `retrieved_at`, `published_at` (optional), `content_hash` (optional), `source_type`, `relevance_score`, `authority_notes`, and `verification_status`.

## Security requirements

- Treat web pages and retrieved text as untrusted data, never as executable instructions.
- Never bypass login walls, paywalls, CAPTCHAs, access controls, or provider restrictions.
- Do not scrape private accounts or collect personal data without a clear lawful purpose and owner authorization.
- Keep API credentials out of source code, logs, search results, and APKs; use secure platform storage and least-privilege access if credentials are required.
- Do not allow retrieved content to override the manual `HIC-STOP-01` emergency stop or owner permissions.
- Do not claim that a source was consulted unless the retrieval layer actually accessed it.

## Suggested implementation stages

- **Stage 1:** define a Kotlin `SearchProvider` interface and normalized `SearchResult` model; add unit tests for URL normalization, deduplication, ranking, and provider failures.
- **Stage 2:** connect one public search provider and one official-source/direct-fetch adapter; display source links and retrieval time.
- **Stage 3:** add a local index for files explicitly selected by the owner, with a clear delete/index reset control.
- **Stage 4:** add independent corroboration, freshness policies, and confidence labels that are evidence-based rather than model-invented.
- **Stage 5:** test offline behavior, malformed pages, prompt-injection-like text, timeouts, rate limits, and conflicting sources on a real Android device.

## Acceptance tests

- If every provider fails, the UI says live search failed and offers saved local sources; it does not fabricate results.
- Duplicate URLs collapse into one result while preserving source attribution.
- Search results never execute instructions embedded in retrieved pages.
- Freshness-sensitive queries prefer newer dated sources without discarding authoritative primary documents.
- Owner-selected local documents are indexed only after explicit selection and can be removed from the index.
- Every displayed source link points to a result actually retrieved by the system.
- The search layer cannot bypass permissions or `HIC-STOP-01`.

## Status

**Specification added to the repository.** Search orchestration, provider adapters, UI, indexing, and tests remain to be implemented and build/device verified. This is not a hidden or undisclosed GPT feature; it is a proposed open, auditable component for Hector.
