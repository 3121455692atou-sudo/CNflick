package com.example.flickime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.flickime.data.DefaultKeyMap
import com.example.flickime.data.KeyMapStore
import com.example.flickime.engine.PinyinEngine
import com.example.flickime.model.FlickDirection
import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.KeyZone
import java.util.Locale
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

    private lateinit var pinyinEngine: PinyinEngine
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var audioManager: AudioManager
    private var vibrator: Vibrator? = null

    private var shengmuPart: String? = null
    private val composedSyllables = mutableListOf<String>()
    private var composingText: String = ""
    private var allCandidates: List<String> = emptyList()

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
    private lateinit var hintCenter: TextView
    private lateinit var hintLeft: TextView
    private lateinit var hintUp: TextView
    private lateinit var hintRight: TextView
    private lateinit var hintDown: TextView

    private var colWidth = 0
    private var rowHeight = 0
    private var rowGap = 0
    private var alphaCapsLock = false
    private val clipboardHistory = mutableListOf<String>()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureSystemClipboard() }
    private val modeSwitchViews = mutableListOf<Pair<TextView, Mode>>()

    private enum class Mode { FLICK, ALPHA, NUM, SYMBOL, CANDIDATE, FUNC, CLIPBOARD }
    private var mode: Mode = Mode.FLICK

    override fun onCreate() {
        super.onCreate()
        pinyinEngine = PinyinEngine(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        loadClipboardHistory()
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        captureSystemClipboard()
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        initDimensions()
        modeSwitchViews.clear()

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Color.parseColor("#AEB7C5"))
            clipChildren = false
            clipToPadding = false
        }

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

    private fun initDimensions() {
        val screenWidth = resources.displayMetrics.widthPixels
        val outerPadding = dp(8)
        rowGap = dp(4)

        val usable = screenWidth - outerPadding
        colWidth = ((usable - rowGap * 4) / 5f).toInt().coerceAtLeast(dp(54))

        val centerSquare = colWidth * 3 + rowGap * 2
        rowHeight = ((centerSquare - rowGap * 3) / 4f).toInt().coerceAtLeast(dp(46))
    }

    private fun keyboardHeight(): Int = rowHeight * 5 + rowGap * 4 + dp(6)

    private fun buildCandidateStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D5DCE6"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        }

        composingView = TextView(this).apply {
            textSize = 12f
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#6B7280"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "拼音"
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroller.addView(candidateRow)

        val expand = TextView(this).apply {
            text = "˅"
            textSize = 20f
            setTextColor(Color.parseColor("#4B5563"))
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
            setBackgroundColor(Color.parseColor("#BBC4D2"))
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
            setBackgroundColor(Color.parseColor("#BBC4D2"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }
        val title = TextView(this).apply {
            text = "剪贴板历史"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
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
            setBackgroundColor(Color.parseColor("#B9C2CD"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }

        val title = TextView(this).apply {
            text = "全部候选"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val close = controlKey("返回") { switchMode(Mode.FLICK) }
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
            setBackgroundColor(Color.parseColor("#BBC4D2"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rows.forEachIndexed { index, rowViews -> panel.addView(makeFixedRow(rowViews, index != rows.lastIndex)) }
        panel.addView(buildBottomUtilityBar())
        return panel
    }

    private fun buildBottomUtilityBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                topMargin = rowGap
                gravity = Gravity.CENTER_HORIZONTAL
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val globe = iconAction("🌐") { switchInputMethodQuick() }
        val settings = iconAction("⚙") { openImeSettings() }
        val spacer1 = spacerCell()
        val spacer2 = spacerCell()
        val spacer3 = spacerCell()
        val cells = listOf(globe, spacer1, spacer2, spacer3, settings)
        cells.forEachIndexed { index, cell ->
            row.addView(cell, LinearLayout.LayoutParams(colWidth, rowHeight).apply {
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
            gravity = Gravity.CENTER
            background = null
            setTextColor(Color.parseColor("#334155"))
            setOnClickListener { playKeyClick(); onClick() }
        }
    }

    private fun makeFixedRow(cells: List<View>, addBottomGap: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                if (addBottomGap) bottomMargin = rowGap
                gravity = Gravity.CENTER_HORIZONTAL
            }
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        cells.forEachIndexed { index, cell ->
            row.addView(cell, LinearLayout.LayoutParams(colWidth, rowHeight).apply {
                if (index != cells.lastIndex) marginEnd = rowGap
            })
        }
        return row
    }

    private fun pinyinFlickKey(spec: FlickKeySpec): View {
        val visual = DirectionalSpec(
            spec.center.lowercase(),
            spec.left.lowercase(),
            spec.up.lowercase(),
            spec.right.lowercase(),
            spec.down.lowercase()
        )
        val key = FrameLayout(this).apply {
            background = keyBackground(Color.parseColor("#EEF1F5"), Color.parseColor("#A6AFBC"))
            isClickable = true
        }
        key.addView(TextView(this).apply {
            text = visual.center
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        })
        key.addView(TextView(this).apply {
            text = visual.left
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = visual.up
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(3)
            }
        })
        key.addView(TextView(this).apply {
            text = visual.right
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = visual.down
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(3)
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
        val key = FrameLayout(this).apply {
            background = keyBackground(Color.parseColor("#EEF1F5"), Color.parseColor("#A6AFBC"))
            isClickable = true
        }
        key.addView(TextView(this).apply {
            text = spec.center
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        })
        key.addView(TextView(this).apply {
            text = spec.left
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = spec.up
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(3)
            }
        })
        key.addView(TextView(this).apply {
            text = spec.right
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(4)
            }
        })
        key.addView(TextView(this).apply {
            text = spec.down
            textSize = 10f
            setTextColor(Color.parseColor("#4B5563"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(3)
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

    private fun groupedFlickKey(label: String, spec: DirectionalSpec): View {
        val key = directionalKey(spec, allowVertical = false) { commitAlphaChar(it) } as TextView
        key.text = label
        return key
    }

    private fun directionalKey(
        spec: DirectionalSpec,
        allowVertical: Boolean = true,
        commit: (String) -> Unit
    ): View {
        val key = TextView(this).apply {
            text = displayLabel(spec.center)
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#111827"))
            background = keyBackground(Color.parseColor("#EEF1F5"), Color.parseColor("#A6AFBC"))
            isClickable = true
        }

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

    private fun controlKey(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = displayLabel(label)
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#1F2937"))
            background = keyBackground(Color.parseColor("#C7D0DC"), Color.parseColor("#9AA4B2"))
            setOnClickListener { playKeyClick(); onClick() }
        }
    }

    private fun modeSwitchKey(label: String, target: Mode): View {
        val key = TextView(this).apply {
            text = displayLabel(label)
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.NORMAL)
            setOnClickListener { playKeyClick(); switchMode(target) }
        }
        modeSwitchViews += key to target
        applyModeSwitchStyle(key, selected = mode == target)
        return key
    }

    private fun applyModeSwitchStyle(v: TextView, selected: Boolean) {
        if (selected) {
            v.setTextColor(Color.WHITE)
            v.background = keyBackground(Color.parseColor("#F59E0B"), Color.parseColor("#D97706"))
        } else {
            v.setTextColor(Color.parseColor("#1F2937"))
            v.background = keyBackground(Color.parseColor("#C7D0DC"), Color.parseColor("#9AA4B2"))
        }
    }

    private fun refreshModeSwitchStyles() {
        val iterator = modeSwitchViews.iterator()
        while (iterator.hasNext()) {
            val (view, target) = iterator.next()
            if (view.parent == null) {
                iterator.remove()
            } else {
                applyModeSwitchStyle(view, mode == target)
            }
        }
    }

    private fun backspaceKey(): View {
        val key = TextView(this).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#1F2937"))
            background = keyBackground(Color.parseColor("#C7D0DC"), Color.parseColor("#9AA4B2"))
            isClickable = true
        }

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
        return TextView(this).apply {
            text = displayLabel(label)
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = keyBackground(Color.parseColor("#1677FF"), Color.parseColor("#1562CC"))
            setOnClickListener { playKeyClick(); onClick() }
        }
    }

    private fun inputKey(label: String): View {
        return TextView(this).apply {
            text = displayLabel(label)
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#111827"))
            background = keyBackground(Color.parseColor("#EEF1F5"), Color.parseColor("#A6AFBC"))
            setOnClickListener { playKeyClick(); commitTextSafe(label) }
        }
    }

    private fun keyBackground(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(lighten(fill), fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun makeHintBubble(text: String, center: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (center) 19f else 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(if (center) Color.parseColor("#2563EB") else Color.parseColor("#111827"))
                setStroke(dp(2), Color.WHITE)
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
        bg.setColor(if (selected) Color.parseColor("#6B7280") else Color.parseColor("#111827"))
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
                val query = buildQueryPinyin()
                if (query.isNotBlank()) {
                    allCandidates = try {
                        if (composedSyllables.size > 1) pinyinEngine.queryCandidatesForSyllables(composedSyllables, 48)
                        else pinyinEngine.queryCandidates(query, 48)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                } else {
                    allCandidates = emptyList()
                }
            }
            KeyZone.Yunmu -> {
                val full = (shengmuPart ?: "") + text
                shengmuPart = null
                composedSyllables += full.lowercase()
                composingText = buildComposingDisplay()
                val query = buildQueryPinyin()
                allCandidates = try {
                    if (composedSyllables.size > 1) pinyinEngine.queryCandidatesForSyllables(composedSyllables, 48)
                    else pinyinEngine.queryCandidates(query, 48)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        }

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
        composingView.setTextColor(if (composingText.isBlank()) Color.parseColor("#6B7280") else Color.parseColor("#1D4ED8"))

        candidateRow.removeAllViews()
        allCandidates.take(12).forEach { c ->
            candidateRow.addView(TextView(this).apply {
                text = c
                textSize = 18f
                setTextColor(Color.parseColor("#111827"))
                setPadding(dp(7), dp(1), dp(7), dp(1))
                setOnClickListener {
                    commitCandidate(c)
                    if (mode == Mode.CANDIDATE) switchMode(Mode.FLICK)
                }
            })
        }
    }

    private fun refreshCandidateGrid() {
        candidateGrid.removeAllViews()
        allCandidates.take(48).forEach { c ->
            val item = TextView(this).apply {
                text = c
                textSize = 24f
                gravity = Gravity.CENTER
                minHeight = dp(44)
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setTextColor(Color.parseColor("#111827"))
                background = keyBackground(Color.parseColor("#EEF1F5"), Color.parseColor("#A6AFBC"))
                setOnClickListener {
                    commitCandidate(c)
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

    private fun commitCandidate(text: String) {
        val query = buildQueryPinyin()
        if (query.isNotBlank()) {
            pinyinEngine.recordUserChoice(query, text)
        }
        commitTextSafe(text)
        resetComposing()
    }

    private fun backspace() {
        if (shengmuPart != null) {
            shengmuPart = null
            composingText = buildComposingDisplay()
            val query = buildQueryPinyin()
            allCandidates = if (query.isNotBlank()) {
                try {
                    if (composedSyllables.size > 1) pinyinEngine.queryCandidatesForSyllables(composedSyllables, 48)
                    else pinyinEngine.queryCandidates(query, 48)
                } catch (_: Throwable) { emptyList() }
            } else {
                emptyList()
            }
            refreshCandidateViews()
            return
        }
        if (composedSyllables.isNotEmpty()) {
            composedSyllables.removeAt(composedSyllables.lastIndex)
            composingText = buildComposingDisplay()
            val query = buildQueryPinyin()
            allCandidates = if (query.isNotBlank()) {
                try {
                    if (composedSyllables.size > 1) pinyinEngine.queryCandidatesForSyllables(composedSyllables, 48)
                    else pinyinEngine.queryCandidates(query, 48)
                } catch (_: Throwable) { emptyList() }
            } else {
                emptyList()
            }
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
            commitTextSafe(raw)
            resetComposing()
            return
        }
        commitTextSafe(" ")
    }

    private fun sendEnter() {
        val raw = buildRawPinyinForCommit()
        if (raw.isNotBlank()) {
            commitTextSafe(raw)
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
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 14f
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
                setTextColor(Color.parseColor("#111827"))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = keyBackground(Color.parseColor("#EEF1F5"), Color.parseColor("#A6AFBC"))
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
        shengmuPart = null
        composedSyllables.clear()
        composingText = ""
        allCandidates = emptyList()
        refreshCandidateViews()
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

    private fun displayLabel(text: String): String {
        val hasAsciiLetter = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        return if (hasAsciiLetter) text.uppercase() else text
    }

    private fun playKeyClick() {
        try {
            if (isSoundEnabled()) {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.45f)
            }
            if (isVibrationEnabled()) {
                val vib = vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(8L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(8L)
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

    private fun isSoundEnabled(): Boolean {
        return getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean("sound_enabled", true)
    }

    private fun isVibrationEnabled(): Boolean {
        return getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean("vibration_enabled", false)
    }
}
