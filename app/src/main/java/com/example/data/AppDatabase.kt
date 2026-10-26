package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BookDao
import com.example.data.dao.ModelDao
import com.example.data.dao.VoiceDao
import com.example.data.model.BookEntity
import com.example.data.model.TtsModelEntity
import com.example.data.model.VoiceProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [BookEntity::class, TtsModelEntity::class, VoiceProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun modelDao(): ModelDao
    abstract fun voiceDao(): VoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kokoro_voice.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(getInstance(context))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(database: AppDatabase) {
            val modelDao = database.modelDao()
            val voiceDao = database.voiceDao()
            val bookDao = database.bookDao()

            // Seed default Hugging Face models
            modelDao.insertModels(
                listOf(
                    TtsModelEntity(
                        id = "hexgrad/Kokoro-82M",
                        name = "Kokoro-82M (Default)",
                        description = "State-of-the-art 82M neural TTS. Natural prosody & 24kHz audio output.",
                        source = "HUGGING_FACE",
                        repoId = "hexgrad/Kokoro-82M",
                        fileName = "kokoro-v1.0.onnx",
                        sizeBytes = 86_500_000L, // ~82.5 MB
                        isDownloaded = true,
                        isSelected = true,
                        sampleRate = 24000
                    ),
                    TtsModelEntity(
                        id = "onnx-community/Kokoro-82M-v1.0-ONNX",
                        name = "Kokoro-82M Quantized (INT8)",
                        description = "Lightweight mobile-optimized quantized ONNX model for high battery efficiency.",
                        source = "HUGGING_FACE",
                        repoId = "onnx-community/Kokoro-82M-v1.0-ONNX",
                        fileName = "kokoro-v1.0-int8.onnx",
                        sizeBytes = 44_200_000L, // ~42 MB
                        isDownloaded = false,
                        isSelected = false,
                        sampleRate = 24000
                    ),
                    TtsModelEntity(
                        id = "rhasspy/piper-voices-en_US",
                        name = "Piper Fast Acoustic (en-US)",
                        description = "Ultra low-latency lightweight neural model with near-instant synthesis.",
                        source = "HUGGING_FACE",
                        repoId = "rhasspy/piper-voices",
                        fileName = "en_US-lessac-medium.onnx",
                        sizeBytes = 38_100_000L,
                        isDownloaded = false,
                        isSelected = false,
                        sampleRate = 22050
                    )
                )
            )

            // Seed default Kokoro voice styles
            voiceDao.insertVoices(
                listOf(
                    VoiceProfileEntity(
                        id = "af_heart",
                        name = "Heart (Warm & Melodic)",
                        language = "en-us",
                        gender = "Female",
                        accent = "American",
                        description = "Most popular Kokoro voice. Warm, expressive and engaging tone.",
                        pitch = 1.0f,
                        speed = 1.0f,
                        energy = 1.0f,
                        isCustomImport = false,
                        isSelected = true
                    ),
                    VoiceProfileEntity(
                        id = "af_bella",
                        name = "Bella (Bright & Narrative)",
                        language = "en-us",
                        gender = "Female",
                        accent = "American",
                        description = "Energetic, clear articulate speech ideal for nonfiction.",
                        pitch = 1.05f,
                        speed = 1.0f,
                        energy = 1.0f,
                        isCustomImport = false,
                        isSelected = false
                    ),
                    VoiceProfileEntity(
                        id = "am_adam",
                        name = "Adam (Deep & Authoritative)",
                        language = "en-us",
                        gender = "Male",
                        accent = "American",
                        description = "Deep baritone resonance, perfect for thrillers and audiobooks.",
                        pitch = 0.95f,
                        speed = 1.0f,
                        energy = 1.05f,
                        isCustomImport = false,
                        isSelected = false
                    ),
                    VoiceProfileEntity(
                        id = "am_michael",
                        name = "Michael (Casual & Conversational)",
                        language = "en-us",
                        gender = "Male",
                        accent = "American",
                        description = "Relaxed conversational voice suited for podcasts and articles.",
                        pitch = 1.0f,
                        speed = 1.0f,
                        energy = 0.98f,
                        isCustomImport = false,
                        isSelected = false
                    ),
                    VoiceProfileEntity(
                        id = "bf_emma",
                        name = "Emma (Classic British Storyteller)",
                        language = "en-gb",
                        gender = "Female",
                        accent = "British",
                        description = "Refined British RP accent with gentle cadence and poise.",
                        pitch = 1.02f,
                        speed = 0.98f,
                        energy = 0.95f,
                        isCustomImport = false,
                        isSelected = false
                    ),
                    VoiceProfileEntity(
                        id = "bm_george",
                        name = "George (Distinguished British)",
                        language = "en-gb",
                        gender = "Male",
                        accent = "British",
                        description = "Aristocratic timbre, classic narrator for period literature.",
                        pitch = 0.92f,
                        speed = 0.96f,
                        energy = 1.02f,
                        isCustomImport = false,
                        isSelected = false
                    )
                )
            )

            // Seed preloaded sample ebook chapters
            bookDao.insertBook(
                BookEntity(
                    title = "The Time Machine (Chapter 1)",
                    author = "H. G. Wells",
                    content = """
The Time Traveller was expounding a recondite matter to us. His grey eyes shone and twinkled, and his usually pale face was flushed and animated. The fire burnt brightly, and the soft radiance of the incandescent lights in the lilies of silver caught the bubbles that flashed and passed in our glasses. Our chairs, being his patents, embraced and caressed us rather than submitted to be sat upon; and there was that luxurious after-dinner atmosphere when thought roams gracefully free of the trammels of precision. And he put it to us in this way, marking the points with a lean forefinger, as we sat and lazily admired his earnestness over this new paradox.

"You must follow me carefully. I shall have to controvert one or two ideas that are almost universally accepted. The geometry, for instance, they taught you at school is founded on a misconception."

"Is not that rather a large thing to expect us to begin upon?" said Filby, an argumentative person with red hair.

"I do not mean to ask you to accept anything without reasonable ground for it. You will soon admit as much as I need from you. You know of course that a mathematical line, a line of thickness nil, has no real existence. They taught you that? Neither has a mathematical plane. These things are mere abstractions."

"That is all right," said the Psychologist.

"Nor, having only length, breadth, and thickness, can a cube have a real existence."

"There I object," said Filby. "Of course a solid body may exist. All real things are three-dimensional."

"So most people think. But wait a moment. Can an instantaneous cube exist?"

"Don't follow you," said Filby.

"Can a cube that does not exist for any time at all, have a real existence?" Filby had to pause and think. "Clearly," the Time Traveller proceeded, "any real body must have extension in four directions: it must have Length, Breadth, Thickness, and Duration."
                    """.trimIndent(),
                    totalSentences = 17,
                    currentSentenceIndex = 0,
                    isPreset = true
                )
            )

            bookDao.insertBook(
                BookEntity(
                    title = "Neural Speech & On-Device AI",
                    author = "AI Research Insights",
                    content = """
On-device neural Text-to-Speech represents a monumental leap in accessibility, privacy, and user experience. Traditional voice engines relied on robotic concatenative sound clips. Modern models like Kokoro-82M utilize advanced style diffusion and acoustic conditioning to synthesize natural human cadence in real time.

By breaking long documents at natural sentence boundaries ending with a dot, parallel pipelined generation becomes possible. While the current sentence is articulated through the audio speaker, the background processor pre-generates the subsequent phrase. This technique guarantees completely gapless playback with zero audible stutter.

Furthermore, caching synthesized audio segments ensures that whenever a reader rewinds to replay an earlier passage, no regeneration is necessary. The result is ultra-low latency, maximum battery conservation, and seamless offline accessibility anywhere in the world.
                    """.trimIndent(),
                    totalSentences = 9,
                    currentSentenceIndex = 0,
                    isPreset = true
                )
            )
        }
    }
}
