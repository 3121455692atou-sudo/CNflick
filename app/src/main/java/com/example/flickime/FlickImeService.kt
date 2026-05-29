package com.example.flickime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.flickime.data.KeyMapStore
import com.example.flickime.engine.JapaneseEngine
import com.example.flickime.engine.LexiconManager
import com.example.flickime.engine.PinyinEngine
import com.example.flickime.engine.ShapeCodeManager
import com.example.flickime.engine.ZhuyinConverter
import com.example.flickime.model.FlickDirection
import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.InputLanguage
import com.example.flickime.model.KeyZone
import com.example.flickime.theme.FontManager
import com.example.flickime.theme.KeyboardTheme
import com.example.flickime.theme.ThemeManager
import com.example.flickime.theme.UiPrefs
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class FlickImeService : InputMethodService() {
    companion object {
        private const val SETTINGS_PREFS = "flick_settings"
        private const val VOICE_SAMPLE_RATE = 16_000
    }
    private data class DirectionalSpec(
        val center: String,
        val left: String,
        val up: String,
        val right: String,
        val down: String,
        val upLeft: String = "",
        val upRight: String = "",
        val downLeft: String = "",
        val downRight: String = ""
    )
    private data class CandidateEntry(
        val text: String,
        val consumeSyllables: Int,
        val recordChoice: Boolean = true,
        val replaceBeforeCursor: Int = 0
    )
    private data class InitialContextQuery(
        val typedLength: Int,
        val tokens: List<String>
    )
    private data class ModeSwitchEntry(
        val container: FrameLayout,
        val label: TextView,
        val target: Mode
    )

    private lateinit var pinyinEngine: PinyinEngine
    private lateinit var japaneseEngine: JapaneseEngine
    private val candidateExecutor = Executors.newSingleThreadExecutor()
    private val warmupExecutor = Executors.newSingleThreadExecutor()
    private val voiceExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val candidateToken = AtomicInteger(0)
    private lateinit var keyboardTheme: KeyboardTheme
    private var keyboardBgImage: android.graphics.Bitmap? = null
    private var keyBgImage: android.graphics.Bitmap? = null
    private var activeTypeface: Typeface = Typeface.DEFAULT
    private var centerTextSp: Float = 18f
    private var sideTextSp: Float = 10f
    private var keyTextAlpha: Float = 1f
    private var keyImageAlpha: Float = 0.9f
    private var keyBgAlpha: Float = 0.85f
    private var customFontColor: Int? = null
    private var keySizeScale: Float = 1f
    private var keyGapDp: Float = 4f
    private var enableEightDirectionPinyinFlick: Boolean = false
    private var enableEightDirectionSymbolFlick: Boolean = true
    private var showCenterKeyText: Boolean = true
    private var showSideKeyText: Boolean = true
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var audioManager: AudioManager
    private var vibrator: Vibrator? = null
    private var soundPool: SoundPool? = null
    private var customSoundId: Int = 0
    private var senseVoiceRecognizer: OfflineRecognizer? = null
    private var senseVoiceLoading: Boolean = false
    private var voiceAudioRecord: AudioRecord? = null
    private var voiceAudioBytes: ByteArrayOutputStream? = null
    private var voiceRecordingThread: Thread? = null
    private var voiceListening: Boolean = false

    private var shengmuPart: String? = null
    private val composedSyllables = mutableListOf<String>()
    private val composedDisplaySyllables = mutableListOf<String>()
    private var composingText: String = ""
    private var allCandidates: List<CandidateEntry> = emptyList()
    private var composingSessionFullQuery: String = ""
    private var composingSessionCommittedText: String = ""

    private lateinit var composingView: TextView
    private lateinit var candidateStripContainer: LinearLayout
    private lateinit var candidateExpandButton: TextView
    private lateinit var candidateRow: LinearLayout
    private lateinit var candidateGrid: GridLayout

    private lateinit var keyboardContainer: FrameLayout
    private lateinit var flickPanel: View
    private lateinit var alphaPanel: View
    private lateinit var numPanel: View
    private lateinit var symbolPanel: View
    private lateinit var candidatePanel: View
    private lateinit var funcPanel: View
    private lateinit var clipboardPanel: View
    private lateinit var clipboardList: LinearLayout

    private lateinit var rootOverlay: FrameLayout
    private var inputRootView: FrameLayout? = null
    private var rootBgView: ImageView? = null
    private lateinit var hintCenter: TextView
    private lateinit var hintLeft: TextView
    private lateinit var hintUp: TextView
    private lateinit var hintRight: TextView
    private lateinit var hintDown: TextView
    private lateinit var hintUpLeft: TextView
    private lateinit var hintUpRight: TextView
    private lateinit var hintDownLeft: TextView
    private lateinit var hintDownRight: TextView

    private var colWidth = 0
    private var rowHeight = 0
    private var rowGap = 0
    private var candidateStripHeight = 0
    private var alphaCapsLock = false
    private val clipboardHistory = mutableListOf<String>()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureSystemClipboard() }
    private val modeSwitchViews = mutableListOf<ModeSwitchEntry>()
    private var currentInputLanguage: InputLanguage = InputLanguage.PINYIN
    private var enabledInputLanguages: List<InputLanguage> = listOf(
        InputLanguage.PINYIN,
        InputLanguage.ZHUYIN,
        InputLanguage.JAPANESE,
        InputLanguage.SHAPE
    )
    private val japaneseModifierTokens = setOf("゛゜小", "小゛゜", "ﾞﾟ小", "小ﾞﾟ")
    private val japaneseTransformCycles = listOf(
        listOf("あ", "ぁ"), listOf("い", "ぃ"), listOf("う", "ぅ", "ゔ"), listOf("え", "ぇ"), listOf("お", "ぉ"),
        listOf("や", "ゃ"), listOf("ゆ", "ゅ"), listOf("よ", "ょ"), listOf("わ", "ゎ"),
        listOf("か", "が"), listOf("き", "ぎ"), listOf("く", "ぐ"), listOf("け", "げ"), listOf("こ", "ご"),
        listOf("さ", "ざ"), listOf("し", "じ"), listOf("す", "ず"), listOf("せ", "ぜ"), listOf("そ", "ぞ"),
        listOf("た", "だ"), listOf("ち", "ぢ"), listOf("つ", "っ", "づ"), listOf("て", "で"), listOf("と", "ど"),
        listOf("は", "ば", "ぱ"), listOf("ひ", "び", "ぴ"), listOf("ふ", "ぶ", "ぷ"), listOf("へ", "べ", "ぺ"), listOf("ほ", "ぼ", "ぽ")
    )
    private val pinyinInitialsForBackspace = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f",
        "d", "t", "n", "l",
        "g", "k", "h",
        "j", "q", "x",
        "r", "z", "c", "s",
        "y", "w"
    )
    private val pinyinSeparatorCharsArray = charArrayOf('\'', '’', '‘', '＇', '`', '｀')
    private val zhuyinInitialsForBackspace = setOf(
        "ㄅ", "ㄆ", "ㄇ", "ㄈ",
        "ㄉ", "ㄊ", "ㄋ", "ㄌ",
        "ㄍ", "ㄎ", "ㄏ",
        "ㄐ", "ㄑ", "ㄒ",
        "ㄓ", "ㄔ", "ㄕ", "ㄖ",
        "ㄗ", "ㄘ", "ㄙ"
    )

    private enum class Mode { FLICK, ALPHA, NUM, SYMBOL, CANDIDATE, FUNC, CLIPBOARD }
    private enum class ActionKeyKind { BACKSPACE, VOICE, ENTER, FUNC }
    private var mode: Mode = Mode.FLICK
    private var rightActionKeyOrder: List<ActionKeyKind> = listOf(
        ActionKeyKind.BACKSPACE,
        ActionKeyKind.VOICE,
        ActionKeyKind.FUNC,
        ActionKeyKind.ENTER
    )

    override fun onCreate() {
        super.onCreate()
        pinyinEngine = PinyinEngine(this)
        japaneseEngine = JapaneseEngine(this)
        warmupExecutor.execute {
            runCatching { pinyinEngine.queryCandidates("ni", 1) }
            runCatching { LexiconManager.warmup(this) }
            runCatching { ShapeCodeManager.warmup(this) }
            runCatching { com.example.flickime.engine.JapaneseLexiconManager.warmup(this) }
        }
        keyboardTheme = ThemeManager.getCurrentTheme(this)
        reloadCustomUiSettings()
        loadLanguagePrefs()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
        loadClipboardHistory()
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        captureSystemClipboard()
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        soundPool?.release()
        soundPool = null
        stopSenseVoiceRecording(recognize = false)
        senseVoiceRecognizer?.release()
        senseVoiceRecognizer = null
        candidateExecutor.shutdownNow()
        warmupExecutor.shutdownNow()
        voiceExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetComposing()
    }

    override fun onCreateInputView(): View {
        initDimensions()
        modeSwitchViews.clear()

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(if (keyboardBgImage == null) colorKeyboardBackground() else Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }
        if (keyboardBgImage != null) {
            rootBgView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(keyboardBgImage)
            }
            root.addView(rootBgView)
        } else {
            rootBgView = null
        }

        inputRootView = root

        val contentLeftPadding = dp(4)
        val contentTopPadding = dp(4)
        val contentRightPadding = dp(4)
        val contentBottomPadding = dp(6)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentLeftPadding, contentTopPadding, contentRightPadding, contentBottomPadding)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            val navBottom = navigationBarBottomInset(insets)
            content.setPadding(
                contentLeftPadding,
                contentTopPadding,
                contentRightPadding,
                contentBottomPadding + navBottom
            )
            insets
        }
        root.post { root.requestApplyInsets() }

        content.addView(buildCandidateStrip())

        keyboardContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardHeight())
            clipChildren = false
            clipToPadding = false
        }

        flickPanel = buildFlickPanel().apply { visibility = View.VISIBLE }
        alphaPanel = buildAlphaPanel().apply { visibility = View.GONE }
        numPanel = buildNumPanel().apply { visibility = View.GONE }
        symbolPanel = buildSymbolPanel().apply { visibility = View.GONE }
        candidatePanel = buildCandidatePanel().apply { visibility = View.GONE }
        funcPanel = buildFunctionPanel().apply { visibility = View.GONE }
        clipboardPanel = buildClipboardPanel().apply { visibility = View.GONE }

        keyboardContainer.addView(flickPanel)
        keyboardContainer.addView(alphaPanel)
        keyboardContainer.addView(numPanel)
        keyboardContainer.addView(symbolPanel)
        keyboardContainer.addView(candidatePanel)
        keyboardContainer.addView(funcPanel)
        keyboardContainer.addView(clipboardPanel)

        content.addView(keyboardContainer)

        rootOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clipChildren = false
            clipToPadding = false
            isClickable = false
            isFocusable = false
            elevation = dp(40).toFloat()
        }
        hintCenter = makeHintBubble("", true)
        hintLeft = makeHintBubble("", false)
        hintUp = makeHintBubble("", false)
        hintRight = makeHintBubble("", false)
        hintDown = makeHintBubble("", false)
        hintUpLeft = makeHintBubble("", false)
        hintUpRight = makeHintBubble("", false)
        hintDownLeft = makeHintBubble("", false)
        hintDownRight = makeHintBubble("", false)
        rootOverlay.addView(hintCenter)
        rootOverlay.addView(hintLeft)
        rootOverlay.addView(hintUp)
        rootOverlay.addView(hintRight)
        rootOverlay.addView(hintDown)
        rootOverlay.addView(hintUpLeft)
        rootOverlay.addView(hintUpRight)
        rootOverlay.addView(hintDownLeft)
        rootOverlay.addView(hintDownRight)
        hideHintOverlay()

        root.addView(content)
        root.addView(rootOverlay)

        refreshCandidateViews()
        refreshModeSwitchStyles()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardTheme = ThemeManager.getCurrentTheme(this)
        reloadCustomUiSettings()
        loadLanguagePrefs()
        updateRootBackground()
        refreshCandidateStripStyle()
        rebuildPanelsFromSettings()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        keyboardTheme = ThemeManager.getCurrentTheme(this)
        reloadCustomUiSettings()
        loadLanguagePrefs()
        updateRootBackground()
        refreshCandidateStripStyle()
        rebuildPanelsFromSettings()
        refreshCandidateViews()
    }

    private fun updateRootBackground() {
        val inputRoot = inputRootView ?: return
        if (keyboardBgImage == null) {
            rootBgView?.let { inputRoot.removeView(it) }
            rootBgView = null
            inputRoot.setBackgroundColor(colorKeyboardBackground())
            return
        }
        inputRoot.setBackgroundColor(Color.TRANSPARENT)
        val bg = rootBgView ?: ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            inputRoot.addView(this, 0)
        }
        bg.setImageBitmap(keyboardBgImage)
        rootBgView = bg
    }

    private fun initDimensions() {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val outerPadding = dp(8)
        rowGap = dpf(keyGapDp.coerceIn(0f, 14f))
        candidateStripHeight = if (isLandscape) dp(44) else dp(56)

        val usable = screenWidth - outerPadding
        colWidth = ((usable - rowGap * 4) / 5f).toInt().coerceAtLeast(dp(54))

        val centerSquare = colWidth * 3 + rowGap * 2
        val widthBasedRow = ((centerSquare - rowGap * 3) / 4f).toInt()
        val maxKeyboardRatio = if (isLandscape) 0.42f else 0.60f
        val maxKeyboardHeight = (screenHeight * maxKeyboardRatio).toInt()
        val heightBasedRow = ((maxKeyboardHeight - rowGap * 4 - dp(6)) / 5f).toInt()
        val minRow = if (isLandscape) dp(32) else dp(46)
        rowHeight = (minOf(widthBasedRow, heightBasedRow) * keySizeScale).toInt().coerceAtLeast(minRow)
    }

    private fun keyboardHeight(): Int = rowHeight * 5 + rowGap * 4 + dp(6)

    private fun buildCandidateStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolvedCandidatePanelBackground())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, candidateStripHeight)
        }
        candidateStripContainer = strip

        composingView = TextView(this).apply {
            textSize = 12f
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorHintText())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = languagePlaceholder()
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        scroller.addView(candidateRow)

        val expand = TextView(this).apply {
            text = "˅"
            textSize = 20f
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener {
                if (allCandidates.isNotEmpty()) {
                    refreshCandidateGrid()
                    switchMode(Mode.CANDIDATE)
                }
            }
        }
        candidateExpandButton = expand

        bottom.addView(scroller)
        bottom.addView(expand)

        strip.addView(composingView)
        strip.addView(bottom)
        return strip
    }

    private fun refreshCandidateStripStyle() {
        if (::candidateStripContainer.isInitialized) {
            candidateStripContainer.setBackgroundColor(resolvedCandidatePanelBackground())
        }
        if (::composingView.isInitialized) {
            composingView.setTypeface(activeTypeface, Typeface.NORMAL)
        }
        if (::candidateExpandButton.isInitialized) {
            candidateExpandButton.setTypeface(activeTypeface, Typeface.NORMAL)
            candidateExpandButton.setTextColor(colorSubKeyText())
        }
        if (::composingView.isInitialized && ::candidateRow.isInitialized) {
            refreshCandidateViews()
        }
    }

    private fun buildFlickPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(resolvedPanelBackground())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val keys = loadCurrentLanguageKeys()
        val languageModeLabel = currentLanguageModeLabel()
        val actionKeys = buildRightActionKeys()
        val rows = listOf(
            listOf(
                modeSwitchKey("☆123", Mode.NUM),
                pinyinFlickKey(keys[0]), pinyinFlickKey(keys[1]), pinyinFlickKey(keys[2]),
                actionKeys[0]
            ),
            listOf(
                modeSwitchKey("ABC", Mode.ALPHA),
                pinyinFlickKey(keys[3]), pinyinFlickKey(keys[4]), pinyinFlickKey(keys[5]),
                actionKeys[1]
            ),
            listOf(
                modeSwitchKey(languageModeLabel, Mode.FLICK),
                pinyinFlickKey(keys[6]), pinyinFlickKey(keys[7]), pinyinFlickKey(keys[8]),
                actionKeys[2]
            ),
            listOf(
                modeSwitchKey("符号", Mode.SYMBOL),
                pinyinFlickKey(keys[9]), pinyinFlickKey(keys[10]), pinyinFlickKey(keys[11]),
                actionKeys[3]
            )
        )

        rows.forEachIndexed { index, rowViews -> panel.addView(makeFixedRow(rowViews, index != rows.lastIndex)) }
        panel.addView(buildBottomUtilityBar())
        return panel
    }

    private fun buildAlphaPanel(): View {
        val specs = KeyMapStore.loadAlphaKeys(this).map {
            DirectionalSpec(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight)
        }
        val fallback = listOf(
            DirectionalSpec("b", "a", "", "c", ""),
            DirectionalSpec("e", "d", "", "f", ""),
            DirectionalSpec("h", "g", "", "i", ""),
            DirectionalSpec("k", "j", "", "l", ""),
            DirectionalSpec("n", "m", "", "o", ""),
            DirectionalSpec("q", "p", "", "r", ""),
            DirectionalSpec("t", "s", "", "u", ""),
            DirectionalSpec("w", "v", "", "x", ""),
            DirectionalSpec("z", "y", "", "'", ""),
            DirectionalSpec(",", ";", "", ".", ""),
            DirectionalSpec(" ", "", "", "", ""),
            DirectionalSpec("⇧", "?", "'", "!", "\"")
        )
        val alpha = if (specs.size >= 12) specs else fallback

        val actionKeys = buildRightActionKeys()
        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), alphaFlickKey(alpha[0]), alphaFlickKey(alpha[1]), alphaFlickKey(alpha[2]), actionKeys[0]),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), alphaFlickKey(alpha[3]), alphaFlickKey(alpha[4]), alphaFlickKey(alpha[5]), actionKeys[1]),
            listOf(modeSwitchKey(currentLanguageModeLabel(), Mode.FLICK), alphaFlickKey(alpha[6]), alphaFlickKey(alpha[7]), alphaFlickKey(alpha[8]), actionKeys[2]),
            listOf(
                modeSwitchKey("符号", Mode.SYMBOL),
                alphaFlickKey(alpha[9]),
                alphaFlickKey(alpha[10]),
                alphaFlickKey(alpha[11]),
                actionKeys[3]
            )
        )
        return panelFromRows(rows)
    }

    private fun buildNumPanel(): View {
        val specs = KeyMapStore.loadNumKeys(this).map {
            DirectionalSpec(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight)
        }
        val actionKeys = buildRightActionKeys()
        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), symbolFlickKey(specs[0]), symbolFlickKey(specs[1]), symbolFlickKey(specs[2]), actionKeys[0]),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), symbolFlickKey(specs[3]), symbolFlickKey(specs[4]), symbolFlickKey(specs[5]), actionKeys[1]),
            listOf(modeSwitchKey(currentLanguageModeLabel(), Mode.FLICK), symbolFlickKey(specs[6]), symbolFlickKey(specs[7]), symbolFlickKey(specs[8]), actionKeys[2]),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), symbolFlickKey(specs[9]), symbolFlickKey(specs[10]), symbolFlickKey(specs[11]), actionKeys[3])
        )
        return panelFromRows(rows)
    }

    private fun buildSymbolPanel(): View {
        val specs = KeyMapStore.loadSymbolKeys(this).map {
            DirectionalSpec(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight)
        }

        val actionKeys = buildRightActionKeys()
        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), symbolFlickKey(specs[0]), symbolFlickKey(specs[1]), symbolFlickKey(specs[2]), actionKeys[0]),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), symbolFlickKey(specs[3]), symbolFlickKey(specs[4]), symbolFlickKey(specs[5]), actionKeys[1]),
            listOf(modeSwitchKey(currentLanguageModeLabel(), Mode.FLICK), symbolFlickKey(specs[6]), symbolFlickKey(specs[7]), symbolFlickKey(specs[8]), actionKeys[2]),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), symbolFlickKey(specs[9]), symbolFlickKey(specs[10]), symbolFlickKey(specs[11]), actionKeys[3])
        )
        return panelFromRows(rows)
    }

    private fun buildFunctionPanel(): View {
        val actionKeys = buildRightActionKeys()
        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), controlKey("复制") { copySelection() }, controlKey("↑") { sendArrow(KeyEvent.KEYCODE_DPAD_UP) }, controlKey("粘贴") { pasteClipboard() }, actionKeys[0]),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), controlKey("←") { sendArrow(KeyEvent.KEYCODE_DPAD_LEFT) }, controlKey("语音") { startVoiceInput() }, controlKey("→") { sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT) }, actionKeys[1]),
            listOf(modeSwitchKey(currentLanguageModeLabel(), Mode.FLICK), controlKey("剪切") { cutSelection() }, controlKey("↓") { sendArrow(KeyEvent.KEYCODE_DPAD_DOWN) }, controlKey("全选") { selectAll() }, actionKeys[2]),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), controlKey("HOME") { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }, controlKey("剪贴板") { showClipboardPanel() }, controlKey("END") { sendKey(KeyEvent.KEYCODE_MOVE_END) }, actionKeys[3])
        )
        return panelFromRows(rows)
    }

    private fun buildRightActionKeys(): List<View> {
        return rightActionKeyOrder.map { key ->
            when (key) {
                ActionKeyKind.BACKSPACE -> backspaceKey()
                ActionKeyKind.VOICE -> voiceKey()
                ActionKeyKind.ENTER -> primaryKey("回车") { sendEnter() }
                ActionKeyKind.FUNC -> modeSwitchKey("功能", Mode.FUNC)
            }
        }
    }

    private fun buildClipboardPanel(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolvedPanelBackground())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }
        val title = TextView(this).apply {
            text = "剪贴板历史"
            textSize = 17f
            setTypeface(activeTypeface, Typeface.BOLD)
            setTextColor(colorKeyText())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val clear = controlKey("清空") {
            clipboardHistory.clear()
            saveClipboardHistory()
            refreshClipboardPanel()
        }
        clear.layoutParams = LinearLayout.LayoutParams(dp(72), dp(40))
        val close = controlKey("返回") { switchMode(Mode.FUNC) }
        close.layoutParams = LinearLayout.LayoutParams(dp(72), dp(40)).apply { marginStart = dp(6) }
        header.addView(title)
        header.addView(clear)
        header.addView(close)

        clipboardList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(clipboardList)
        }

        container.addView(header)
        container.addView(scroll)
        refreshClipboardPanel()
        return container
    }

    private fun buildCandidatePanel(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resolvedCandidatePanelBackground())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }

        val title = TextView(this).apply {
            text = "全部候选"
            textSize = 17f
            setTypeface(activeTypeface, Typeface.BOLD)
            setTextColor(colorKeyText())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val close = controlKey("返回", transparentWhenImageBg = true) { switchMode(Mode.FLICK) }
        close.layoutParams = LinearLayout.LayoutParams(dp(88), dp(40))

        header.addView(title)
        header.addView(close)

        candidateGrid = GridLayout(this).apply {
            columnCount = 4
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, dp(8), 0, 0)
            useDefaultMargins = true
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        val gridScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(candidateGrid)
        }

        container.addView(header)
        container.addView(gridScroll)
        return container
    }

    private fun panelFromRows(rows: List<List<View>>): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(resolvedPanelBackground())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rows.forEachIndexed { index, rowViews -> panel.addView(makeFixedRow(rowViews, index != rows.lastIndex)) }
        panel.addView(buildBottomUtilityBar())
        return panel
    }

    private fun rebuildPanelsFromSettings() {
        if (!::keyboardContainer.isInitialized) return
        modeSwitchViews.clear()
        val currentMode = mode

        val newFlick = buildFlickPanel()
        val newAlpha = buildAlphaPanel()
        val newNum = buildNumPanel()
        val newSymbol = buildSymbolPanel()
        val newCandidate = buildCandidatePanel()
        val newFunc = buildFunctionPanel()
        val newClipboard = buildClipboardPanel()

        keyboardContainer.removeAllViews()
        flickPanel = newFlick
        alphaPanel = newAlpha
        numPanel = newNum
        symbolPanel = newSymbol
        candidatePanel = newCandidate
        funcPanel = newFunc
        clipboardPanel = newClipboard

        keyboardContainer.addView(flickPanel)
        keyboardContainer.addView(alphaPanel)
        keyboardContainer.addView(numPanel)
        keyboardContainer.addView(symbolPanel)
        keyboardContainer.addView(candidatePanel)
        keyboardContainer.addView(funcPanel)
        keyboardContainer.addView(clipboardPanel)

        mode = currentMode
        switchMode(currentMode)
    }

    private fun buildBottomUtilityBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
                topMargin = rowGap
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val globeMode = UiPrefs.getGlobeKeyMode(this)
        val globe = when (globeMode) {
            UiPrefs.GLOBE_KEY_MODE_HIDDEN -> spacerCell()
            UiPrefs.GLOBE_KEY_MODE_DISABLED -> iconAction("🌐", enabled = false) {}
            else -> {
                val canSwitchLanguage = UiPrefs.getGlobeLanguageSwitchEnabled(this) && enabledInputLanguages.size > 1
                if (canSwitchLanguage) iconAction("🌐") { switchLanguageQuick() }
                else iconAction("🌐", enabled = false) {}
            }
        }
        val collapse = iconAction("⌄") { hideImeWindow() }
        val settings = iconAction("⚙") { openImeSettings() }
        val spacer1 = spacerCell()
        val spacer2 = spacerCell()
        val cells = listOf(globe, spacer1, collapse, spacer2, settings)
        cells.forEachIndexed { index, cell ->
            row.addView(cell, LinearLayout.LayoutParams(0, rowHeight, 1f).apply {
                if (index != cells.lastIndex) marginEnd = rowGap
            })
        }
        return row
    }

    private fun spacerCell(): View {
        return View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun iconAction(icon: String, enabled: Boolean = true, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = icon
            textSize = 27f
            setTypeface(activeTypeface, Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = null
            setTextColor(colorKeyText())
            alpha = if (enabled) 1f else 0.4f
            isEnabled = enabled
            isClickable = enabled
            if (enabled) {
                setOnClickListener { playKeyClick(); onClick() }
            } else {
                setOnClickListener(null)
            }
        }
    }

    private fun makeFixedRow(cells: List<View>, addBottomGap: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
                if (addBottomGap) bottomMargin = rowGap
            }
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        cells.forEachIndexed { index, cell ->
            row.addView(cell, LinearLayout.LayoutParams(0, rowHeight, 1f).apply {
                if (index != cells.lastIndex) marginEnd = rowGap
            })
        }
        return row
    }

    private fun pinyinFlickKey(spec: FlickKeySpec): View {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val centerSize = if (isLandscape) centerTextSp - 2f else centerTextSp
        val sideSize = if (isLandscape) sideTextSp - 1f else sideTextSp
        val verticalEdgeMargin = if (isLandscape) dp(1) else dp(3)
        val allowDiagonal = enableEightDirectionPinyinFlick
        val visual = DirectionalSpec(
            spec.center.lowercase(),
            spec.left.lowercase(),
            spec.up.lowercase(),
            spec.right.lowercase(),
            spec.down.lowercase(),
            spec.upLeft.lowercase(),
            spec.upRight.lowercase(),
            spec.downLeft.lowercase(),
            spec.downRight.lowercase()
        )
        val key = FrameLayout(this).apply {
            background = keyBackground(colorKeyBackground(), colorKeyBorder())
            isClickable = true
        }
        addDirectionalLabels(key, visual, centerSize, sideSize, verticalEdgeMargin, allowDiagonal)

        var startX = 0f
        var startY = 0f
        var direction = FlickDirection.Center
        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    direction = FlickDirection.Center
                    showHintOverlay(visual, v, direction, allowVertical = true, allowDiagonal = allowDiagonal)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, allowVertical = true, allowDiagonal = allowDiagonal)
                    showHintOverlay(visual, v, direction, allowVertical = true, allowDiagonal = allowDiagonal)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = textByDirection(visual, direction)
                    if (out.isEmpty()) return@setOnTouchListener true
                    playKeyClick()
                    onPinyinFlick(spec.zone, out)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideHintOverlay()
                    true
                }
                else -> false
            }
        }
        return key
    }

    private fun symbolFlickKey(spec: DirectionalSpec): View {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val centerSize = if (isLandscape) centerTextSp - 2f else centerTextSp
        val sideSize = if (isLandscape) sideTextSp - 1f else sideTextSp
        val verticalEdgeMargin = if (isLandscape) dp(1) else dp(3)
        val allowDiagonal = enableEightDirectionSymbolFlick
        val key = FrameLayout(this).apply {
            background = keyBackground(colorKeyBackground(), colorKeyBorder())
            isClickable = true
        }
        addDirectionalLabels(key, spec, centerSize, sideSize, verticalEdgeMargin, allowDiagonal)

        var startX = 0f
        var startY = 0f
        var direction = FlickDirection.Center
        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    direction = FlickDirection.Center
                    showHintOverlay(spec, v, direction, allowVertical = true, allowDiagonal = allowDiagonal)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, allowVertical = true, allowDiagonal = allowDiagonal)
                    showHintOverlay(spec, v, direction, allowVertical = true, allowDiagonal = allowDiagonal)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = textByDirection(spec, direction)
                    if (out.isEmpty()) return@setOnTouchListener true
                    playKeyClick()
                    commitDirectText(out)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideHintOverlay()
                    true
                }
                else -> false
            }
        }
        return key
    }

    private fun textFlickKey(spec: DirectionalSpec): View = directionalKey(spec) { commitDirectText(it) }

    private fun alphaFlickKey(spec: DirectionalSpec): View {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val centerSize = if (isLandscape) centerTextSp - 2f else centerTextSp
        val sideSize = if (isLandscape) sideTextSp - 1f else sideTextSp
        val visual = DirectionalSpec(
            center = displayLabel(spec.center),
            left = displayLabel(spec.left),
            up = displayLabel(spec.up),
            right = displayLabel(spec.right),
            down = displayLabel(spec.down),
            upLeft = displayLabel(spec.upLeft),
            upRight = displayLabel(spec.upRight),
            downLeft = displayLabel(spec.downLeft),
            downRight = displayLabel(spec.downRight)
        )
        val key = FrameLayout(this).apply {
            background = keyBackground(colorKeyBackground(), colorKeyBorder())
            isClickable = true
        }
        addDirectionalLabels(key, visual, centerSize, sideSize, verticalEdgeMargin = dp(2), allowDiagonal = true)

        var startX = 0f
        var startY = 0f
        var direction = FlickDirection.Center
        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    direction = FlickDirection.Center
                    showHintOverlay(visual, v, direction, allowVertical = true, allowDiagonal = true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, allowVertical = true, allowDiagonal = true)
                    showHintOverlay(visual, v, direction, allowVertical = true, allowDiagonal = true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = when (direction) {
                        FlickDirection.Left -> spec.left
                        FlickDirection.Up -> spec.up
                        FlickDirection.Right -> spec.right
                        FlickDirection.Down -> spec.down
                        FlickDirection.UpLeft -> spec.upLeft
                        FlickDirection.UpRight -> spec.upRight
                        FlickDirection.DownLeft -> spec.downLeft
                        FlickDirection.DownRight -> spec.downRight
                        else -> spec.center
                    }
                    if (out.isEmpty()) return@setOnTouchListener true
                    playKeyClick()
                    commitAlphaChar(out)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideHintOverlay()
                    true
                }
                else -> false
            }
        }
        return key
    }

    private fun directionalKey(
        spec: DirectionalSpec,
        allowVertical: Boolean = true,
        allowDiagonal: Boolean = false,
        keyLabel: String = spec.center,
        commit: (String) -> Unit
    ): View {
        val (key, _) = centeredLabelKey(
            label = keyLabel,
            textSizeSp = centerTextSp,
            textStyle = Typeface.NORMAL,
            textColor = colorKeyText()
        )
        key.background = keyBackground(colorKeyBackground(), colorKeyBorder())

        var startX = 0f
        var startY = 0f
        var direction = FlickDirection.Center

        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    direction = FlickDirection.Center
                    showHintOverlay(spec, v, direction, allowVertical, allowDiagonal)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, allowVertical, allowDiagonal)
                    showHintOverlay(spec, v, direction, allowVertical, allowDiagonal)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = textByDirection(spec, direction)
                    if (out.isEmpty()) return@setOnTouchListener true
                    playKeyClick()
                    commit(out)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideHintOverlay()
                    true
                }
                else -> false
            }
        }
        return key
    }

    private fun addDirectionalLabels(
        key: FrameLayout,
        spec: DirectionalSpec,
        centerSize: Float,
        sideSize: Float,
        verticalEdgeMargin: Int,
        allowDiagonal: Boolean
    ) {
        if (showCenterKeyText) {
            key.addView(TextView(this).apply {
                text = spec.center
                textSize = centerSize
                includeFontPadding = false
                setTypeface(activeTypeface, Typeface.BOLD)
                setTextColor(colorKeyText())
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            })
        }
        if (!showSideKeyText) return

        key.addView(TextView(this).apply {
            text = spec.left
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply { marginStart = dp(4) }
        })
        key.addView(TextView(this).apply {
            text = spec.up
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = verticalEdgeMargin }
        })
        key.addView(TextView(this).apply {
            text = spec.right
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { marginEnd = dp(4) }
        })
        key.addView(TextView(this).apply {
            text = spec.down
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = verticalEdgeMargin }
        })

        if (!allowDiagonal) return

        val diagonalSize = (sideSize - 1f).coerceAtLeast(8f)
        if (spec.upLeft.isNotBlank()) {
            key.addView(TextView(this).apply {
                text = spec.upLeft
                textSize = diagonalSize
                includeFontPadding = false
                setTypeface(activeTypeface, Typeface.NORMAL)
                setTextColor(colorSubKeyText())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    topMargin = verticalEdgeMargin
                    marginStart = dp(4)
                }
            })
        }
        if (spec.upRight.isNotBlank()) {
            key.addView(TextView(this).apply {
                text = spec.upRight
                textSize = diagonalSize
                includeFontPadding = false
                setTypeface(activeTypeface, Typeface.NORMAL)
                setTextColor(colorSubKeyText())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = verticalEdgeMargin
                    marginEnd = dp(4)
                }
            })
        }
        if (spec.downLeft.isNotBlank()) {
            key.addView(TextView(this).apply {
                text = spec.downLeft
                textSize = diagonalSize
                includeFontPadding = false
                setTypeface(activeTypeface, Typeface.NORMAL)
                setTextColor(colorSubKeyText())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    bottomMargin = verticalEdgeMargin
                    marginStart = dp(4)
                }
            })
        }
        if (spec.downRight.isNotBlank()) {
            key.addView(TextView(this).apply {
                text = spec.downRight
                textSize = diagonalSize
                includeFontPadding = false
                setTypeface(activeTypeface, Typeface.NORMAL)
                setTextColor(colorSubKeyText())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    bottomMargin = verticalEdgeMargin
                    marginEnd = dp(4)
                }
            })
        }
    }

    private fun centeredLabelKey(
        label: String,
        textSizeSp: Float,
        textStyle: Int,
        textColor: Int
    ): Pair<FrameLayout, TextView> {
        val container = FrameLayout(this).apply {
            isClickable = true
        }
        val textView = TextView(this).apply {
            text = displayLabel(label)
            gravity = Gravity.CENTER
            textSize = textSizeSp
            setTypeface(activeTypeface, textStyle)
            includeFontPadding = false
            setTextColor(textColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        container.addView(textView)
        return container to textView
    }

    private fun controlKey(label: String, transparentWhenImageBg: Boolean = false, onClick: () -> Unit): View {
        val (key, _) = centeredLabelKey(label, 14f, Typeface.NORMAL, colorKeyText())
        if (transparentWhenImageBg && hasImageBackgroundForUi()) {
            key.background = null
        } else {
            key.background = keyBackground(colorKeyBackground(), colorKeyBorder())
        }
        key.setOnClickListener { playKeyClick(); onClick() }
        return key
    }

    private fun spaceKey(): View {
        val (key, _) = centeredLabelKey("空格", 14f, Typeface.NORMAL, colorKeyText())
        key.background = keyBackground(colorKeyBackground(), colorKeyBorder())
        key.setOnClickListener { playKeyClick(); onSpacePressed() }
        return key
    }

    private fun voiceKey(): View = primaryKey("语音") { startVoiceInput() }

    private fun modeSwitchKey(label: String, target: Mode): View {
        val (key, textView) = centeredLabelKey(label, 14f, Typeface.NORMAL, colorKeyText())
        key.setOnClickListener { playKeyClick(); switchMode(target) }
        val entry = ModeSwitchEntry(key, textView, target)
        modeSwitchViews += entry
        applyModeSwitchStyle(entry, selected = mode == target)
        return key
    }

    private fun applyModeSwitchStyle(entry: ModeSwitchEntry, selected: Boolean) {
        if (selected) {
            entry.label.setTextColor(colorAccentKeyText())
            entry.container.background = keyBackground(colorAccentKeyBackground(), colorKeyBorder())
        } else {
            entry.label.setTextColor(colorKeyText())
            entry.container.background = keyBackground(colorKeyBackground(), colorKeyBorder())
        }
    }

    private fun refreshModeSwitchStyles() {
        val iterator = modeSwitchViews.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.container.parent == null) {
                iterator.remove()
            } else {
                applyModeSwitchStyle(entry, mode == entry.target)
            }
        }
    }

    private fun backspaceKey(): View {
        val (key, _) = centeredLabelKey("⌫", 14f, Typeface.NORMAL, colorKeyText())
        key.background = keyBackground(colorKeyBackground(), colorKeyBorder())

        var startY = 0f
        var longPressActive = false
        var clearTriggered = false
        val repeat = object : Runnable {
            override fun run() {
                if (!longPressActive || clearTriggered) return
                backspace()
                key.postDelayed(this, 70L)
            }
        }
        val beginLongPress = Runnable {
            longPressActive = true
            key.post(repeat)
        }

        key.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.y
                    longPressActive = false
                    clearTriggered = false
                    key.removeCallbacks(beginLongPress)
                    key.removeCallbacks(repeat)
                    key.postDelayed(beginLongPress, 300L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!clearTriggered && longPressActive && e.y - startY < -dp(28)) {
                        clearTriggered = true
                        key.removeCallbacks(repeat)
                        clearAllInput()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    key.removeCallbacks(beginLongPress)
                    key.removeCallbacks(repeat)
                    if (!longPressActive && !clearTriggered) {
                        playKeyClick()
                        backspace()
                    }
                    longPressActive = false
                    clearTriggered = false
                    true
                }
                else -> false
            }
        }
        return key
    }

    private fun primaryKey(label: String, onClick: () -> Unit): View {
        val (key, _) = centeredLabelKey(label, 16f, Typeface.BOLD, colorAccentKeyText())
        key.background = keyBackground(colorAccentKeyBackground(), colorKeyBorder())
        key.setOnClickListener { playKeyClick(); onClick() }
        return key
    }

    private fun inputKey(label: String): View {
        val (key, _) = centeredLabelKey(label, 16f, Typeface.NORMAL, colorKeyText())
        key.background = keyBackground(colorKeyBackground(), colorKeyBorder())
        key.setOnClickListener { playKeyClick(); commitDirectText(label) }
        return key
    }

    private fun keyBackground(fill: Int, stroke: Int): android.graphics.drawable.Drawable {
        val bitmap = keyBgImage
        val fillAlpha = if (bitmap == null) keyBgAlpha else (keyBgAlpha * 0.35f)
        val strokeAlpha = if (bitmap == null) keyBgAlpha else (keyBgAlpha * 0.55f)
        val fillLayer = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(withCustomAlpha(lighten(fill), fillAlpha), withCustomAlpha(fill, fillAlpha))
        }
        val strokeLayer = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), withCustomAlpha(stroke, strokeAlpha))
        }
        if (bitmap == null) return LayerDrawable(arrayOf(fillLayer, strokeLayer))
        val imageLayer = BitmapDrawable(resources, bitmap).apply {
            alpha = (keyImageAlpha.coerceIn(0f, 1f) * 255).toInt()
            gravity = Gravity.FILL
        }
        return LayerDrawable(arrayOf(fillLayer, imageLayer, strokeLayer))
    }

    private fun makeHintBubble(text: String, center: Boolean): TextView {
        val baseBg = if (center) colorAccentKeyBackground() else colorKeyText()
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (center) 19f else 17f
            setTypeface(activeTypeface, Typeface.BOLD)
            setTextColor(readableTextColor(baseBg))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(baseBg)
                setStroke(dp(2), withCustomAlpha(readableTextColor(baseBg), 0.7f))
            }
            layoutParams = FrameLayout.LayoutParams(dp(58), dp(44))
            elevation = dp(60).toFloat()
        }
    }

    private fun textByDirection(spec: DirectionalSpec, direction: FlickDirection): String {
        return when (direction) {
            FlickDirection.Center -> spec.center
            FlickDirection.Left -> spec.left
            FlickDirection.Up -> spec.up
            FlickDirection.Right -> spec.right
            FlickDirection.Down -> spec.down
            FlickDirection.UpLeft -> spec.upLeft
            FlickDirection.UpRight -> spec.upRight
            FlickDirection.DownLeft -> spec.downLeft
            FlickDirection.DownRight -> spec.downRight
        }
    }

    private fun showHintOverlay(
        spec: DirectionalSpec,
        key: View,
        direction: FlickDirection,
        allowVertical: Boolean,
        allowDiagonal: Boolean = false
    ) {
        if (!UiPrefs.getShowFlickHintOverlay(this)) {
            hideHintOverlay()
            return
        }

        val keyPos = IntArray(2)
        val rootPos = IntArray(2)
        key.getLocationOnScreen(keyPos)
        rootOverlay.getLocationOnScreen(rootPos)

        val cx = keyPos[0] - rootPos[0] + key.width / 2
        val cy = keyPos[1] - rootPos[1] + key.height / 2
        val dist = dp(58)
        val maxW = rootOverlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val maxH = rootOverlay.height.takeIf { it > 0 } ?: keyboardHeight()

        hintCenter.text = spec.center
        hintLeft.text = spec.left
        hintUp.text = if (allowVertical) spec.up else ""
        hintRight.text = spec.right
        hintDown.text = if (allowVertical) spec.down else ""
        hintUpLeft.text = if (allowVertical && allowDiagonal) spec.upLeft else ""
        hintUpRight.text = if (allowVertical && allowDiagonal) spec.upRight else ""
        hintDownLeft.text = if (allowVertical && allowDiagonal) spec.downLeft else ""
        hintDownRight.text = if (allowVertical && allowDiagonal) spec.downRight else ""

        placeHint(hintCenter, cx, cy, maxW, maxH)
        placeHint(hintLeft, cx - dist, cy, maxW, maxH)
        if (allowVertical) placeHint(hintUp, cx, cy - dist, maxW, maxH)
        placeHint(hintRight, cx + dist, cy, maxW, maxH)
        if (allowVertical) placeHint(hintDown, cx, cy + dist, maxW, maxH)
        if (allowVertical && allowDiagonal) {
            placeHint(hintUpLeft, cx - dist, cy - dist, maxW, maxH)
            placeHint(hintUpRight, cx + dist, cy - dist, maxW, maxH)
            placeHint(hintDownLeft, cx - dist, cy + dist, maxW, maxH)
            placeHint(hintDownRight, cx + dist, cy + dist, maxW, maxH)
        }

        highlightHint(hintLeft, direction == FlickDirection.Left)
        if (allowVertical) highlightHint(hintUp, direction == FlickDirection.Up)
        highlightHint(hintRight, direction == FlickDirection.Right)
        if (allowVertical) highlightHint(hintDown, direction == FlickDirection.Down)
        if (allowVertical && allowDiagonal) {
            highlightHint(hintUpLeft, direction == FlickDirection.UpLeft)
            highlightHint(hintUpRight, direction == FlickDirection.UpRight)
            highlightHint(hintDownLeft, direction == FlickDirection.DownLeft)
            highlightHint(hintDownRight, direction == FlickDirection.DownRight)
        }

        hintCenter.visibility = if (spec.center.isNotBlank()) View.VISIBLE else View.GONE
        hintLeft.visibility = if (spec.left.isNotBlank()) View.VISIBLE else View.GONE
        hintUp.visibility = if (allowVertical && spec.up.isNotBlank()) View.VISIBLE else View.GONE
        hintRight.visibility = if (spec.right.isNotBlank()) View.VISIBLE else View.GONE
        hintDown.visibility = if (allowVertical && spec.down.isNotBlank()) View.VISIBLE else View.GONE
        hintUpLeft.visibility = if (allowVertical && allowDiagonal && spec.upLeft.isNotBlank()) View.VISIBLE else View.GONE
        hintUpRight.visibility = if (allowVertical && allowDiagonal && spec.upRight.isNotBlank()) View.VISIBLE else View.GONE
        hintDownLeft.visibility = if (allowVertical && allowDiagonal && spec.downLeft.isNotBlank()) View.VISIBLE else View.GONE
        hintDownRight.visibility = if (allowVertical && allowDiagonal && spec.downRight.isNotBlank()) View.VISIBLE else View.GONE
        rootOverlay.bringToFront()
    }

    private fun placeHint(v: TextView, cx: Int, cy: Int, maxW: Int, maxH: Int) {
        val hw = dp(29)
        val hh = dp(22)
        val clampedCx = cx.coerceIn(hw + dp(2), maxW - hw - dp(2))
        val clampedCy = cy.coerceIn(hh + dp(2), maxH - hh - dp(2))
        val lp = v.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = clampedCx - hw
        lp.topMargin = clampedCy - hh
        v.layoutParams = lp
    }

    private fun highlightHint(v: TextView, selected: Boolean) {
        val color = if (selected) colorSelectedItemBackground() else colorKeyText()
        val bg = v.background as GradientDrawable
        bg.setColor(color)
        v.setTextColor(readableTextColor(color))
    }

    private fun hideHintOverlay() {
        hintCenter.visibility = View.GONE
        hintLeft.visibility = View.GONE
        hintUp.visibility = View.GONE
        hintRight.visibility = View.GONE
        hintDown.visibility = View.GONE
        hintUpLeft.visibility = View.GONE
        hintUpRight.visibility = View.GONE
        hintDownLeft.visibility = View.GONE
        hintDownRight.visibility = View.GONE
    }

    private fun detectDirection(
        dx: Float,
        dy: Float,
        allowVertical: Boolean,
        allowDiagonal: Boolean = false
    ): FlickDirection {
        val threshold = dp(14).toFloat()
        if (abs(dx) < threshold && abs(dy) < threshold) return FlickDirection.Center
        if (!allowVertical) {
            return if (dx > threshold) FlickDirection.Right else if (dx < -threshold) FlickDirection.Left else FlickDirection.Center
        }

        if (allowDiagonal && abs(dx) >= threshold && abs(dy) >= threshold) {
            val major = maxOf(abs(dx), abs(dy))
            val minor = minOf(abs(dx), abs(dy))
            if (minor / major >= 0.5f) {
                return when {
                    dx < 0 && dy < 0 -> FlickDirection.UpLeft
                    dx > 0 && dy < 0 -> FlickDirection.UpRight
                    dx < 0 && dy > 0 -> FlickDirection.DownLeft
                    else -> FlickDirection.DownRight
                }
            }
        }

        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) FlickDirection.Right else FlickDirection.Left
        } else {
            if (dy > 0) FlickDirection.Down else FlickDirection.Up
        }
    }

    private fun onPinyinFlick(zone: KeyZone, text: String) {
        if (text.isNotEmpty() && text.isBlank()) {
            onSpacePressed()
            return
        }
        if (isPunctuationToken(text)) {
            if (commitTextSafe(text)) resetComposing()
            return
        }

        when (currentInputLanguage) {
            InputLanguage.PINYIN -> {
                handlePinyinFlick(zone, text)
                requestCandidatesAsync()
                refreshCandidateViews()
            }
            InputLanguage.ZHUYIN -> {
                handleZhuyinFlick(zone, text)
                requestCandidatesAsync()
                refreshCandidateViews()
            }
            InputLanguage.JAPANESE -> {
                if (handleJapaneseFlick(text)) {
                    requestCandidatesAsync()
                    refreshCandidateViews()
                }
            }
            InputLanguage.SHAPE -> {
                handleShapeFlick(text)
                requestCandidatesAsync()
                refreshCandidateViews()
            }
        }
    }

    private fun handlePinyinFlick(zone: KeyZone, text: String) {
        if (isPinyinSeparatorToken(text)) {
            val pending = shengmuPart
            if (pending != null && isPinyinInitialToken(pending)) {
                composedSyllables += pending.lowercase(Locale.getDefault())
                composedDisplaySyllables += pending
                shengmuPart = null
            }
            composingText = buildComposingDisplay()
            return
        }
        // 部分音节（如 ü/v）被放到声母区时，按韵母逻辑处理，允许首音节直接输入。
        val actualZone = if (zone == KeyZone.Shengmu && isYunmuLikeToken(text)) KeyZone.Yunmu else zone
        when (actualZone) {
            KeyZone.Shengmu -> {
                val pending = shengmuPart
                if (pending != null && isPinyinInitialToken(pending) && isPinyinInitialToken(text)) {
                    composedSyllables += pending.lowercase(Locale.getDefault())
                    composedDisplaySyllables += pending
                }
                shengmuPart = text
            }
            KeyZone.Yunmu -> {
                val full = (shengmuPart ?: "") + text
                shengmuPart = null
                composedSyllables += full.lowercase()
                composedDisplaySyllables += full
            }
        }
        composingText = buildComposingDisplay()
    }

    private fun handleShapeFlick(text: String) {
        val normalized = text.lowercase(Locale.getDefault()).filter { it in 'a'..'z' || it == ';' || it == '\'' }
        if (normalized.isBlank()) {
            if (commitTextSafe(text)) resetComposing()
            return
        }
        shengmuPart = null
        normalized.forEach {
            composedSyllables += it.toString()
            composedDisplaySyllables += it.toString()
        }
        composingText = buildComposingDisplay()
    }

    private fun handleZhuyinFlick(zone: KeyZone, text: String) {
        val actualZone = when {
            zone == KeyZone.Shengmu && isZhuyinYunmuLikeToken(text) -> KeyZone.Yunmu
            zone == KeyZone.Yunmu && isZhuyinShengmuLikeToken(text) -> KeyZone.Shengmu
            else -> zone
        }
        when (actualZone) {
            KeyZone.Shengmu -> {
                shengmuPart = ZhuyinConverter.normalize(text)
            }
            KeyZone.Yunmu -> {
                val fullZhuyin = ZhuyinConverter.normalize((shengmuPart ?: "") + text)
                shengmuPart = null
                if (fullZhuyin.isNotBlank()) {
                    composedDisplaySyllables += fullZhuyin
                    composedSyllables += ZhuyinConverter.toPinyin(fullZhuyin).lowercase()
                }
            }
        }
        composingText = buildComposingDisplay()
    }

    private fun handleJapaneseFlick(text: String): Boolean {
        if (japaneseModifierTokens.contains(text)) {
            return applyJapaneseModifier()
        }
        shengmuPart = null
        val hira = com.example.flickime.engine.JapaneseLexiconManager.normalizeReading(text)
        if (hira.isBlank()) {
            return if (commitTextSafe(text)) {
                resetComposing()
                false
            } else {
                false
            }
        }
        composedSyllables += hira
        composedDisplaySyllables += hira
        composingText = buildComposingDisplay()
        return true
    }

    private fun isPunctuationToken(text: String): Boolean {
        return text in setOf("，", "。", "？", "！", "、", "！", ",", ".", "?", "!")
    }

    private fun isYunmuLikeToken(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.lowercase(Locale.getDefault())
            .replace("ü", "v")
        return t == "v" || t == "er" || t.firstOrNull() in listOf('a', 'e', 'i', 'o', 'u')
    }

    private fun isPinyinInitialToken(text: String): Boolean {
        val normalized = text.lowercase(Locale.getDefault()).replace("ü", "v")
        return pinyinInitialsForBackspace.contains(normalized)
    }

    private fun isPinyinSeparatorToken(text: String): Boolean {
        return text.length == 1 && isPinyinSeparatorChar(text[0])
    }

    private fun isPinyinSeparatorChar(c: Char): Boolean {
        return c == '\'' || c == '’' || c == '‘' || c == '＇' || c == '`' || c == '｀'
    }

    private fun isPinyinInitialSequence(parts: List<String>): Boolean {
        return parts.isNotEmpty() && parts.all { isPinyinInitialToken(it) }
    }

    private fun isZhuyinYunmuLikeToken(text: String): Boolean {
        val t = ZhuyinConverter.normalize(text)
        if (t.isBlank()) return false
        val finals = listOf("ㄚ", "ㄛ", "ㄜ", "ㄝ", "ㄞ", "ㄟ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ", "ㄦ", "ㄧ", "ㄨ", "ㄩ")
        return finals.any { t.startsWith(it) } || t.length >= 2
    }

    private fun isZhuyinShengmuLikeToken(text: String): Boolean {
        val t = ZhuyinConverter.normalize(text)
        if (t.isBlank()) return false
        val initials = setOf(
            "ㄅ", "ㄆ", "ㄇ", "ㄈ",
            "ㄉ", "ㄊ", "ㄋ", "ㄌ",
            "ㄍ", "ㄎ", "ㄏ",
            "ㄐ", "ㄑ", "ㄒ",
            "ㄓ", "ㄔ", "ㄕ", "ㄖ",
            "ㄗ", "ㄘ", "ㄙ"
        )
        return initials.contains(t)
    }

    private fun applyJapaneseModifier(): Boolean {
        if (composedDisplaySyllables.isEmpty()) return false
        val index = composedDisplaySyllables.lastIndex
        val transformed = transformJapaneseKana(composedDisplaySyllables[index])
        if (transformed == composedDisplaySyllables[index]) return false

        composedDisplaySyllables[index] = transformed
        val normalized = com.example.flickime.engine.JapaneseLexiconManager.normalizeReading(transformed)
        if (normalized.isNotBlank()) {
            composedSyllables[index] = normalized
        } else {
            composedSyllables[index] = transformed
        }
        composingText = buildComposingDisplay()
        return true
    }

    private fun transformJapaneseKana(value: String): String {
        if (value.length != 1) return value
        val ch = value
        japaneseTransformCycles.forEach { cycle ->
            val idx = cycle.indexOf(ch)
            if (idx >= 0) return cycle[(idx + 1) % cycle.size]
        }
        return value
    }

    private fun switchMode(target: Mode) {
        mode = target
        flickPanel.visibility = if (mode == Mode.FLICK) View.VISIBLE else View.GONE
        alphaPanel.visibility = if (mode == Mode.ALPHA) View.VISIBLE else View.GONE
        numPanel.visibility = if (mode == Mode.NUM) View.VISIBLE else View.GONE
        symbolPanel.visibility = if (mode == Mode.SYMBOL) View.VISIBLE else View.GONE
        candidatePanel.visibility = if (mode == Mode.CANDIDATE) View.VISIBLE else View.GONE
        funcPanel.visibility = if (mode == Mode.FUNC) View.VISIBLE else View.GONE
        clipboardPanel.visibility = if (mode == Mode.CLIPBOARD) View.VISIBLE else View.GONE
        refreshModeSwitchStyles()
    }

    private fun refreshCandidateViews() {
        if (!::composingView.isInitialized || !::candidateRow.isInitialized) return
        composingView.text = if (composingText.isBlank()) languagePlaceholder() else composingText
        composingView.setTextColor(if (composingText.isBlank()) colorHintText() else colorAccentKeyBackground())

        candidateRow.removeAllViews()
        val rowItemBg = candidateItemBackground()
        allCandidates.take(12).forEach { candidate ->
            candidateRow.addView(TextView(this).apply {
                text = candidate.text
                textSize = 18f
                setTypeface(activeTypeface, Typeface.NORMAL)
                setTextColor(colorKeyText())
                includeFontPadding = true
                gravity = Gravity.CENTER
                minHeight = dp(34)
                setPadding(dp(8), dp(3), dp(8), dp(4))
                if (rowItemBg != null) background = rowItemBg.constantState?.newDrawable()?.mutate()
                else setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    commitCandidate(candidate)
                    if (mode == Mode.CANDIDATE) switchMode(Mode.FLICK)
                }
            })
        }
    }

    private fun refreshCandidateGrid() {
        candidateGrid.removeAllViews()
        allCandidates.take(48).forEach { candidate ->
            val item = TextView(this).apply {
                text = candidate.text
                textSize = 24f
                setTypeface(activeTypeface, Typeface.NORMAL)
                includeFontPadding = true
                gravity = Gravity.CENTER
                minHeight = dp(52)
                setPadding(dp(4), dp(8), dp(4), dp(9))
                setTextColor(colorKeyText())
                val bg = candidateItemBackground()
                if (bg != null) background = bg else setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    commitCandidate(candidate)
                    switchMode(Mode.FLICK)
                }
            }
            candidateGrid.addView(item, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    private fun commitCandidate(candidate: CandidateEntry) {
        val current = composedSyllables.toList()
        val currentDisplay = composedDisplaySyllables.toList()
        if (current.isEmpty()) {
            if (candidate.replaceBeforeCursor > 0 &&
                !withInputConnection { it.deleteSurroundingText(candidate.replaceBeforeCursor, 0) }
            ) {
                return
            }
            if (commitTextSafe(candidate.text)) resetComposing()
            return
        }
        val consume = candidate.consumeSyllables.coerceIn(1, current.size)
        val remaining = current.drop(consume)
        val remainingDisplay = currentDisplay.drop(consume)
        val fullQuery = if (composingSessionFullQuery.isBlank()) current.joinToString("") else composingSessionFullQuery
        val fullText = composingSessionCommittedText + candidate.text

        if (!commitTextSafe(candidate.text)) return

        if (remaining.isEmpty()) {
            if (candidate.recordChoice && fullQuery.isNotBlank() && fullText.isNotBlank()) {
                candidateExecutor.execute {
                    runCatching {
                        if (currentInputLanguage == InputLanguage.JAPANESE) {
                            japaneseEngine.recordUserChoice(fullQuery, fullText)
                        } else if (currentInputLanguage == InputLanguage.PINYIN || currentInputLanguage == InputLanguage.ZHUYIN) {
                            pinyinEngine.recordUserChoice(fullQuery, fullText)
                        }
                    }
                }
            }
            resetComposing()
            refreshContextualCandidatesFromEditor()
            return
        }

        if (currentInputLanguage != InputLanguage.JAPANESE && currentInputLanguage != InputLanguage.SHAPE && composingSessionFullQuery.isBlank()) {
            composingSessionFullQuery = current.joinToString("")
        }
        if (currentInputLanguage != InputLanguage.JAPANESE && currentInputLanguage != InputLanguage.SHAPE) {
            composingSessionCommittedText += candidate.text
        }
        shengmuPart = null
        composedSyllables.clear()
        composedSyllables.addAll(remaining)
        composedDisplaySyllables.clear()
        composedDisplaySyllables.addAll(remainingDisplay)
        composingText = buildComposingDisplay()
        requestCandidatesAsync()
        refreshCandidateViews()
    }

    private fun backspace() {
        if (composingSessionCommittedText.isNotBlank()) {
            composingSessionCommittedText = ""
            composingSessionFullQuery = ""
        }
        if (shengmuPart != null) {
            shengmuPart = null
            composingText = buildComposingDisplay()
            requestCandidatesAsync()
            refreshCandidateViews()
            return
        }
        if (composedSyllables.isNotEmpty()) {
            val removedQuery = composedSyllables.removeAt(composedSyllables.lastIndex)
            val removedDisplay = if (composedDisplaySyllables.isNotEmpty()) {
                composedDisplaySyllables.removeAt(composedDisplaySyllables.lastIndex)
            } else {
                removedQuery
            }
            val restored = restorePendingInitialAfterBackspace(removedQuery, removedDisplay)
            shengmuPart = restored
            composingText = buildComposingDisplay()
            requestCandidatesAsync()
            refreshCandidateViews()
            return
        }
        if (withInputConnection { it.deleteSurroundingText(1, 0) }) {
            refreshContextualCandidatesFromEditor()
        }
    }

    private fun restorePendingInitialAfterBackspace(removedQuery: String, removedDisplay: String): String? {
        return when (currentInputLanguage) {
            InputLanguage.PINYIN -> {
                extractPinyinInitial(removedDisplay)
                    ?: extractPinyinInitial(removedQuery)
            }
            InputLanguage.ZHUYIN -> extractZhuyinInitial(removedDisplay)
            InputLanguage.JAPANESE -> null
            InputLanguage.SHAPE -> null
        }
    }

    private fun extractPinyinInitial(text: String): String? {
        if (text.isBlank()) return null
        val normalized = text.lowercase(Locale.getDefault())
            .replace("ü", "v")
            .replace("u:", "v")
        val initial = pinyinInitialsForBackspace.firstOrNull { normalized.startsWith(it) } ?: return null
        return initial.takeIf { normalized.length > it.length }
    }

    private fun extractZhuyinInitial(text: String): String? {
        val normalized = ZhuyinConverter.normalize(text)
        if (normalized.length <= 1) return null
        val initial = normalized.first().toString()
        return initial.takeIf { zhuyinInitialsForBackspace.contains(it) }
    }

    private fun clearAllInput() {
        resetComposing()
        withInputConnection { it.deleteSurroundingText(1000, 1000) }
    }

    private fun onSpacePressed() {
        if (allCandidates.isNotEmpty() && buildRawDisplayForCommit().isNotBlank()) {
            commitCandidate(allCandidates.first())
            return
        }
        val rawDisplay = buildRawDisplayForCommit()
        if (rawDisplay.isNotBlank()) {
            if (!commitTextSafe(rawDisplay)) return
            if (currentInputLanguage == InputLanguage.PINYIN) {
                val fullQuery = if (composingSessionFullQuery.isBlank()) buildRawQueryForCommit() else composingSessionFullQuery
                val fullText = composingSessionCommittedText + rawDisplay
                if (fullQuery.isNotBlank() && fullText.isNotBlank()) {
                    candidateExecutor.execute {
                        runCatching { pinyinEngine.recordUserChoice(fullQuery, fullText) }
                    }
                }
            }
            resetComposing()
            refreshContextualCandidatesFromEditor()
            return
        }
        commitDirectText(" ")
    }

    private fun sendEnter() {
        val rawDisplay = buildRawDisplayForCommit()
        if (rawDisplay.isNotBlank()) {
            if (!commitTextSafe(rawDisplay)) return
            if (currentInputLanguage == InputLanguage.PINYIN) {
                val fullQuery = if (composingSessionFullQuery.isBlank()) buildRawQueryForCommit() else composingSessionFullQuery
                val fullText = composingSessionCommittedText + rawDisplay
                if (fullQuery.isNotBlank() && fullText.isNotBlank()) {
                    candidateExecutor.execute {
                        runCatching { pinyinEngine.recordUserChoice(fullQuery, fullText) }
                    }
                }
            }
            resetComposing()
            refreshContextualCandidatesFromEditor()
            return
        }
        withInputConnection {
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        resetComposing()
    }

    private fun sendArrow(code: Int) {
        withInputConnection {
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun sendKey(code: Int) {
        withInputConnection {
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun copySelection() {
        withInputConnection { ic ->
            val selected = ic.getSelectedText(0)?.toString().orEmpty()
            if (selected.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("copied", selected))
            }
        }
    }

    private fun cutSelection() {
        withInputConnection { ic ->
            val selected = ic.getSelectedText(0)?.toString().orEmpty()
            if (selected.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("cut", selected))
                ic.commitText("", 1)
            }
        }
    }

    private fun pasteClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isNotEmpty()) commitDirectText(text)
    }

    private fun showClipboardPanel() {
        playKeyClick()
        captureSystemClipboard()
        refreshClipboardPanel()
        switchMode(Mode.CLIPBOARD)
    }

    private fun startVoiceInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "请先在 CNflick 设置里授予录音权限", Toast.LENGTH_SHORT).show()
            openImeSettings()
            return
        }
        if (voiceListening) {
            stopSenseVoiceRecording(recognize = true)
            return
        }
        if (senseVoiceRecognizer != null) {
            beginSenseVoiceRecording()
            return
        }
        if (senseVoiceLoading) {
            Toast.makeText(this, "SenseVoice 模型正在加载", Toast.LENGTH_SHORT).show()
            return
        }
        loadSenseVoiceAndStart()
    }

    private fun loadSenseVoiceAndStart() {
        senseVoiceLoading = true
        resetComposing()
        composingText = "SenseVoice 模型正在加载"
        refreshCandidateViews()
        voiceExecutor.execute {
            val result = runCatching { createSenseVoiceRecognizer() }
            mainHandler.post {
                senseVoiceLoading = false
                val recognizer = result.getOrNull()
                if (recognizer == null) {
                    resetComposing()
                    Toast.makeText(
                        this@FlickImeService,
                        "SenseVoice 模型加载失败：${result.exceptionOrNull()?.message.orEmpty()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@post
                }
                senseVoiceRecognizer = recognizer
                beginSenseVoiceRecording()
            }
        }
    }

    private fun createSenseVoiceRecognizer(): OfflineRecognizer {
        val senseVoice = OfflineSenseVoiceModelConfig().apply {
            model = "sense-voice/model.int8.onnx"
            language = "zh"
            useInverseTextNormalization = true
        }
        val modelConfig = OfflineModelConfig().apply {
            this.senseVoice = senseVoice
            tokens = "sense-voice/tokens.txt"
            numThreads = 2
            debug = false
            provider = "cpu"
        }
        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig(sampleRate = VOICE_SAMPLE_RATE, featureDim = 80, dither = 0.0f)
            this.modelConfig = modelConfig
        }
        return OfflineRecognizer(assets, config)
    }

    private fun beginSenseVoiceRecording() {
        resetComposing()
        val minBuffer = AudioRecord.getMinBufferSize(
            VOICE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Toast.makeText(this, "无法启动录音", Toast.LENGTH_SHORT).show()
            return
        }
        val record = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                VOICE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        } catch (t: Throwable) {
            Toast.makeText(this, "无法启动录音：${t.message.orEmpty()}", Toast.LENGTH_SHORT).show()
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Toast.makeText(this, "录音设备初始化失败", Toast.LENGTH_SHORT).show()
            return
        }

        val audioBytes = ByteArrayOutputStream()
        voiceAudioRecord = record
        voiceAudioBytes = audioBytes
        voiceListening = true
        composingText = "正在听写，点语音结束"
        refreshCandidateViews()

        try {
            record.startRecording()
        } catch (t: Throwable) {
            voiceListening = false
            voiceAudioRecord = null
            voiceAudioBytes = null
            record.release()
            resetComposing()
            Toast.makeText(this, "录音启动失败：${t.message.orEmpty()}", Toast.LENGTH_SHORT).show()
            return
        }

        val bufferSize = (minBuffer / 2).coerceAtLeast(1024)
        voiceRecordingThread = Thread {
            val buffer = ShortArray(bufferSize)
            while (voiceListening && !Thread.currentThread().isInterrupted) {
                val read = runCatching { record.read(buffer, 0, buffer.size) }.getOrDefault(0)
                if (read <= 0) continue
                synchronized(audioBytes) {
                    for (i in 0 until read) {
                        val sample = buffer[i].toInt()
                        audioBytes.write(sample and 0xff)
                        audioBytes.write((sample shr 8) and 0xff)
                    }
                }
            }
        }.apply {
            name = "CNflick-SenseVoiceRecorder"
            start()
        }
    }

    private fun stopSenseVoiceRecording(recognize: Boolean) {
        val record = voiceAudioRecord
        val bytes = voiceAudioBytes
        voiceListening = false
        voiceAudioRecord = null
        voiceAudioBytes = null
        voiceRecordingThread?.interrupt()
        voiceRecordingThread = null
        if (record != null) {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        if (!recognize) {
            resetComposing()
            return
        }
        val data = synchronized(bytes ?: ByteArrayOutputStream()) {
            bytes?.toByteArray() ?: ByteArray(0)
        }
        if (data.size < VOICE_SAMPLE_RATE / 2) {
            resetComposing()
            Toast.makeText(this, "录音太短", Toast.LENGTH_SHORT).show()
            return
        }
        composingText = "正在识别..."
        refreshCandidateViews()
        voiceExecutor.execute {
            val result = runCatching { recognizeSenseVoice(data) }
            mainHandler.post {
                resetComposing()
                val text = result.getOrDefault("")
                if (text.isNotBlank()) {
                    commitVoiceText(text)
                } else {
                    Toast.makeText(
                        this@FlickImeService,
                        "没有识别到内容${result.exceptionOrNull()?.message?.let { "：$it" }.orEmpty()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun recognizeSenseVoice(pcm16le: ByteArray): String {
        val recognizer = senseVoiceRecognizer ?: return ""
        val samples = FloatArray(pcm16le.size / 2)
        var byteIndex = 0
        for (i in samples.indices) {
            val lo = pcm16le[byteIndex].toInt() and 0xff
            val hi = pcm16le[byteIndex + 1].toInt()
            val sample = (hi shl 8) or lo
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) / 32768.0f
            byteIndex += 2
        }
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, VOICE_SAMPLE_RATE)
            recognizer.decode(stream)
            normalizeVoiceText(recognizer.getResult(stream).text)
        } finally {
            stream.release()
        }
    }

    private fun commitVoiceText(text: String) {
        val finalText = punctuateVoiceText(text)
        if (finalText.isBlank()) {
            refreshCandidateViews()
            return
        }
        commitDirectText(finalText)
    }

    private fun normalizeVoiceText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""
        val noSpaces = if (trimmed.any { it in '\u4E00'..'\u9FFF' }) {
            trimmed.replace(" ", "")
        } else {
            trimmed
        }
        return replaceSpokenPunctuation(noSpaces)
    }

    private fun replaceSpokenPunctuation(text: String): String {
        return text
            .replace("逗号", "，")
            .replace("顿号", "、")
            .replace("句号", "。")
            .replace("问号", "？")
            .replace("感叹号", "！")
            .replace("叹号", "！")
            .replace("冒号", "：")
            .replace("分号", "；")
            .replace("换行", "\n")
    }

    private fun punctuateVoiceText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.last() in setOf('。', '，', '、', '？', '！', '；', '：', '.', ',', '?', '!', ';', ':', '\n')) {
            return trimmed
        }
        if (!trimmed.any { it in '\u4E00'..'\u9FFF' }) return "$trimmed."
        val questionEndings = listOf("吗", "么", "呢", "嘛", "是不是", "对不对", "好不好", "行不行", "可以吗")
        return trimmed + if (questionEndings.any { trimmed.endsWith(it) }) "？" else "。"
    }

    private fun captureSystemClipboard() {
        val text = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        pushClipboardHistory(text)
    }

    private fun pushClipboardHistory(text: String) {
        clipboardHistory.remove(text)
        clipboardHistory.add(0, text)
        while (clipboardHistory.size > 30) clipboardHistory.removeLast()
        saveClipboardHistory()
        if (::clipboardList.isInitialized) refreshClipboardPanel()
    }

    private fun refreshClipboardPanel() {
        if (!::clipboardList.isInitialized) return
        clipboardList.removeAllViews()
        if (clipboardHistory.isEmpty()) {
            clipboardList.addView(TextView(this).apply {
                text = "暂无历史"
                setTextColor(colorHintText())
                textSize = 14f
                setTypeface(activeTypeface, Typeface.NORMAL)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
            return
        }
        clipboardHistory.forEach { item ->
            val line = TextView(this).apply {
                text = item
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                textSize = 16f
                setTypeface(activeTypeface, Typeface.NORMAL)
                setTextColor(colorKeyText())
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = keyBackground(colorKeyBackground(), colorKeyBorder())
                setOnClickListener {
                    commitDirectText(item)
                    switchMode(Mode.FUNC)
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6)
            clipboardList.addView(line, lp)
        }
    }

    private fun saveClipboardHistory() {
        val raw = clipboardHistory.joinToString("\u001F")
        getSharedPreferences("flick_ime", Context.MODE_PRIVATE).edit().putString("clipboard_history", raw).apply()
    }

    private fun loadClipboardHistory() {
        val raw = getSharedPreferences("flick_ime", Context.MODE_PRIVATE).getString("clipboard_history", "").orEmpty()
        if (raw.isBlank()) return
        clipboardHistory.clear()
        raw.split("\u001F").map { it.trim() }.filter { it.isNotBlank() }.forEach { clip ->
            if (!clipboardHistory.contains(clip)) clipboardHistory += clip
        }
    }

    private fun selectAll() {
        withInputConnection {
            val down = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
            val up = KeyEvent(0L, 0L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON)
            it.sendKeyEvent(down)
            it.sendKeyEvent(up)
        }
    }

    private fun loadLanguagePrefs() {
        val enabled = UiPrefs.getEnabledInputLanguages(this).ifEmpty { setOf(InputLanguage.PINYIN) }
        enabledInputLanguages = listOf(InputLanguage.PINYIN, InputLanguage.ZHUYIN, InputLanguage.JAPANESE, InputLanguage.SHAPE)
            .filter { enabled.contains(it) }
            .ifEmpty { listOf(InputLanguage.PINYIN) }
        val current = UiPrefs.getCurrentInputLanguage(this)
        currentInputLanguage = if (enabledInputLanguages.contains(current)) current else enabledInputLanguages.first()
        if (currentInputLanguage != current) {
            UiPrefs.setCurrentInputLanguage(this, currentInputLanguage)
        }
    }

    private fun loadCurrentLanguageKeys(): List<FlickKeySpec> {
        return when (currentInputLanguage) {
            InputLanguage.PINYIN -> KeyMapStore.loadPinyinKeys(this)
            InputLanguage.ZHUYIN -> KeyMapStore.loadZhuyinKeys(this)
            InputLanguage.JAPANESE -> KeyMapStore.loadJapaneseKeys(this)
            InputLanguage.SHAPE -> loadShapeKeys()
        }
    }

    private fun loadShapeKeys(): List<FlickKeySpec> {
        return KeyMapStore.loadAlphaKeys(this).map {
            val center = if (it.center.contains("大写锁定") || it.center == "⇧") "" else it.center
            FlickKeySpec(
                center = center,
                left = it.left,
                up = it.up,
                right = it.right,
                down = it.down,
                upLeft = it.upLeft,
                upRight = it.upRight,
                downLeft = it.downLeft,
                downRight = it.downRight,
                zone = KeyZone.Shengmu
            )
        }
    }

    private fun currentLanguageModeLabel(): String {
        return when (currentInputLanguage) {
            InputLanguage.PINYIN -> "拼音"
            InputLanguage.ZHUYIN -> "注音"
            InputLanguage.JAPANESE -> "かな"
            InputLanguage.SHAPE -> "五笔"
        }
    }

    private fun languagePlaceholder(): String {
        return when (currentInputLanguage) {
            InputLanguage.PINYIN -> "拼音"
            InputLanguage.ZHUYIN -> "注音"
            InputLanguage.JAPANESE -> "かな"
            InputLanguage.SHAPE -> "五笔/形码"
        }
    }

    private fun switchLanguageQuick() {
        loadLanguagePrefs()
        if (enabledInputLanguages.size <= 1) return
        val idx = enabledInputLanguages.indexOf(currentInputLanguage).coerceAtLeast(0)
        val next = enabledInputLanguages[(idx + 1) % enabledInputLanguages.size]
        if (next == currentInputLanguage) return
        currentInputLanguage = next
        UiPrefs.setCurrentInputLanguage(this, next)
        resetComposing()
        mode = Mode.FLICK
        rebuildPanelsFromSettings()
        refreshCandidateViews()
    }

    private fun switchInputMethodQuick() {
        playKeyClick()
        try {
            if (!switchToNextInputMethod(false)) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        } catch (_: Throwable) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun hideImeWindow() {
        playKeyClick()
        try {
            requestHideSelf(0)
        } catch (_: Throwable) {
        }
    }

    private fun openImeSettings() {
        playKeyClick()
        try {
            val intent = android.content.Intent(this, ImeSettingsActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private fun commitDirectText(text: String): Boolean {
        val committed = commitTextSafe(text)
        if (committed) refreshContextualCandidatesFromEditor()
        return committed
    }

    private fun commitTextSafe(text: String): Boolean {
        if (text.isEmpty()) return true
        return withInputConnection { it.commitText(text, 1) }
    }

    private fun withInputConnection(block: (InputConnection) -> Unit): Boolean {
        val ic = currentInputConnection ?: return false
        try {
            block(ic)
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    private fun resetComposing() {
        candidateToken.incrementAndGet()
        shengmuPart = null
        composedSyllables.clear()
        composedDisplaySyllables.clear()
        composingText = ""
        allCandidates = emptyList()
        composingSessionFullQuery = ""
        composingSessionCommittedText = ""
        if (::composingView.isInitialized && ::candidateRow.isInitialized) {
            refreshCandidateViews()
        }
    }

    private fun computeCandidates(
        query: String,
        syllables: List<String>
    ): List<CandidateEntry> {
        if (query.isBlank()) return emptyList()
        return try {
            val base = when (currentInputLanguage) {
                InputLanguage.JAPANESE -> computeJapaneseCandidates(syllables)
                InputLanguage.SHAPE -> ShapeCodeManager.queryCandidates(this, query, 48)
                    .map { CandidateEntry(it, syllables.size.coerceAtLeast(1)) }
                else -> {
                    if (currentInputLanguage == InputLanguage.PINYIN && isPinyinInitialSequence(syllables)) {
                        pinyinEngine.queryInitialCandidates(syllables, 48)
                            .map { CandidateEntry(it, syllables.size, recordChoice = false) }
                    } else if (syllables.size <= 1) {
                        pinyinEngine.queryCandidates(query, 48).map { CandidateEntry(it, 1) }
                    } else {
                        computeMultiSyllableCandidates(syllables)
                    }
                }
            }
            mergeSpecialCandidates(base, timeCandidatesForQuery(query, syllables))
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun mergeSpecialCandidates(
        base: List<CandidateEntry>,
        special: List<CandidateEntry>
    ): List<CandidateEntry> {
        if (special.isEmpty()) return base
        val out = ArrayList<CandidateEntry>(base.size + special.size)
        fun add(candidate: CandidateEntry) {
            if (candidate.text.isBlank()) return
            if (out.none { it.text == candidate.text }) out += candidate
        }
        base.firstOrNull()?.let(::add)
        special.forEach(::add)
        base.drop(1).forEach(::add)
        return out.take(48)
    }

    private fun timeCandidatesForQuery(query: String, syllables: List<String>): List<CandidateEntry> {
        if (currentInputLanguage != InputLanguage.PINYIN && currentInputLanguage != InputLanguage.ZHUYIN) {
            return emptyList()
        }
        val normalized = query.lowercase(Locale.getDefault()).replace("'", "")
        val matched = normalized in setOf(
            "shijian",
            "riqi",
            "jintian",
            "xianzai",
            "jidian"
        )
        if (!matched) return emptyList()
        val consume = syllables.size.coerceAtLeast(1)
        return currentTimeCandidateTexts().map { CandidateEntry(it, consume, recordChoice = false) }
    }

    private fun computeJapaneseCandidates(syllables: List<String>): List<CandidateEntry> {
        val size = syllables.size
        if (size <= 0) return emptyList()
        val out = ArrayList<CandidateEntry>(64)

        fun addCandidate(text: String, consume: Int) {
            if (text.isBlank()) return
            val normalizedConsume = consume.coerceIn(1, size)
            if (out.none { it.text == text && it.consumeSyllables == normalizedConsume }) {
                out += CandidateEntry(text, normalizedConsume)
            }
        }

        val fullReading = syllables.joinToString("")

        // 1) 优先给“前缀汉字 + 后缀假名”的混合候选（如：愛してた）
        buildLeadingKanjiMixedCandidate(syllables)?.let { mixed ->
            if (mixed != fullReading) addCandidate(mixed, size)
        }

        // 2) 第二候选给纯假名原串
        addCandidate(fullReading, size)

        // 3) 再给分段混合候选（避免整串强制全汉字）
        buildGreedyJapaneseSentenceCandidate(syllables, minConvertLen = 2)?.let { mixed ->
            if (mixed != fullReading) addCandidate(mixed, size)
        }

        // 4) 最后再给整串全转换候选
        japaneseEngine.queryCandidates(fullReading, 24).forEach { addCandidate(it, size) }

        val maxPrefix = minOf(size, 8)
        for (consume in maxPrefix downTo 1) {
            val reading = syllables.take(consume).joinToString("")
            val limit = when {
                consume == size -> 24
                consume >= 3 -> 16
                else -> 20
            }
            japaneseEngine.queryCandidates(reading, limit).forEach { addCandidate(it, consume) }
        }

        val kata = hiraganaToKatakana(fullReading)
        if (kata.isNotBlank() && kata != fullReading) {
            addCandidate(kata, size)
        }
        return out.take(48)
    }

    private fun buildLeadingKanjiMixedCandidate(syllables: List<String>): String? {
        if (syllables.size < 2) return null
        val full = syllables.joinToString("")
        val maxPrefix = minOf(8, syllables.size)
        for (consume in maxPrefix downTo 2) {
            val prefixReading = syllables.take(consume).joinToString("")
            val prefixCand = japaneseEngine.queryCandidates(prefixReading, 12)
                .firstOrNull { it.isNotBlank() && it != prefixReading && containsKanji(it) }
                ?: continue
            val suffix = syllables.drop(consume).joinToString("")
            val mixed = prefixCand + suffix
            if (mixed.isNotBlank() && mixed != full) return mixed
        }
        return null
    }

    private fun computeMultiSyllableCandidates(syllables: List<String>): List<CandidateEntry> {
        val size = syllables.size
        val out = ArrayList<CandidateEntry>(64)

        fun addCandidate(text: String, consume: Int) {
            if (text.isBlank()) return
            val normalizedConsume = consume.coerceIn(1, size)
            if (out.none { it.text == text && it.consumeSyllables == normalizedConsume }) {
                out += CandidateEntry(text, normalizedConsume)
            }
        }

        val fullQuery = syllables.joinToString("")
        pinyinEngine.queryCandidates(fullQuery, 16).forEach { text ->
            if (text.length >= 2) addCandidate(text, size)
        }

        buildGreedySentenceCandidate(syllables)?.let { addCandidate(it, size) }

        val maxPrefix = minOf(size, 6)
        for (consume in maxPrefix downTo 1) {
            val prefix = syllables.take(consume).joinToString("")
            val limit = if (consume == 1) 16 else 10
            pinyinEngine.queryCandidates(prefix, limit).forEach { text ->
                if (consume > 1 && text.length == 1) return@forEach
                addCandidate(text, consume)
            }
        }

        return out.take(48)
    }

    private fun buildGreedySentenceCandidate(syllables: List<String>): String? {
        if (syllables.isEmpty()) return null
        var i = 0
        val sb = StringBuilder()
        while (i < syllables.size) {
            var chosen: String? = null
            var chosenLen = 0
            val maxChunk = minOf(4, syllables.size - i)
            for (len in maxChunk downTo 1) {
                val py = syllables.subList(i, i + len).joinToString("")
                val candidates = pinyinEngine.queryCandidates(py, if (len == 1) 4 else 6)
                val picked = candidates.firstOrNull { c ->
                    if (len == 1) c.isNotBlank() else c.length >= 2
                } ?: candidates.firstOrNull()
                if (picked != null) {
                    chosen = picked
                    chosenLen = len
                    break
                }
            }
            if (chosen == null || chosenLen <= 0) return null
            sb.append(chosen)
            i += chosenLen
        }
        val sentence = sb.toString()
        return sentence.takeIf { it.isNotBlank() }
    }

    private fun buildGreedyJapaneseSentenceCandidate(
        syllables: List<String>,
        minConvertLen: Int = 1
    ): String? {
        if (syllables.isEmpty()) return null
        var i = 0
        val sb = StringBuilder()
        while (i < syllables.size) {
            var chosen: String? = null
            var chosenLen = 0
            val maxChunk = minOf(8, syllables.size - i)
            for (len in maxChunk downTo 1) {
                if (len < minConvertLen) continue
                val reading = syllables.subList(i, i + len).joinToString("")
                val picked = japaneseEngine.queryCandidates(reading, if (len == 1) 6 else 8).firstOrNull()
                if (picked != null) {
                    chosen = picked
                    chosenLen = len
                    break
                }
            }
            if (chosen == null || chosenLen <= 0) {
                sb.append(syllables[i])
                i += 1
                continue
            }
            sb.append(chosen)
            i += chosenLen
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun hiraganaToKatakana(text: String): String {
        if (text.isBlank()) return text
        val out = StringBuilder(text.length)
        text.forEach { c ->
            when {
                c == 'ゔ' -> out.append('ヴ')
                c in 'ぁ'..'ゖ' -> out.append((c.code + 0x60).toChar())
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun containsKanji(text: String): Boolean {
        return text.any { it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' }
    }

    private fun candidateItemBackground(): android.graphics.drawable.Drawable? {
        // 导入输入法背景图后，候选网格去掉实体框，避免遮挡背景。
        return if (hasImageBackgroundForUi()) null else keyBackground(colorKeyBackground(), colorKeyBorder())
    }

    private fun requestCandidatesAsync() {
        val syllablesSnapshot = buildSyllablesForCandidates()
        val query = syllablesSnapshot.joinToString("")
        val token = candidateToken.incrementAndGet()
        if (query.isBlank()) {
            allCandidates = buildContextualCandidatesFromEditor()
            refreshCandidateViews()
            return
        }
        allCandidates = emptyList()
        candidateExecutor.execute {
            val result = computeCandidates(query, syllablesSnapshot)
            mainHandler.post {
                if (token != candidateToken.get()) return@post
                allCandidates = result
                refreshCandidateViews()
            }
        }
    }

    private fun refreshContextualCandidatesFromEditor() {
        if (!canShowContextualCandidates()) return
        candidateToken.incrementAndGet()
        allCandidates = buildContextualCandidatesFromEditor()
        refreshCandidateViews()
    }

    private fun canShowContextualCandidates(): Boolean {
        return shengmuPart == null &&
            composedSyllables.isEmpty() &&
            composedDisplaySyllables.isEmpty() &&
            composingText.isBlank() &&
            !voiceListening
    }

    private fun buildContextualCandidatesFromEditor(): List<CandidateEntry> {
        if (!canShowContextualCandidates()) return emptyList()
        val before = currentInputConnection
            ?.getTextBeforeCursor(80, 0)
            ?.toString()
            .orEmpty()
        if (before.isBlank()) return emptyList()

        val out = ArrayList<CandidateEntry>(8)
        out += buildInitialContextCandidates(before)
        evaluateMathExpressionBeforeCursor(before)?.let { out += CandidateEntry(it, 0, recordChoice = false) }
        if (hasTimeContextTrigger(before)) {
            currentTimeCandidateTexts().forEach { out += CandidateEntry(it, 0, recordChoice = false) }
        }
        return out.distinctBy { it.text }.take(12)
    }

    private fun buildInitialContextCandidates(before: String): List<CandidateEntry> {
        if (currentInputLanguage != InputLanguage.PINYIN || mode == Mode.ALPHA) return emptyList()
        val query = extractInitialContextQuery(before) ?: return emptyList()
        return pinyinEngine.queryInitialCandidates(query.tokens, 12)
            .filter { it.isNotBlank() }
            .map {
                CandidateEntry(
                    text = it,
                    consumeSyllables = 0,
                    recordChoice = false,
                    replaceBeforeCursor = query.typedLength
                )
            }
    }

    private fun extractInitialContextQuery(before: String): InitialContextQuery? {
        var start = before.length
        while (start > 0 && isInitialContextChar(before[start - 1])) {
            start -= 1
        }
        if (start == before.length) return null

        val suffix = before.substring(start)
        if (suffix.length > 12) return null
        val normalized = suffix.lowercase(Locale.getDefault())
        val tokens = parseInitialContextTokens(normalized) ?: return null
        if (tokens.isEmpty() || tokens.size > 8) return null
        return InitialContextQuery(suffix.length, tokens)
    }

    private fun isInitialContextChar(c: Char): Boolean {
        return c in 'a'..'z' || c in 'A'..'Z' || isPinyinSeparatorChar(c)
    }

    private fun parseInitialContextTokens(text: String): List<String>? {
        if (text.isBlank()) return null
        val normalized = text.lowercase(Locale.getDefault())
        return if (normalized.any(::isPinyinSeparatorChar)) {
            val out = mutableListOf<String>()
            normalized
                .splitToSequence(*pinyinSeparatorCharsArray)
                .filter { it.isNotBlank() }
                .forEach { segment ->
                    val parsed = parseInitialSegment(segment) ?: return null
                    out += parsed
                }
            out.takeIf { it.isNotEmpty() }
        } else {
            parseInitialSegment(normalized)
        }
    }

    private fun parseInitialSegment(segment: String): List<String>? {
        if (segment.isBlank()) return null
        val out = mutableListOf<String>()
        var index = 0
        while (index < segment.length) {
            val remaining = segment.substring(index)
            val token = when {
                remaining.startsWith("zh") -> "zh"
                remaining.startsWith("ch") -> "ch"
                remaining.startsWith("sh") -> "sh"
                else -> remaining.first().toString()
            }
            if (!isPinyinInitialToken(token)) return null
            out += token
            index += token.length
        }
        return out
    }

    private fun evaluateMathExpressionBeforeCursor(before: String): String? {
        if (before.isEmpty() || before.last().isWhitespace()) return null
        val expr = extractMathExpressionSuffix(before) ?: return null
        val normalized = expr
            .replace('＋', '+')
            .replace('－', '-')
            .replace('×', '*')
            .replace('÷', '/')
            .replace('（', '(')
            .replace('）', ')')
        if (!normalized.any { it == '+' || it == '-' || it == '*' || it == '/' }) return null
        if (normalized.trimEnd().lastOrNull() in setOf('+', '-', '*', '/', '.')) return null
        val value = MathExpressionParser(normalized).parse() ?: return null
        return formatMathResult(value)
    }

    private fun extractMathExpressionSuffix(before: String): String? {
        var start = before.length
        while (start > 0) {
            val c = before[start - 1]
            val allowed = c.isDigit() ||
                c == '.' ||
                c == '+' || c == '-' || c == '*' || c == '/' || c == '×' || c == '÷' ||
                c == '＋' || c == '－' ||
                c == '(' || c == ')' || c == '（' || c == '）' ||
                c.isWhitespace()
            if (!allowed) break
            start -= 1
        }
        val expr = before.substring(start).trim()
        if (expr.length < 3) return null
        if (expr.none { it.isDigit() }) return null
        return expr
    }

    private fun formatMathResult(value: Double): String? {
        if (!value.isFinite()) return null
        val rounded = kotlin.math.round(value)
        if (abs(value - rounded) < 1e-9) {
            return rounded.toLong().toString()
        }
        return BigDecimal.valueOf(value)
            .setScale(10, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .take(18)
            .trimEnd('.')
            .takeIf { it.isNotBlank() }
    }

    private fun hasTimeContextTrigger(before: String): Boolean {
        val trimmed = before.trimEnd()
        if (trimmed.isBlank()) return false
        val lower = trimmed.lowercase(Locale.getDefault())
        return listOf("时间", "现在", "日期", "今天", "几点", "几点了", "time", "date", "now")
            .any { lower.endsWith(it) }
    }

    private fun currentTimeCandidateTexts(): List<String> {
        val now = LocalDateTime.now()
        val locale = Locale.CHINA
        return listOf(
            now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale)),
            now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", locale)),
            now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", locale))
        ).distinct()
    }

    private fun buildSyllablesForCandidates(): List<String> {
        val pending = shengmuPart.orEmpty()
        if (currentInputLanguage == InputLanguage.PINYIN &&
            pending.isNotBlank() &&
            isPinyinInitialToken(pending)
        ) {
            if (composedSyllables.isEmpty()) return listOf(pending.lowercase(Locale.getDefault()))
            if (isPinyinInitialSequence(composedSyllables)) {
                return composedSyllables + pending.lowercase(Locale.getDefault())
            }
        }
        return composedSyllables.toList()
    }

    private fun buildComposingDisplay(): String {
        val sep = if (currentInputLanguage == InputLanguage.JAPANESE || currentInputLanguage == InputLanguage.SHAPE) "" else "'"
        val base = composedDisplaySyllables.joinToString(sep)
        val pending = shengmuPart.orEmpty()
        if (pending.isBlank()) return base
        return if (base.isBlank()) pending else base + sep + pending
    }

    private fun buildRawQueryForCommit(): String {
        val base = composedSyllables.joinToString("")
        val pending = shengmuPart.orEmpty()
        return when (currentInputLanguage) {
            InputLanguage.PINYIN -> if (pending.isBlank()) base else base + pending
            InputLanguage.ZHUYIN -> if (pending.isBlank()) base else base + ZhuyinConverter.toPinyin(pending)
            InputLanguage.JAPANESE -> base
            InputLanguage.SHAPE -> base
        }
    }

    private fun buildRawDisplayForCommit(): String {
        val base = composedDisplaySyllables.joinToString("")
        val pending = shengmuPart.orEmpty()
        return when (currentInputLanguage) {
            InputLanguage.PINYIN -> buildRawQueryForCommit()
            InputLanguage.ZHUYIN -> if (pending.isBlank()) base else base + pending
            InputLanguage.JAPANESE -> base
            InputLanguage.SHAPE -> base
        }
    }

    private class MathExpressionParser(private val source: String) {
        private var index = 0

        fun parse(): Double? {
            return try {
                val value = parseExpression()
                skipSpaces()
                if (index == source.length) value else null
            } catch (_: ArithmeticException) {
                null
            } catch (_: NumberFormatException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    match('+') -> value + parseTerm()
                    match('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                value = when {
                    match('*') -> value * parseFactor()
                    match('/') -> {
                        val divisor = parseFactor()
                        if (abs(divisor) < 1e-12) throw ArithmeticException("divide by zero")
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()
            if (match('(')) {
                val value = parseExpression()
                skipSpaces()
                if (!match(')')) throw IllegalArgumentException("missing closing parenthesis")
                return value
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipSpaces()
            val start = index
            var dotSeen = false
            while (index < source.length) {
                val c = source[index]
                when {
                    c.isDigit() -> index += 1
                    c == '.' && !dotSeen -> {
                        dotSeen = true
                        index += 1
                    }
                    else -> break
                }
            }
            if (start == index) throw NumberFormatException("number expected")
            return source.substring(start, index).toDouble()
        }

        private fun match(c: Char): Boolean {
            if (index >= source.length || source[index] != c) return false
            index += 1
            return true
        }

        private fun skipSpaces() {
            while (index < source.length && source[index].isWhitespace()) index += 1
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun navigationBarBottomInset(insets: WindowInsets): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
    }

    private fun displayLabel(text: String): String {
        val hasAsciiLetter = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        return if (hasAsciiLetter) text.uppercase() else text
    }

    private fun playKeyClick() {
        try {
            if (UiPrefs.getUseCustomSound(this) && customSoundId != 0) {
                soundPool?.play(customSoundId, 1f, 1f, 1, 0, 1f)
            } else if (isSoundEnabled()) {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.45f)
            }
            if (isVibrationEnabled()) {
                val hapticDone = if (::keyboardContainer.isInitialized) {
                    keyboardContainer.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                } else false
                if (!hapticDone) {
                    val vib = vibrator
                    if (vib != null && vib.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vib.vibrate(18L)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun lighten(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val nr = (r + (255 - r) * 0.12f).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * 0.12f).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * 0.12f).toInt().coerceIn(0, 255)
        return Color.rgb(nr, ng, nb)
    }

    private fun readableTextColor(background: Int): Int {
        val luminance = (Color.red(background) * 299 + Color.green(background) * 587 + Color.blue(background) * 114) / 1000
        return if (luminance >= 160) Color.BLACK else Color.WHITE
    }

    private fun commitAlphaChar(ch: String) {
        if (ch == "大写锁定" || ch == "⇧") {
            toggleAlphaCaps()
            return
        }
        if (ch.length == 1 && ch[0].isLetter()) {
            val out = if (alphaCapsLock) ch.uppercase() else ch.lowercase()
            commitDirectText(out)
        } else {
            commitDirectText(ch)
        }
    }

    private fun toggleAlphaCaps() {
        alphaCapsLock = !alphaCapsLock
        val old = alphaPanel
        val wasVisible = old.visibility == View.VISIBLE
        val index = keyboardContainer.indexOfChild(old)
        val newPanel = buildAlphaPanel().apply { visibility = if (wasVisible) View.VISIBLE else View.GONE }
        keyboardContainer.removeViewAt(index)
        keyboardContainer.addView(newPanel, index)
        alphaPanel = newPanel
    }

    private fun reloadCustomUiSettings() {
        keyboardTheme = ThemeManager.getCurrentTheme(this)
        activeTypeface = FontManager.resolveTypeface(this)
        centerTextSp = UiPrefs.getCenterTextSp(this)
        sideTextSp = UiPrefs.getSideTextSp(this)
        keyTextAlpha = UiPrefs.getKeyTextAlpha(this)
        keyImageAlpha = UiPrefs.getKeyImageAlpha(this)
        keyBgAlpha = UiPrefs.getKeyBgAlpha(this)
        customFontColor = UiPrefs.resolveFontColor(this)
        keySizeScale = UiPrefs.getKeySizeScale(this).coerceIn(0.75f, 1.25f)
        keyGapDp = UiPrefs.getKeyGapDp(this).coerceIn(0f, 14f)
        enableEightDirectionPinyinFlick = UiPrefs.getEnableEightDirectionPinyin(this)
        enableEightDirectionSymbolFlick = UiPrefs.getEnableEightDirectionSymbol(this)
        showCenterKeyText = UiPrefs.getShowCenterKeyText(this)
        showSideKeyText = UiPrefs.getShowSideKeyText(this)
        rightActionKeyOrder = loadActionKeyOrderFromPrefs()
        keyboardBgImage = loadBitmap(UiPrefs.getImeBgImagePath(this))
        keyBgImage = loadBitmap(UiPrefs.getKeyBgImagePath(this))
        reloadCustomSound()
    }

    private fun loadActionKeyOrderFromPrefs(): List<ActionKeyKind> {
        val raw = UiPrefs.getActionKeyOrder(this)
        val parsed = raw.mapNotNull { actionKeyKindFromPref(it) }.distinct()
        if (parsed.size == 4) return parsed
        return listOf(
            ActionKeyKind.BACKSPACE,
            ActionKeyKind.VOICE,
            ActionKeyKind.FUNC,
            ActionKeyKind.ENTER
        )
    }

    private fun actionKeyKindFromPref(raw: String): ActionKeyKind? {
        return when (raw) {
            UiPrefs.ACTION_KEY_BACKSPACE -> ActionKeyKind.BACKSPACE
            UiPrefs.ACTION_KEY_SPACE, UiPrefs.ACTION_KEY_VOICE -> ActionKeyKind.VOICE
            UiPrefs.ACTION_KEY_ENTER -> ActionKeyKind.ENTER
            UiPrefs.ACTION_KEY_FUNC -> ActionKeyKind.FUNC
            else -> null
        }
    }

    private fun loadBitmap(path: String): android.graphics.Bitmap? {
        if (path.isBlank()) return null
        return try {
            if (path.startsWith("asset://")) {
                assets.open(path.removePrefix("asset://")).use { BitmapFactory.decodeStream(it) }
            } else {
                val f = File(path)
                if (!f.exists()) null else f.inputStream().use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun reloadCustomSound() {
        soundPool?.release()
        soundPool = null
        customSoundId = 0
        if (!UiPrefs.getUseCustomSound(this)) return
        val path = UiPrefs.getCustomSoundPath(this)
        if (path.isBlank() || !File(path).exists()) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build().also {
            customSoundId = it.load(path, 1)
        }
    }

    private fun colorKeyboardBackground(): Int = colorOrDefault(keyboardTheme.colors.keyboardBackground, "#AEB7C5")
    private fun colorPanelBackground(): Int = colorOrDefault(keyboardTheme.colors.panelBackground, "#BBC4D2")
    private fun resolvedPanelBackground(): Int {
        return if (hasImageBackgroundForUi()) Color.TRANSPARENT else colorPanelBackground()
    }
    private fun resolvedCandidatePanelBackground(): Int {
        return resolvedPanelBackground()
    }
    private fun hasImageBackgroundForUi(): Boolean {
        if (keyboardBgImage != null || keyBgImage != null) return true
        return UiPrefs.getImeBgImagePath(this).isNotBlank() || UiPrefs.getKeyBgImagePath(this).isNotBlank()
    }
    private fun colorKeyBackground(): Int = colorOrDefault(keyboardTheme.colors.keyBackground, "#EEF1F5")
    private fun colorKeyBorder(): Int = colorOrDefault(keyboardTheme.colors.keyBorder, "#A6AFBC")
    private fun colorKeyText(): Int = withAlpha(customFontColor ?: colorOrDefault(keyboardTheme.colors.keyText, "#111827"))
    private fun colorSubKeyText(): Int = withAlpha(customFontColor ?: colorOrDefault(keyboardTheme.colors.subKeyText, "#4B5563"))
    private fun colorAccentKeyBackground(): Int = colorOrDefault(keyboardTheme.colors.accentKeyBackground, "#1677FF")
    private fun colorAccentKeyText(): Int = withAlpha(customFontColor ?: colorOrDefault(keyboardTheme.colors.accentKeyText, "#FFFFFF"))
    private fun colorSelectedItemBackground(): Int = colorOrDefault(keyboardTheme.colors.selectedItemBackground, "#6B7280")
    private fun colorHintText(): Int = customFontColor ?: colorOrDefault(keyboardTheme.colors.hintText, "#6B7280")

    private fun colorOrDefault(value: String, fallback: String): Int {
        return try {
            Color.parseColor(value)
        } catch (_: Throwable) {
            Color.parseColor(fallback)
        }
    }

    private fun withAlpha(color: Int): Int {
        val a = (keyTextAlpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun withCustomAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun isSoundEnabled(): Boolean {
        return getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean("sound_enabled", true)
    }

    private fun isVibrationEnabled(): Boolean {
        return getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean("vibration_enabled", false)
    }

}
