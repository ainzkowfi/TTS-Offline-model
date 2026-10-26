# Full Ebook Audio FLAC Export, Multi-Engine TTS, & Kokoro Inno-Clone Voice Cloning

This update adds full audiobook export into standalone FLAC format for offline listening, multi-TTS engine architecture (Kokoro-82M, Piper, MeloTTS, and Android Neural), and zero-shot voice cloning powered by `remsky/kokoro-inno-clone-tuner` from reference audio recordings.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences have been incorporated into this plan:
- **Audio Download Format**: Full ebook audio is exported directly into lossless **FLAC (`.flac`)** (audio only, no book text) to the device's public storage so it can be played in any external media player, car stereo, or offline audio device.
- **Voice Cloning Strategy**: Seamlessly clone voices using `remsky/kokoro-inno-clone-tuner` by either recording live audio through the microphone or importing an existing reference speech clip (`.wav`, `.mp3`, `.m4a`).
- **Multi-TTS Support**: Expand the engine layer to support Kokoro-82M, Piper Neural TTS, MeloTTS, and Android Neural Speech Services.

---

## 1. Overview & Core Features

### 1. Full Ebook Audio FLAC Exporter
- Merges all generated sentence audio segments into a seamless, continuous audio stream without audible clicks or gaps.
- Encodes into native **FLAC** (Free Lossless Audio Codec) using Android's native audio pipeline.
- Writes to public device storage (`MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` / `Downloads`) with audio metadata tags (Track Title, Artist/Voice Name, Book Title).
- Provides a direct "Download Full Audio (FLAC)" button in the Reader screen and after batch pre-rendering.

### 2. Multi-TTS Engine Architecture
- **Kokoro-82M (Default)**: 24kHz style-diffusion neural TTS with multi-speaker embeddings.
- **Piper Neural TTS**: Ultra-lightweight low-latency on-device neural voices.
- **MeloTTS**: Real-time prosody-conditioned multi-speaker synthesis.
- **System Neural TTS**: Hardware-accelerated offline Android neural speech services.
- Seamless engine switcher in the Models tab with active model badges and engine configurations.

### 3. Voice Cloning with `remsky/kokoro-inno-clone-tuner`
- Integrated Hugging Face repository `remsky/kokoro-inno-clone-tuner`.
- **Clone Voice Studio**:
  - Live 5–10 second voice recording with visual decibel meter.
  - Or import any reference audio file (`.wav`, `.mp3`, `.m4a`, `.ogg`).
  - Distilled speaker acoustic analyzer: extracts pitch ceiling ($F_0$), harmonic overtone distribution, and rhythm prosody.
  - Generates a Kokoro-compatible style vector profile and saves it to the custom voice library.
  - Provides instant "Test Cloned Voice" preview button.

---

## 2. Technical Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Ebook / PDF Reader UI                           │
│  - Playback Bar            - Pre-render Banner                         │
│  - [Download Full Audio (FLAC)] Button                                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        FlacAudioExporter                               │
│  - Collects all sentence audio segments in order                       │
│  - Concatenates PCM streams at native sample rate                      │
│  - Encodes to FLAC format with lossless compression                    │
│  - Writes to MediaStore / Downloads with ID3 / Vorbis tags             │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                       Voice Cloning Pipeline                           │
│            (remsky/kokoro-inno-clone-tuner integration)                │
│  1. Audio Input: Record from Mic or Import WAV/MP3                     │
│  2. Acoustic Analysis: F0 pitch tracking, spectral centroid, formants   │
│  3. Latent Mapping: Maps into Kokoro [510, 1, 256] style vector space  │
│  4. Enrolls new VoiceProfileEntity (usable across all books)           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Implementation Steps

1. **`audio/FlacAudioExporter.kt`**:
   - Concatenates sentence segments into a contiguous PCM audio buffer.
   - Encodes 16-bit PCM samples into standard lossless FLAC container with FLAC stream header and metadata blocks.
   - Writes directly to public audio directory via `ContentResolver` / `MediaStore`.

2. **`engine/VoiceCloneEngine.kt`**:
   - Implements the `remsky/kokoro-inno-clone-tuner` acoustic encoder interface.
   - Extracts pitch tracking, resonance envelope, and timbre weights from reference audio.
   - Generates persistent custom voice profiles in Room database.

3. **`ui/screens/VoiceCloneDialog.kt`**:
   - Modern Compose dialog with microphone recorder, waveform meter, file picker, and clone preview.

4. **Engine & UI Integration**:
   - Wire "Download Full Audio (FLAC)" button to Reader toolbar and batch pre-rendering dialog.
   - Add multi-engine selector to Models tab (Kokoro, Piper, MeloTTS, System).
