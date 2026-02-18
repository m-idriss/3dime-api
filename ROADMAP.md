# 3dime-api Roadmap

This roadmap outlines the strategic direction for the **3dime-api** project, building upon the recently completed performance and reliability improvements.

## 🏁 Phase 1: Stability & Developer Experience (Short-term)
*Focus on making the core engine rock-solid and highly observable.*

- [x] **Performance: Cached Credentials**: Implement credential caching for Gemini API (Completed).
- [x] **Performance: App Caching**: Implement `@CacheResult` for GitHub/Notion (Completed).
- [ ] **Enhanced Image Processing**: Implement support for URL-based images in `GeminiService` (currently warning only).
- [ ] **Advanced Error Mapping**: Refine `GlobalExceptionMapper` to handle Gemini-specific errors (rate limits, safety filters).
- [ ] **Structured Logging**: Integrate Micrometer or OpenTelemetry for better visibility.
- [ ] **Expanded Test Coverage**: Increase unit test coverage for `QuotaService` and `NotionService` edge cases.

## 🚀 Phase 2: Feature Expansion (Mid-term)
*Broadening the value proposition and integration surface.*

- [ ] **Direct Calendar Integration**: Add direct export to Google Calendar and Outlook APIs.
- [ ] **Support for Multiple Formats**: Enable event extraction into CSV and JSON.
- [ ] **Image Pre-processing**: Implement basic image manipulation (contrast/sharpening) for better OCR.
- [ ] **GitHub Webhooks**: Transition from polling to webhooks for real-time stats.

## 💎 Phase 3: Scale & Monetization (Long-term)
*Transitioning from a personal tool to a production SaaS platform.*

- [ ] **Fast Startup (Native Image)**: Full GraalVM optimization for sub-second cold starts.
- [ ] **Payments Bridge**: Integrate Stripe to automate plan upgrades.
- [ ] **Admin Dashboard**: Build a management interface using Notion CMS data as a backend.
- [ ] **Multi-tenancy**: Architect isolation levels for enterprise data silos.

---
> [!NOTE]
> This roadmap is a living document and should be adjusted based on user feedback and technical priorities.
