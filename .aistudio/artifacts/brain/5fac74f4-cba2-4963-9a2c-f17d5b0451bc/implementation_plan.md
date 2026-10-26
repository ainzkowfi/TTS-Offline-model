# Ebook & PDF Import, Punctuation Pre-Rendering, & Adjustable Pre-Generation

This update extends Kokoro Voice with native document parsers for PDF and EPUB formats, automated whole-book punctuation normalization, user-adjustable lookahead pre-generation (1 to 5 sentences), and on-demand full chapter pre-rendering.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions have been confirmed and incorporated into the design:

- **Ebook & PDF Text Extraction**:
  - Automated cleaning and paragraph normalization: stripping running headers, footers, page numbers (e.g., "Page 12 of 140", standalone numbers), and artifact watermarks.
  - EPUB parser unpacking ZIP container files, reading XHTML/HTML content documents in spine order, and extracting clean textual prose.
  - PDF text parser extracting plain text streams while rejoining hyphenated line wraps.
- **Adjustable Pre-Generation Buffer (1 to 5 Sentences)**:
  - An interactive slider and chip selector in the playback bar and reader settings allowing the user to configure how many upcoming sentences are pre-generated ahead of playback ($N+1$ up to $N+K$, where $K \in [1, 5]$).
- **Whole-Book Punctuation Processing & Pre-Rendering**:
  - Punctuation normalizer: standardizes smart quotes, em-dashes, ellipses (`...`), brackets, and dialogue markers to ensure maximum acoustic fidelity and prosody for the neural TTS engine.
  - One-tap "Pre-render All / Chapter" action in the Reader toolbar, with a progress bar and cancel button, allowing readers to batch-render and cache sentences for complete offline listening before travel.

---

## 1. Overview & Core Concept

### What It Does
Users can now import `.epub` and `.pdf` files directly via the Android file picker. The app extracts clean prose, cleans headers and footers, and runs a comprehensive punctuation processor that standardizes cadence marks for neural TTS. Readers can control the background generation aggressiveness with an adjustable slider (1 to 5 sentences ahead) or trigger full chapter pre-rendering with a visual progress bar.

### Target Persona
- Readers listening to long EPUB books and PDF research papers or articles.
- Commuters preparing reading material for offline flights without cellular or WiFi access.
- Users seeking customizable battery vs. lookahead buffer balance.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Importing PDF or EPUB**:
   - In the Library tab, the user taps **Open File** or the **+** FAB.
   - The Android file picker allows selecting `.pdf`, `.epub`, `.txt`, or `.md` files.
   - The app parses the file, strips headers/footers, and runs punctuation normalization.
   - The book opens directly in the Reader, showing total extracted sentences.

2. **Adjusting Lookahead Pre-Generation (1 to 5 Sentences)**:
   - In the Player bottom bar, tapping the pre-gen settings pill reveals a slider: **Pre-generate Ahead: [1 | 2 | 3 | 4 | 5 sentences]**.
   - Setting it to 1 runs minimal battery double-buffering; setting it to 3–5 builds an extensive audio buffer ahead of time.

3. **Pre-Rendering the Entire Book**:
   - The top bar features a **Pre-render Book** button.
   - Tapping it opens a dedicated batch progress modal: *"Pre-rendering sentences: 14/85 (16%) • Kokoro Neural Engine"*, with a progress bar and *Stop / Pause* button.
   - All generated sentences are saved into the local WAV cache, ready for instant offline replay.

### Visual Identity & Theme
- Consistent with Obsidian Dark & Electric Cyan theme.
- New status indicators:
  - Blue/cyan pulse for active pre-generation queue.
  - Green checkmark tags for pre-rendered sentences.
  - Lookahead queue chips showing exact sentence indices currently being buffered.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Built-in Zero-Dependency EPUB & PDF Extraction
- **Approach**: 
  - EPUB: Extract via `java.util.zip.ZipInputStream`, parsing container metadata (`content.opf`) to extract XHTML/HTML chapter flows in order, stripping HTML tags into clean paragraph blocks.
  - PDF: Lightweight PDF text stream extractor parsing stream operators (`BT`, `ET`, `Tj`, `TJ`) and rejoining hyphenated broken words (`exam-` + `ple` $\to$ `example`).
- **Why**: Eliminates massive 25MB external native PDF libraries (which increase APK size and slow build times) while extracting clean text from standard documents and books.

### Decision 2: Punctuation Normalization Pipeline
- **Approach**: Replace irregular unicode symbols, curly quotes, en/em dashes (`—` $\to$ `, ` or pause mark), ellipses (`...` $\to$ pause token) before sentence tokenization.
- **Why**: Neural TTS engines produce robotic glitches when encountering weird unicode glyphs or abrupt punctuation breaks. Normalizing them ensures smooth human-like cadence.

---

## 4. Technical Architecture & Data Strategy

### Pre-Generation Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Document Import Pipeline                        │
│  - EPUB Unpacker (.epub)         - PDF Text Extractor (.pdf)           │
│  - Text / Markdown (.txt, .md)   - Punctuation Normalizer              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        ReaderViewModel                                 │
│  - lookaheadCount: Int (1 to 5)                                        │
│  - isBatchPreRendering: Boolean                                        │
│  - batchProgress: (current, total, percent)                            │
└───────────────┬───────────────────────────────────────┬────────────────┘
                │ Active Playback                       │ Lookahead Worker
                ▼                                       ▼
┌───────────────────────────────┐       ┌────────────────────────────────┐
│         AudioPlayer           │       │    Pipelined Pre-Gen Engine    │
│  - Plays Sentence [N] Audio   │       │  - Checks Cache [N+1 .. N+K]   │
│  - Speed Tuner (0.5x - 2.5x)  │       │  - Pre-renders up to K ahead   │
│  - Zero Gap Completion Handover│◀──────│  - Emits Ready per Segment     │
└───────────────────────────────┘       └────────────────────────────────┘
```

### New Components to Implement
1. `domain/EbookParser.kt`: Handles `.epub` ZIP extraction, `.pdf` stream text extraction, and running header/footer sanitization.
2. `domain/PunctuationNormalizer.kt`: Cleans typography, em-dashes, ellipses, dialogue quotes, and abbreviations for neural speech.
3. `audio/PipelinedAudioPlayer.kt`: Extended with `lookaheadCount: Int` (1 to 5) to buffer multiple sentences ahead.
4. `ui/components/PreRenderDialog.kt`: Modal with progress bar and stop button for batch pre-rendering an entire book.
