package com.example.flickime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioManager
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
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.flickime.data.DefaultKeyMap
import com.example.flickime.data.KeyMapStore
import com.example.flickime.engine.LexiconManager
import com.example.flickime.engine.PinyinEngine
import com.example.flickime.model.FlickDirection
import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.KeyZone
import com.example.flickime.theme.FontManager
import com.example.flickime.theme.KeyboardTheme
import com.example.flickime.theme.ThemeManager
import com.example.flickime.theme.UiPrefs
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class FlickImeService : InputMethodService() {
    companion object {
        private const val SETTINGS_PREFS = "flick_settings"
    }
    private data class DirectionalSpec(
        val center: String,
        val left: String,
        val up: String,
        val right: String,
        val down: String
    )
    private data class CandidateEntry(
        val text: String,
        val consumeSyllables: Int
    )
    private data class ModeSwitchEntry(
        val container: FrameLayout,
        val label: TextView,
        val target: Mode
    )

    private lateinit var pinyinEngine: PinyinEngine
    private val candidateExecutor = Executors.newSingleThreadExecutor()
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
    private var keySizeScale: Float = 1f
    private var keyGapDp: Float = 4f
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var audioManager: AudioManager
    private var vibrator: Vibrator? = null
    private var soundPool: SoundPool? = null
    private var customSoundId: Int = 0

    private var shengmuPart: String? = null
    private val composedSyllables = mutableListOf<String>()
    private var composingText: String = ""
    private var allCandidates: List<CandidateEntry> = emptyList()
    private var composingSessionFullQuery: String = ""
    private var composingSessionCommittedText: String = ""

    private lateinit var composingView: TextView
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

    private var colWidth = 0
    private var rowHeight = 0
    private var rowGap = 0
    private var candidateStripHeight = 0
    private var alphaCapsLock = false
    private val clipboardHistory = mutableListOf<String>()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureSystemClipboard() }
    private val modeSwitchViews = mutableListOf<ModeSwitchEntry>()

    private enum class Mode { FLICK, ALPHA, NUM, SYMBOL, CANDIDATE, FUNC, CLIPBOARD }
    private var mode: Mode = Mode.FLICK

    override fun onCreate() {
        super.onCreate()
        pinyinEngine = PinyinEngine(this)
        candidateExecutor.execute {
            runCatching { LexiconManager.warmup(this) }
        }
        keyboardTheme = ThemeManager.getCurrentTheme(this)
        reloadCustomUiSettings()
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
        candidateExecutor.shutdownNow()
        super.onDestroy()
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(6))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }

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
        rootOverlay.addView(hintCenter)
        rootOverlay.addView(hintLeft)
        rootOverlay.addView(hintUp)
        rootOverlay.addView(hintRight)
        rootOverlay.addView(hintDown)
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
        updateRootBackground()
        rebuildPanelsFromSettings()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        keyboardTheme = ThemeManager.getCurrentTheme(this)
        reloadCustomUiSettings()
        updateRootBackground()
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

        composingView = TextView(this).apply {
            textSize = 12f
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorHintText())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "拼音"
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

        bottom.addView(scroller)
        bottom.addView(expand)

        strip.addView(composingView)
        strip.addView(bottom)
        return strip
    }

    private fun buildFlickPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(resolvedPanelBackground())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val keys = KeyMapStore.loadPinyinKeys(this)
        val rows = listOf(
            listOf(
                modeSwitchKey("☆123", Mode.NUM),
                pinyinFlickKey(keys[0]), pinyinFlickKey(keys[1]), pinyinFlickKey(keys[2]),
                backspaceKey()
            ),
            listOf(
                modeSwitchKey("ABC", Mode.ALPHA),
                pinyinFlickKey(keys[3]), pinyinFlickKey(keys[4]), pinyinFlickKey(keys[5]),
                controlKey("空格") { onSpacePressed() }
            ),
            listOf(
                modeSwitchKey("拼音", Mode.FLICK),
                pinyinFlickKey(keys[6]), pinyinFlickKey(keys[7]), pinyinFlickKey(keys[8]),
                primaryKey("回车") { sendEnter() }
            ),
            listOf(
                modeSwitchKey("符号", Mode.SYMBOL),
                pinyinFlickKey(keys[9]), pinyinFlickKey(keys[10]), pinyinFlickKey(keys[11]),
                modeSwitchKey("功能", Mode.FUNC)
            )
        )

        rows.forEachIndexed { index, rowViews -> panel.addView(makeFixedRow(rowViews, index != rows.lastIndex)) }
        panel.addView(buildBottomUtilityBar())
        return panel
    }

    private fun buildAlphaPanel(): View {
        val abc = DirectionalSpec("b", "a", "b", "c", "b")
        val def = DirectionalSpec("e", "d", "e", "f", "e")
        val ghi = DirectionalSpec("h", "g", "h", "i", "h")
        val jkl = DirectionalSpec("k", "j", "k", "l", "k")
        val mno = DirectionalSpec("n", "m", "n", "o", "n")
        val pqr = DirectionalSpec("q", "p", "q", "r", "q")
        val stu = DirectionalSpec("t", "s", "t", "u", "t")
        val vwx = DirectionalSpec("w", "v", "w", "x", "w")
        val yzq = DirectionalSpec("z", "y", "z", "'", "z")

        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), groupedFlickKey("ABC", abc), groupedFlickKey("DEF", def), groupedFlickKey("GHI", ghi), backspaceKey()),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), groupedFlickKey("JKL", jkl), groupedFlickKey("MNO", mno), groupedFlickKey("PQR", pqr), controlKey("空格") { onSpacePressed() }),
            listOf(modeSwitchKey("拼音", Mode.FLICK), groupedFlickKey("STU", stu), groupedFlickKey("VWX", vwx), groupedFlickKey("YZ'", yzq), primaryKey("回车") { sendEnter() }),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), inputKey(","), controlKey(if (alphaCapsLock) "大写锁定开" else "大写锁定关") { toggleAlphaCaps() }, inputKey("."), modeSwitchKey("功能", Mode.FUNC))
        )
        return panelFromRows(rows)
    }

    private fun buildNumPanel(): View {
        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), inputKey("1"), inputKey("2"), inputKey("3"), backspaceKey()),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), inputKey("4"), inputKey("5"), inputKey("6"), controlKey("空格") { onSpacePressed() }),
            listOf(modeSwitchKey("拼音", Mode.FLICK), inputKey("7"), inputKey("8"), inputKey("9"), primaryKey("回车") { sendEnter() }),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), inputKey("("), inputKey("0"), inputKey(")"), modeSwitchKey("功能", Mode.FUNC))
        )
        return panelFromRows(rows)
    }

    private fun buildSymbolPanel(): View {
        val specs = KeyMapStore.loadSymbolKeys(this).map {
            DirectionalSpec(it.center, it.left, it.up, it.right, it.down)
        }

        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), symbolFlickKey(specs[0]), symbolFlickKey(specs[1]), symbolFlickKey(specs[2]), backspaceKey()),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), symbolFlickKey(specs[3]), symbolFlickKey(specs[4]), symbolFlickKey(specs[5]), controlKey("空格") { onSpacePressed() }),
            listOf(modeSwitchKey("拼音", Mode.FLICK), symbolFlickKey(specs[6]), symbolFlickKey(specs[7]), symbolFlickKey(specs[8]), primaryKey("回车") { sendEnter() }),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), symbolFlickKey(specs[9]), symbolFlickKey(specs[10]), symbolFlickKey(specs[11]), modeSwitchKey("功能", Mode.FUNC))
        )
        return panelFromRows(rows)
    }

    private fun buildFunctionPanel(): View {
        val rows = listOf(
            listOf(modeSwitchKey("☆123", Mode.NUM), controlKey("复制") { copySelection() }, controlKey("↑") { sendArrow(KeyEvent.KEYCODE_DPAD_UP) }, controlKey("粘贴") { pasteClipboard() }, backspaceKey()),
            listOf(modeSwitchKey("ABC", Mode.ALPHA), controlKey("←") { sendArrow(KeyEvent.KEYCODE_DPAD_LEFT) }, controlKey("●") {}, controlKey("→") { sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT) }, controlKey("空格") { onSpacePressed() }),
            listOf(modeSwitchKey("拼音", Mode.FLICK), controlKey("剪切") { cutSelection() }, controlKey("↓") { sendArrow(KeyEvent.KEYCODE_DPAD_DOWN) }, controlKey("全选") { selectAll() }, primaryKey("回车") { sendEnter() }),
            listOf(modeSwitchKey("符号", Mode.SYMBOL), controlKey("HOME") { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }, controlKey("剪贴板") { showClipboardPanel() }, controlKey("END") { sendKey(KeyEvent.KEYCODE_MOVE_END) }, modeSwitchKey("功能", Mode.FUNC))
        )
        return panelFromRows(rows)
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

        val globe = iconAction("🌐") { switchInputMethodQuick() }
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

    private fun iconAction(icon: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = icon
            textSize = 27f
            setTypeface(activeTypeface, Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = null
            setTextColor(colorKeyText())
            setOnClickListener { playKeyClick(); onClick() }
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
        val visual = DirectionalSpec(
            spec.center.lowercase(),
            spec.left.lowercase(),
            spec.up.lowercase(),
            spec.right.lowercase(),
            spec.down.lowercase()
        )
        val key = FrameLayout(this).apply {
            background = keyBackground(colorKeyBackground(), colorKeyBorder())
            isClickable = true
        }
        key.addView(TextView(this).apply {
            text = visual.center
            textSize = centerSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.BOLD)
            setTextColor(colorKeyText())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        })
        key.addView(TextView(this).apply {
            text = visual.left
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = visual.up
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = verticalEdgeMargin
            }
        })
        key.addView(TextView(this).apply {
            text = visual.right
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = visual.down
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = verticalEdgeMargin
            }
        })

        var startX = 0f
        var startY = 0f
        var direction = FlickDirection.Center
        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    direction = FlickDirection.Center
                    showHintOverlay(visual, v, direction, true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, true)
                    showHintOverlay(visual, v, direction, true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = when (direction) {
                        FlickDirection.Center -> visual.center
                        FlickDirection.Left -> visual.left
                        FlickDirection.Up -> visual.up
                        FlickDirection.Right -> visual.right
                        FlickDirection.Down -> visual.down
                    }
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
        val key = FrameLayout(this).apply {
            background = keyBackground(colorKeyBackground(), colorKeyBorder())
            isClickable = true
        }
        key.addView(TextView(this).apply {
            text = spec.center
            textSize = centerSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.BOLD)
            setTextColor(colorKeyText())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        })
        key.addView(TextView(this).apply {
            text = spec.left
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = spec.up
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = verticalEdgeMargin
            }
        })
        key.addView(TextView(this).apply {
            text = spec.right
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = spec.down
            textSize = sideSize
            includeFontPadding = false
            setTypeface(activeTypeface, Typeface.NORMAL)
            setTextColor(colorSubKeyText())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = verticalEdgeMargin
            }
        })

        var startX = 0f
        var startY = 0f
        var direction = FlickDirection.Center
        key.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.x
                    startY = e.y
                    direction = FlickDirection.Center
                    showHintOverlay(spec, v, direction, true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, true)
                    showHintOverlay(spec, v, direction, true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = when (direction) {
                        FlickDirection.Center -> spec.center
                        FlickDirection.Left -> spec.left
                        FlickDirection.Up -> spec.up
                        FlickDirection.Right -> spec.right
                        FlickDirection.Down -> spec.down
                    }
                    playKeyClick()
                    commitTextSafe(out)
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

    private fun textFlickKey(spec: DirectionalSpec): View = directionalKey(spec) { commitTextSafe(it) }

    private fun groupedFlickKey(label: String, spec: DirectionalSpec): View =
        directionalKey(spec, allowVertical = false, keyLabel = label) { commitAlphaChar(it) }

    private fun directionalKey(
        spec: DirectionalSpec,
        allowVertical: Boolean = true,
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
                    showHintOverlay(spec, v, direction, allowVertical)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    direction = detectDirection(e.x - startX, e.y - startY, allowVertical)
                    showHintOverlay(spec, v, direction, allowVertical)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideHintOverlay()
                    val out = when (direction) {
                        FlickDirection.Center -> spec.center
                        FlickDirection.Left -> spec.left
                        FlickDirection.Up -> spec.up
                        FlickDirection.Right -> spec.right
                        FlickDirection.Down -> spec.down
                    }
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
        key.setOnClickListener { playKeyClick(); commitTextSafe(label) }
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
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (center) 19f else 17f
            setTypeface(activeTypeface, Typeface.BOLD)
            setTextColor(colorAccentKeyText())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(if (center) colorAccentKeyBackground() else colorKeyText())
                setStroke(dp(2), colorAccentKeyText())
            }
            layoutParams = FrameLayout.LayoutParams(dp(58), dp(44))
            elevation = dp(60).toFloat()
        }
    }

    private fun showHintOverlay(spec: DirectionalSpec, key: View, direction: FlickDirection, allowVertical: Boolean) {
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

        placeHint(hintCenter, cx, cy, maxW, maxH)
        placeHint(hintLeft, cx - dist, cy, maxW, maxH)
        if (allowVertical) placeHint(hintUp, cx, cy - dist, maxW, maxH)
        placeHint(hintRight, cx + dist, cy, maxW, maxH)
        if (allowVertical) placeHint(hintDown, cx, cy + dist, maxW, maxH)

        highlightHint(hintLeft, direction == FlickDirection.Left)
        if (allowVertical) highlightHint(hintUp, direction == FlickDirection.Up)
        highlightHint(hintRight, direction == FlickDirection.Right)
        if (allowVertical) highlightHint(hintDown, direction == FlickDirection.Down)

        hintCenter.visibility = View.VISIBLE
        hintLeft.visibility = View.VISIBLE
        hintUp.visibility = if (allowVertical) View.VISIBLE else View.GONE
        hintRight.visibility = View.VISIBLE
        hintDown.visibility = if (allowVertical) View.VISIBLE else View.GONE
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
        val bg = v.background as GradientDrawable
        bg.setColor(if (selected) colorSelectedItemBackground() else colorKeyText())
    }

    private fun hideHintOverlay() {
        hintCenter.visibility = View.GONE
        hintLeft.visibility = View.GONE
        hintUp.visibility = View.GONE
        hintRight.visibility = View.GONE
        hintDown.visibility = View.GONE
    }

    private fun detectDirection(dx: Float, dy: Float, allowVertical: Boolean): FlickDirection {
        val threshold = dp(14).toFloat()
        if (abs(dx) < threshold && abs(dy) < threshold) return FlickDirection.Center
        if (!allowVertical) {
            return if (dx > threshold) FlickDirection.Right else if (dx < -threshold) FlickDirection.Left else FlickDirection.Center
        }
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) FlickDirection.Right else FlickDirection.Left
        } else {
            if (dy > 0) FlickDirection.Down else FlickDirection.Up
        }
    }

    private fun onPinyinFlick(zone: KeyZone, text: String) {
        if (isPunctuationToken(text)) {
            commitTextSafe(text)
            resetComposing()
            return
        }

        // 部分音节（如 ü/v）被放到声母区时，按韵母逻辑处理，允许首音节直接输入。
        val actualZone = if (zone == KeyZone.Shengmu && isYunmuLikeToken(text)) {
            KeyZone.Yunmu
        } else {
            zone
        }

        when (actualZone) {
            KeyZone.Shengmu -> {
                shengmuPart = text
                composingText = buildComposingDisplay()
            }
            KeyZone.Yunmu -> {
                val full = (shengmuPart ?: "") + text
                shengmuPart = null
                composedSyllables += full.lowercase()
                composingText = buildComposingDisplay()
            }
        }

        requestCandidatesAsync()
        refreshCandidateViews()
    }

    private fun isPunctuationToken(text: String): Boolean {
        return text in setOf("，", "。", "？", "！", ",", ".", "?", "!")
    }

    private fun isYunmuLikeToken(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.lowercase(Locale.getDefault())
            .replace("ü", "v")
        return t == "v" || t == "er" || t.firstOrNull() in listOf('a', 'e', 'i', 'o', 'u')
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
        composingView.text = if (composingText.isBlank()) "拼音" else composingText
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
        if (current.isEmpty()) {
            commitTextSafe(candidate.text)
            resetComposing()
            return
        }
        val consume = candidate.consumeSyllables.coerceIn(1, current.size)
        val remaining = current.drop(consume)
        val fullQuery = if (composingSessionFullQuery.isBlank()) current.joinToString("") else composingSessionFullQuery
        val fullText = composingSessionCommittedText + candidate.text

        commitTextSafe(candidate.text)

        if (remaining.isEmpty()) {
            if (fullQuery.isNotBlank() && fullText.isNotBlank()) {
                candidateExecutor.execute {
                    runCatching { pinyinEngine.recordUserChoice(fullQuery, fullText) }
                }
            }
            resetComposing()
            return
        }

        if (composingSessionFullQuery.isBlank()) {
            composingSessionFullQuery = current.joinToString("")
        }
        composingSessionCommittedText += candidate.text
        shengmuPart = null
        composedSyllables.clear()
        composedSyllables.addAll(remaining)
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
            composedSyllables.removeAt(composedSyllables.lastIndex)
            composingText = buildComposingDisplay()
            requestCandidatesAsync()
            refreshCandidateViews()
            return
        }
        withInputConnection { it.deleteSurroundingText(1, 0) }
    }

    private fun clearAllInput() {
        resetComposing()
        withInputConnection { it.deleteSurroundingText(1000, 1000) }
    }

    private fun onSpacePressed() {
        if (allCandidates.isNotEmpty()) {
            commitCandidate(allCandidates.first())
            return
        }
        val raw = buildRawPinyinForCommit()
        if (raw.isNotBlank()) {
            val fullQuery = if (composingSessionFullQuery.isBlank()) raw else composingSessionFullQuery
            val fullText = composingSessionCommittedText + raw
            commitTextSafe(raw)
            if (fullQuery.isNotBlank() && fullText.isNotBlank()) {
                candidateExecutor.execute {
                    runCatching { pinyinEngine.recordUserChoice(fullQuery, fullText) }
                }
            }
            resetComposing()
            return
        }
        commitTextSafe(" ")
    }

    private fun sendEnter() {
        val raw = buildRawPinyinForCommit()
        if (raw.isNotBlank()) {
            val fullQuery = if (composingSessionFullQuery.isBlank()) raw else composingSessionFullQuery
            val fullText = composingSessionCommittedText + raw
            commitTextSafe(raw)
            if (fullQuery.isNotBlank() && fullText.isNotBlank()) {
                candidateExecutor.execute {
                    runCatching { pinyinEngine.recordUserChoice(fullQuery, fullText) }
                }
            }
            resetComposing()
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
        if (text.isNotEmpty()) commitTextSafe(text)
    }

    private fun showClipboardPanel() {
        playKeyClick()
        captureSystemClipboard()
        refreshClipboardPanel()
        switchMode(Mode.CLIPBOARD)
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
                    commitTextSafe(item)
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

    private fun commitTextSafe(text: String) {
        withInputConnection { it.commitText(text, 1) }
    }

    private fun withInputConnection(block: (InputConnection) -> Unit) {
        val ic = currentInputConnection ?: return
        try {
            block(ic)
        } catch (_: Throwable) {
        }
    }

    private fun resetComposing() {
        candidateToken.incrementAndGet()
        shengmuPart = null
        composedSyllables.clear()
        composingText = ""
        allCandidates = emptyList()
        composingSessionFullQuery = ""
        composingSessionCommittedText = ""
        refreshCandidateViews()
    }

    private fun computeCandidates(query: String, syllables: List<String>): List<CandidateEntry> {
        if (query.isBlank()) return emptyList()
        return try {
            if (syllables.size <= 1) {
                pinyinEngine.queryCandidates(query, 48).map { CandidateEntry(it, 1) }
            } else {
                computeMultiSyllableCandidates(syllables)
            }
        } catch (_: Throwable) {
            emptyList()
        }
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

    private fun candidateItemBackground(): android.graphics.drawable.Drawable? {
        // 导入输入法背景图后，候选网格去掉实体框，避免遮挡背景。
        return if (hasImageBackgroundForUi()) null else keyBackground(colorKeyBackground(), colorKeyBorder())
    }

    private fun requestCandidatesAsync() {
        val query = buildQueryPinyin()
        val syllablesSnapshot = composedSyllables.toList()
        val token = candidateToken.incrementAndGet()
        if (query.isBlank()) {
            allCandidates = emptyList()
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

    private fun buildQueryPinyin(): String = composedSyllables.joinToString("")

    private fun buildComposingDisplay(): String {
        val base = composedSyllables.joinToString("'")
        return if (shengmuPart.isNullOrBlank()) base else if (base.isBlank()) shengmuPart!! else "$base'${shengmuPart}"
    }

    private fun buildRawPinyinForCommit(): String {
        val base = composedSyllables.joinToString("")
        return if (shengmuPart.isNullOrBlank()) base else base + shengmuPart
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float): Int = (v * resources.displayMetrics.density).toInt()

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

    private fun commitAlphaChar(ch: String) {
        if (ch.length == 1 && ch[0].isLetter()) {
            val out = if (alphaCapsLock) ch.uppercase() else ch.lowercase()
            commitTextSafe(out)
        } else {
            commitTextSafe(ch)
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
        keySizeScale = UiPrefs.getKeySizeScale(this).coerceIn(0.75f, 1.25f)
        keyGapDp = UiPrefs.getKeyGapDp(this).coerceIn(0f, 14f)
        keyboardBgImage = loadBitmap(UiPrefs.getImeBgImagePath(this))
        keyBgImage = loadBitmap(UiPrefs.getKeyBgImagePath(this))
        reloadCustomSound()
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
        val base = colorPanelBackground()
        return if (keyboardBgImage == null) base else withCustomAlpha(base, 0.08f)
    }
    private fun resolvedCandidatePanelBackground(): Int {
        return if (hasImageBackgroundForUi()) Color.TRANSPARENT else resolvedPanelBackground()
    }
    private fun hasImageBackgroundForUi(): Boolean {
        if (keyboardBgImage != null || keyBgImage != null) return true
        return UiPrefs.getImeBgImagePath(this).isNotBlank() || UiPrefs.getKeyBgImagePath(this).isNotBlank()
    }
    private fun colorKeyBackground(): Int = colorOrDefault(keyboardTheme.colors.keyBackground, "#EEF1F5")
    private fun colorKeyBorder(): Int = colorOrDefault(keyboardTheme.colors.keyBorder, "#A6AFBC")
    private fun colorKeyText(): Int = withAlpha(colorOrDefault(keyboardTheme.colors.keyText, "#111827"))
    private fun colorSubKeyText(): Int = withAlpha(colorOrDefault(keyboardTheme.colors.subKeyText, "#4B5563"))
    private fun colorAccentKeyBackground(): Int = colorOrDefault(keyboardTheme.colors.accentKeyBackground, "#1677FF")
    private fun colorAccentKeyText(): Int = withAlpha(colorOrDefault(keyboardTheme.colors.accentKeyText, "#FFFFFF"))
    private fun colorSelectedItemBackground(): Int = colorOrDefault(keyboardTheme.colors.selectedItemBackground, "#6B7280")
    private fun colorHintText(): Int = colorOrDefault(keyboardTheme.colors.hintText, "#6B7280")

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
