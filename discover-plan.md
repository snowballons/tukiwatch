 Let me now create a comprehensive plan for this feature.                                                                              

 Now I have a thorough understanding of the codebase. Let me create a comprehensive plan for the Discovery Screen feature.             

╭─── ☑ Todo 20 tasks ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
│ I. Architecture & Data Model  0/4                                                                                                   │
│   ├─ ☐ Design Discovery data model (extends LiveStream with Twitch-specific fields)                                                 │
│   ├─ ☐ Define API endpoints for Twitch Helix discovery (browse live streams)                                                        │
│   ├─ ☐ Plan hourly refresh mechanism with backend caching                                                                           │
│   └─ ☐ Design multi-platform extensibility                                                                                          │
│ II. Backend Implementation  0/4                                                                                                     │
│   ├─ ☐ Add Twitch Helix client to backend (app access token flow)                                                                   │
│   ├─ ☐ Create /api/discover/twitch endpoint with filters (game, language, pagination)                                               │
│   ├─ ☐ Add hourly background job to cache top streams                                                                               │
│   └─ ☐ Add rate limit handling per Twitch guidelines                                                                                │
│ III. Mobile App - Core  0/4                                                                                                         │
│   ├─ ☐ Create DiscoveryScreen component with browse UI                                                                              │
│   ├─ ☐ Add Discovery tab to bottom navigation (between Add and Settings)                                                            │
│   ├─ ☐ Create useDiscovery hook for data fetching & hourly refresh                                                                  │
│   └─ ☐ Implement pull-to-refresh and infinite scroll pagination                                                                     │
│ IV. Mobile App - Features  0/4                                                                                                      │
│   ├─ ☐ Add 'Add to Library' action on StreamCard in discovery mode                                                                  │
│   ├─ ☐ Add platform filter chips (Twitch first, extensible)                                                                         │
│   ├─ ☐ Add category/game browser                                                                                                    │
│   └─ ☐ Add language filter                                                                                                          │
│ V. Integration & Polish  0/4                                                                                                        │
│   ├─ ☐ Wire navigation from Discovery to PlayerScreen                                                                               │
│   ├─ ☐ Ensure StreamCard works in discovery context (showAddButton)                                                                 │
│   ├─ ☐ Test hourly refresh and cache behavior                                                                                       │
│   └─ ☐ Verify multi-platform structure for future expansion 
