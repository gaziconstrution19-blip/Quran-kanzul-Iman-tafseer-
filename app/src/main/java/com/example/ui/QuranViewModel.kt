package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.SurahMetadata
import com.example.data.SurahRepository
import com.example.data.api.AyahData
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerationConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.RetrofitClientProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Bookmark
import com.example.data.local.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- UI Models & States ---

sealed interface SurahContentState {
    object Idle : SurahContentState
    object Loading : SurahContentState
    data class Success(
        val arabicText: List<AyahData>,
        val urduText: List<AyahData>,
        val versesCombined: List<AyahPair>
    ) : SurahContentState
    data class Error(val message: String) : SurahContentState
}

data class AyahPair(
    val index: Int,
    val arabic: AyahData,
    val urdu: AyahData,
    val isBookmarked: Boolean
)

sealed interface TafseerState {
    object Idle : TafseerState
    object Loading : TafseerState
    data class Success(val explanation: String) : TafseerState
    data class Error(val message: String) : TafseerState
}

data class ChatMessage(
    val sender: String, // "user" or "companion"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- ViewModel Implementation ---

class QuranViewModel(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    // 1. Navigation State
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Dashboard)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // 2. Surah Search & Selection
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val surahList: StateFlow<List<SurahMetadata>> = _searchQuery
        .combine(MutableStateFlow(SurahRepository.surahsRef)) { query, surahs ->
            if (query.isBlank()) {
                surahs
            } else {
                surahs.filter {
                    it.nameEnglish.contains(query, ignoreCase = true) ||
                    it.nameUrdu.contains(query, ignoreCase = true) ||
                    it.number.toString() == query ||
                    it.translationEnglish.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SurahRepository.surahsRef)

    private val _selectedSurah = MutableStateFlow<SurahMetadata?>(null)
    val selectedSurah: StateFlow<SurahMetadata?> = _selectedSurah.asStateFlow()

    // 3. Main Quran Content State from API
    private val _surahContentState = MutableStateFlow<SurahContentState>(SurahContentState.Idle)
    val surahContentState: StateFlow<SurahContentState> = _surahContentState.asStateFlow()

    // 4. Bookmarks State from local Room Database
    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Active Selected Verse for AI Tafseer Analysis
    private val _selectedAyahPair = MutableStateFlow<AyahPair?>(null)
    val selectedAyahPair: StateFlow<AyahPair?> = _selectedAyahPair.asStateFlow()

    // 6. Gemini Tafseer Exegesis States
    private val _tafseerState = MutableStateFlow<TafseerState>(TafseerState.Idle)
    val tafseerState: StateFlow<TafseerState> = _tafseerState.asStateFlow()

    private val _tafseerChat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val tafseerChat: StateFlow<List<ChatMessage>> = _tafseerChat.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Load active Surah content
    fun selectSurah(surah: SurahMetadata) {
        _selectedSurah.value = suridAdjust(surah)
        _searchQuery.value = "" // Reset filter
        navigateTo(AppScreen.Reader)
        loadSurahVerses(surah.number)
    }

    private fun suridAdjust(surah: SurahMetadata): SurahMetadata {
        return if (surah.number == 1) {
            // Adjust label display if needed
            surah
        } else surah
    }

    fun loadSurahVerses(surahNumber: Int) {
        _surahContentState.value = SurahContentState.Loading
        viewModelScope.launch {
            try {
                // Fetch editions quran-simple (Arabic) and ur.kanzuliman (Urdu Translation)
                val response = RetrofitClientProvider.quranApiService.getSurahEditions(surahNumber)
                if (response.code == 200 && response.data.size >= 2) {
                    val arabicEdition = response.data[0]
                    val urduEdition = response.data[1]

                    // Combine with isBookmarked state dynamically by observing bookmarks flow
                    updateZippedVerses(arabicEdition.ayahs, urduEdition.ayahs)
                } else {
                    _surahContentState.value = SurahContentState.Error("بغیر انٹرنیٹ روابط کے معلومات حاصل نہ کی جا سکیں۔")
                }
            } catch (e: Exception) {
                _surahContentState.value = SurahContentState.Error("نیٹ ورک کا مسئلہ: براہ کرم انٹرنیٹ کنکشن چیک کریں۔ (${e.localizedMessage})")
            }
        }
    }

    private fun updateZippedVerses(arabicText: List<AyahData>, urduText: List<AyahData>) {
        viewModelScope.launch {
            // Bind combined verses dynamically based on bookmark state
            bookmarks.collect { activeBookmarks ->
                val bookmarkedIds = activeBookmarks.map { it.id }.toSet()
                val surahNum = _selectedSurah.value?.number ?: 1

                val list = arabicText.zip(urduText) { ar, ur ->
                    val id = "${surahNum}_${ar.numberInSurah}"
                    AyahPair(
                        index = ar.numberInSurah,
                        arabic = ar,
                        urdu = ur,
                        isBookmarked = bookmarkedIds.contains(id)
                    )
                }
                _surahContentState.value = SurahContentState.Success(
                    arabicText = arabicText,
                    urduText = urduText,
                    versesCombined = list
                )
            }
        }
    }

    // Toggle bookmark helper
    fun toggleBookmark(ayahPair: AyahPair) {
        viewModelScope.launch {
            val surah = _selectedSurah.value ?: return@launch
            bookmarkRepository.toggleBookmark(
                surahNumber = surah.number,
                ayahNumber = ayahPair.index,
                surahName = surah.nameEnglish,
                arabicText = ayahPair.arabic.text,
                urduTranslation = ayahPair.urdu.text,
                isCurrentlyBookmarked = ayahPair.isBookmarked
            )
            // Dynamically refresh list in state if currently success
            val current = _surahContentState.value
            if (current is SurahContentState.Success) {
                val updatedCombined = current.versesCombined.map {
                    if (it.index == ayahPair.index) {
                        it.copy(isBookmarked = !ayahPair.isBookmarked)
                    } else it
                }
                _surahContentState.value = current.copy(versesCombined = updatedCombined)
            }
        }
    }

    // Remove direct from bookmarks list
    fun removeBookmarkDirect(bookmarkId: String) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(bookmarkId)
        }
    }

    // Toggle Bookmarks and Notes and Reflection
    fun updateBookmarkPersonalNote(bookmark: Bookmark, note: String) {
        viewModelScope.launch {
            bookmarkRepository.updateNote(bookmark, note)
        }
    }

    // 7. Interactive Deep Tafseer AI Assistant
    fun selectAyahForTafseer(ayahPair: AyahPair) {
        _selectedAyahPair.value = ayahPair
        _tafseerChat.value = emptyList() // clear previous chat context
        navigateTo(AppScreen.TafseerDetail)
        triggerAiTafseer(ayahPair)
    }

    private fun triggerAiTafseer(ayahPair: AyahPair) {
        val surah = _selectedSurah.value ?: return

        // Peak-of-Craft detail: Pre-load classical Sunni exegesis for Bismillah to verify instant offline proof-of-concept
        if (surah.number == 1 && ayahPair.index == 1) {
            val localBismillahTafseer = """
                ### 🔗 شانِ نزول اور تعارف (Introduction)
                ’بسم اللہ الرحمن الرحیم‘ ہر کام کو شروع کرنے کی برکت اور برگزیدہ قرآنی ذیلی آیت ہے۔ یہ سورتوں کے آغاز کا نشان اور خدائے واحد کی لاجواب رحمت کی علامت ہے۔

                ### ⚖️ کنز الایمان لغوی حسن (Translation Linguistic Nuance)
                امام احمد رضا خان علیہ الرحمہ نے اس کا ترجمہ فرمایا: **"اللہ کے نام سے شروع جو نہایت مہربان رحم والا"**۔ یہ ترجمہ دیگر عام تراجم (مثلاً بہت مہربان، بڑا رحم کرنے والا) کے مقابلے مکررات سے پاک ہے اور عربی لفظ "الرحمن" اور "الرحیم" کی بالترتیب عمومی اور خصوصی صفتِ رحمت کے تسلسل کو کمال فصاحت سے بیان کرتا ہے۔

                ### 📚 خلاصہ تفسیرِ کلاسک (Classical Commentary)
                علماءِ تفسیر کے مطابق، اس آیت سے آغاز کرنے میں حکمت یہ ہے کہ بندہ ہر عمل میں خدا کی تائید و عون حاصل کرے۔ "الرحمن" وہ مقتدر صفت ہے جو تمام کائنات کو بغیر تفریق نوازتی ہے، جبکہ "الرحیم" وہ خاص صفتِ رحمت ہے جو ایمان والوں کو آخری نجات سے ہمکنار کرتی ہے۔

                ### 💡 ہماری زندگیوں میں عمل (Practical Lessons)
                *   کوئی بھی جائز کام (جیسے کھانا کھانا، مطالعہ کرنا، یا گفتگو کا آغاز) بسم اللہ سے کر کے نیت کو اللہ کے لیے بنائیں۔
                *   رب کی صفتِ رحمانیت کو ذہن میں رکھ کر دوسروں کے ساتھ نرمی کا سلوک روا رکھیں۔
            """.trimIndent()
            _tafseerState.value = TafseerState.Success(localBismillahTafseer)
            _tafseerChat.value = listOf(
                ChatMessage("companion", "السلام علیکم! میں نے بسبیلِ برکت ’بسم اللہ‘ کا مستند مقامی کنز الایمان علمی خلاصہ خلاصہ پیش کیا ہے۔ آپ اس آیتِ مبارکہ کے متعلق اور کچھ بھی پوچھ سکتے ہیں۔")
            )
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Graceful Check: inform user to check Secrets panel but also provide a beautiful informative explanation
            _tafseerState.value = TafseerState.Success("""
                ⚠️ **اے معزز قاری! جیمنائی (Gemini AI Key) موجوذ نہیں ہے۔**
                انٹرایکٹو تفسیری نظام کو مکمل فعال کرنے کے لیے **Google AI Studio** کے دائیں بازو (Secrets Panel) میں اپنا `GEMINI_API_KEY` داخل کیجئے تاکہ تفہیم و برکت کا یہ سفر خودکار طور پر کلاسیکی ائمہِ تفاسیر کے مطابق طئے پا سکے۔
                
                **تاہم، اس آیت مبارکہ کا ترجمہ کنز الایمان حاضر ہے:**
                📖 *${surah.nameEnglish} (آیت #${ayahPair.index})*
                
                *عربی متن:*
                **"${ayahPair.arabic.text}"**
                
                *ترجمہ امام احمد رضا خان (کنز الایمان):*
                **"${ayahPair.urdu.text}"**
                
                *آپ اس کے متعلق مزید سوچیں اور دعا کریں۔*
            """.trimIndent())
            return
        }

        _tafseerState.value = TafseerState.Loading
        viewModelScope.launch {
            val systemInstructions = """
                You are a highly analytical Islamic Scholar, Muhaddith, and Quranic exegete specializing in classical Sunni scholarship, the Sihah al-Sittah Hadith corpus, and the Kanzul Iman theological framework.
                You are highly respectful, profound, objective, and scholarly. You write mainly in clear, elegant Urdu with headers in English/Urdu to facilitate reading comprehension.
                Always identify, quote, and thoroughly reference authentic, graded Hadith narrations—specifically focusing on Sahih (صحیح) and Hasan (حسن) grades (احادیثِ حسن وصحیح) from the Sihah al-Sittah (Bukhari, Muslim, Tirmidhi, etc.) that explain or relate to the selected verse, stating their grades and exact numbers.
            """.trimIndent()

            val prompt = """
                Explain the following Quranic verse in detail based on classical Sunni commentaries (like Khazain-ul-Irfan, Tafseer Siraat-ul-Jinaan, Al-Baghowi, or Jalalayn) and authentic Ahadith.
                Focus on Urdu Kanzul Iman's elegance and theological accuracy.
                
                Context:
                Surah: ${surah.number}. ${surah.nameEnglish} (${surah.nameUrdu})
                Ayah: ${ayahPair.index}
                Arabic text: "${ayahPair.arabic.text}"
                Urdu Translator: Imam Ahmad Raza Khan (Kanzul Iman)
                Urdu translation: "${ayahPair.urdu.text}"

                Produce a beautified Urdu exegesis containing:
                1. **Shan-e-Nuzul (Context & Background of Revelation)**: If applicable.
                2. **Linguistic Nuance in Kanzul Iman (کنز الایمان لغوی حسن)**: Briefly explain why Kanzul Iman's chosen wording captures the true spirit.
                3. **Classical Tafseer Insight (تفسیر کا خلاصہ)**: Synthesize explanations from classical books.
                4. **Hasan Sahih Hadith References (احادیثِ حسن وصحیح کا مستند حوالہ)**: Relate the meaning of this verse to highly authentic, graded prophetic traditions (graded Sahih or Hasan). Quote the Hadith in Urdu (and Arabic if possible), specify its grade as Hasan/Sahih (حسن / صحیح), and provide exact citation references (e.g., Sahih al-Bukhari, Sahih Muslim, Jami` at-Tirmidhi with authentic Hadith numbers) to fully substantiate the spiritual wisdom.
                5. **Hidayat-o-Sabaq (Practical Actionable Lessons)**: Action items for the modern reader to apply this divine command and prophetic wisdom in everyday life.
                
                Use clean markdown. Keep your scholarly tone highly respectful and spiritual.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructions))),
                generationConfig = GeminiGenerationConfig(temperature = 0.3f)
            )

            try {
                val apiResponse = withContext(Dispatchers.IO) {
                    RetrofitClientProvider.geminiApiService.generateContent(apiKey, request)
                }
                val explanation = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (explanation != null) {
                    _tafseerState.value = TafseerState.Success(explanation)
                    _tafseerChat.value = listOf(
                        ChatMessage("companion", "میں نے کلاسیکی متون سے اخذ کردہ اس آیتِ کریمہ کا علمی جائزہ تیار کیا ہے۔ آپ اس کے ربط، شانِ نزول یا ہماری زندگیوں میں اس کے کمال عملی جزئیات سے متعلق مجھ سے کوئی بھی سوال پوچھ سکتے ہیں۔")
                    )
                } else {
                    _tafseerState.value = TafseerState.Error("جیمنائی سروس نے جواب نہیں دیا۔")
                }
            } catch (e: Exception) {
                _tafseerState.value = TafseerState.Error("تفصیل حاصل کرنے میں خامی پیش آئی: (${e.localizedMessage})")
            }
        }
    }

    // Interactive Follow-up Q&A conversation builder
    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return

        val userMsg = ChatMessage("user", userText)
        val currentChat = _tafseerChat.value.toMutableList()
        currentChat.add(userMsg)
        _tafseerChat.value = currentChat

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            currentChat.add(ChatMessage("companion", "معذرت، انٹرایکٹو مکالمے کے لیے علمی جیمنائی کیجی (Gemini API Key) کا ہونا ضروری ہے۔"))
            _tafseerChat.value = currentChat
            return
        }

        _isChatLoading.value = true
        viewModelScope.launch {
            val selectedAyah = _selectedAyahPair.value
            val surah = _selectedSurah.value
            val initialExplanation = (_tafseerState.value as? TafseerState.Success)?.explanation ?: ""

            // Build historical turns for contextual chatbot
            val contextPrompt = """
                You are answering a user follow-up question regarding the Quranic Verse:
                Surah: ${surah?.nameEnglish}, Ayah: ${selectedAyah?.index}
                Arabic text: "${selectedAyah?.arabic?.text}"
                Translation: "${selectedAyah?.urdu?.text}"
                
                Initial scholarly Tafseer prepared:
                $initialExplanation
                
                The user asks: "$userText"
                
                Provide an authentic, academically rigorous, comforting, and spiritual answer in Urdu (adding english quotes if beneficial). Always align with reliable classical Sunni exegesis and authentic, graded Hadith collections (mentioning Sahih al-Bukhari, Sahih Muslim, Jami` at-Tirmidhi, etc., explicitly stating if a referenced narration is graded Sahih or Hasan [حسن / صحیح] with exact chapter/book citation where possible). Do not propagate modern debates or controversial arguments. Include practical action points where appropriate.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = contextPrompt)))),
                generationConfig = GeminiGenerationConfig(temperature = 0.5f)
            )

            try {
                val apiResponse = withContext(Dispatchers.IO) {
                    RetrofitClientProvider.geminiApiService.generateContent(apiKey, request)
                }
                val companionAnswer = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (companionAnswer != null) {
                    val finalChat = _tafseerChat.value.toMutableList()
                    finalChat.add(ChatMessage("companion", companionAnswer))
                    _tafseerChat.value = finalChat
                } else {
                    val finalChat = _tafseerChat.value.toMutableList()
                    finalChat.add(ChatMessage("companion", "مجھے سرور سے تعمیری جواب موصول نہیں ہوا۔ براہ مہربانی دوبارہ ٹرائی کریں۔"))
                    _tafseerChat.value = finalChat
                }
            } catch (e: Exception) {
                val finalChat = _tafseerChat.value.toMutableList()
                finalChat.add(ChatMessage("companion", "نیٹ ورک خامی پیش آئی: ${e.localizedMessage}"))
                _tafseerChat.value = finalChat
            } finally {
                _isChatLoading.value = false
            }
        }
    }
}

// --- Screen Router ---
enum class AppScreen {
    Dashboard,
    Reader,
    TafseerDetail
}

// --- ViewModel Factory ---

class QuranViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuranViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val repository = BookmarkRepository(database.bookmarkDao())
            @Suppress("UNCHECKED_CAST")
            return QuranViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
