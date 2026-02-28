package com.example.ricosshisen_sho

import android.content.Context
import android.content.SharedPreferences
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import kotlinx.coroutines.*

data class Tile(
    val type: Int,
    val isSelected: Boolean = false,
    val isRemoved: Boolean = false,
    val imageName: String = "",
    val isHint: Boolean = false
)

enum class GameState {
    PLAYING, PAUSED, OPTIONS, SCORE, WON, NO_MOVES, ABOUT
}

class ShisenShoGame(initialRows: Int, initialCols: Int, val context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .build()

    private val clickSoundId = loadSoundResource("tile_click")
    private val errorSoundId = loadSoundResource("tile_error")
    private val matchSoundId = loadSoundResource("tile_match")
    private val victorySoundId = loadSoundResource("tile_tada")

    private fun loadSoundResource(name: String): Int {
        return try {
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) soundPool.load(context, resId, 1) else 0
        } catch (e: Exception) { 0 }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("ShisenShoPrefs", Context.MODE_PRIVATE)

    var rows by mutableStateOf(prefs.getInt("grid_rows", initialRows))
    var cols by mutableStateOf(prefs.getInt("grid_cols", initialCols))
    var boardMode by mutableStateOf(prefs.getString("board_mode", "standard") ?: "standard")
    var boardWidthScale by mutableStateOf(1f)

    var board by mutableStateOf(List(rows) { List(cols) { Tile(0) } })
    var selectedTile by mutableStateOf<Pair<Int, Int>?>(null)
    var gameState by mutableStateOf(GameState.PLAYING)
    var timeSeconds by mutableLongStateOf(0L)

    val hintCooldownSeconds = 30L
    var lastHintTime by mutableLongStateOf(-hintCooldownSeconds)

    val isHintAvailable: Boolean
        get() = timeSeconds >= lastHintTime + hintCooldownSeconds

    val hintSecondsRemaining: Long
        get() = ((lastHintTime + hintCooldownSeconds) - timeSeconds).coerceAtLeast(0L)

    var lastPath by mutableStateOf<List<Pair<Int, Int>>?>(null)
    private var pathClearJob: Job? = null
    private val gameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isSoundEnabled by mutableStateOf(prefs.getBoolean("sound_enabled", true))
        private set

    fun toggleSound(enabled: Boolean) {
        isSoundEnabled = enabled
        prefs.edit { putBoolean("sound_enabled", enabled) }
    }

    var bgThemeColor by mutableStateOf(Color(0xFF004D00))

    private val tileTypes = listOf(
        "tile_dot_1", "tile_dot_2", "tile_dot_3", "tile_dot_4", "tile_dot_5", "tile_dot_6", "tile_dot_7", "tile_dot_8", "tile_dot_9",
        "tile_bamboo_1", "tile_bamboo_2", "tile_bamboo_3", "tile_bamboo_4", "tile_bamboo_5", "tile_bamboo_6", "tile_bamboo_7", "tile_bamboo_8", "tile_bamboo_9",
        "tile_char_1", "tile_char_2", "tile_char_3", "tile_char_4", "tile_char_5", "tile_char_6", "tile_char_7", "tile_char_8", "tile_char_9",
        "tile_wind_e", "tile_wind_s", "tile_wind_w", "tile_wind_n",
        "tile_drag_r", "tile_drag_g", "tile_drag_b"
    )

    init {
        initializeBoard()
    }

    fun getDifficultyLabel(r: Int = rows, c: Int = cols, mode: String = boardMode): String {
        return when {
            mode == "custom" -> "Custom"
            r <= 5 -> "Easy"
            r <= 7 -> "Normal"
            else -> "Hard"
        }
    }

    fun updateGridSize(newRows: Int, newCols: Int, mode: String = "standard") {
        // Validate even tile count (required for Shisen-Sho pairs)
        require((newRows * newCols) % 2 == 0) {
            "Board must contain an even number of tiles. ${newRows}x${newCols} = ${newRows * newCols} tiles (odd)"
        }

        rows = newRows
        cols = newCols
        boardMode = mode
        boardWidthScale = if (mode == "custom") 0.75f else 1f
        prefs.edit {
            putInt("grid_rows", newRows)
            putInt("grid_cols", newCols)
            putString("board_mode", mode)
        }
        initializeBoard()
    }

    private fun playSound(soundId: Int) {
        if (isSoundEnabled && soundId > 0) {
            try {
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            } catch (e: Exception) { }
        }
    }

    fun releaseSounds() {
        pathClearJob?.cancel()
        gameScope.cancel()
        soundPool.release()
    }

    fun initializeBoard() {
        val totalTiles = rows * cols

        // Ensure even tile count (required for pairs in Shisen-Sho)
        check(totalTiles % 2 == 0) {
            "Game board must have an even number of tiles. Current: ${rows}x${cols} = $totalTiles (odd)"
        }

        val tilesList = mutableListOf<Tile>()
        var typeIndex = 0

        // Generate tiles in groups of 4 (2 pairs of the same type)
        while (tilesList.size < totalTiles) {
            val name = tileTypes[typeIndex % tileTypes.size]
            // Add up to 4 tiles of this type
            repeat(4) {
                if (tilesList.size < totalTiles) {
                    tilesList.add(Tile(type = typeIndex % tileTypes.size, imageName = name))
                }
            }
            typeIndex++
        }

        // Verify we have the correct number of tiles
        check(tilesList.size == totalTiles) {
            "Tile count mismatch: expected $totalTiles, got ${tilesList.size}"
        }

        // Verify even count of each tile type
        val typeCounts = tilesList.groupingBy { it.type }.eachCount()
        typeCounts.forEach { (type, count) ->
            check(count % 2 == 0) {
                "Tile type $type has odd count: $count. All types must appear an even number of times."
            }
        }

        tilesList.shuffle()
        board = List(rows) { r -> List(cols) { c -> tilesList[r * cols + c] } }
        selectedTile = null
        timeSeconds = 0
        lastHintTime = -hintCooldownSeconds
        gameState = GameState.PLAYING
        lastPath = null
    }

    fun onTileClick(row: Int, col: Int, view: android.view.View) {
        if (gameState != GameState.PLAYING) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)

        val tile = board[row][col]
        if (tile.isRemoved) return

        val currentSelection = selectedTile
        if (currentSelection == null) {
            playSound(clickSoundId)
            updateTile(row, col, tile.copy(isSelected = true))
            selectedTile = row to col
        } else {
            val (r1, c1) = currentSelection
            if (r1 == row && c1 == col) {
                updateTile(row, col, tile.copy(isSelected = false))
                selectedTile = null
            } else {
                val tile1 = board[r1][c1]
                val connectionPath = if (tile1.imageName == tile.imageName) findConnectionPath(r1, c1, row, col) else null

                if (connectionPath != null) {
                    playSound(matchSoundId)
                    lastPath = connectionPath
                    updateTile(r1, c1, tile1.copy(isRemoved = true, isSelected = false))
                    updateTile(row, col, tile.copy(isRemoved = true, isSelected = false))
                    selectedTile = null
                    clearHints()

                    if (!checkWinCondition()) {
                        checkForDeadlock()
                    }

                    pathClearJob?.cancel()
                    pathClearJob = gameScope.launch {
                        delay(400)
                        lastPath = null
                    }
                } else {
                    playSound(errorSoundId)
                    updateTile(r1, c1, tile1.copy(isSelected = false))
                    updateTile(row, col, tile.copy(isSelected = true))
                    selectedTile = row to col
                }
            }
        }
    }

    private fun checkForDeadlock() {
        var hasMove = false
        outer@for (r1 in 0 until rows) {
            for (c1 in 0 until cols) {
                if (board[r1][c1].isRemoved) continue
                for (r2 in 0 until rows) {
                    for (c2 in 0 until cols) {
                        if ((r1 == r2 && c1 == c2) || board[r2][c2].isRemoved) continue
                        if (board[r1][c1].imageName == board[r2][c2].imageName && findConnectionPath(r1, c1, r2, c2) != null) {
                            hasMove = true
                            break@outer
                        }
                    }
                }
            }
        }
        if (!hasMove) {
            gameState = GameState.NO_MOVES
        }
    }

    fun shuffleBoard() {
        val remaining = board.flatten()
            .filter { !it.isRemoved }
            .map { it.copy(isSelected = false, isHint = false) }
            .shuffled()
        var index = 0
        board = List(rows) { r ->
            List(cols) { c ->
                if (!board[r][c].isRemoved && index < remaining.size) {
                    remaining[index++]
                } else {
                    board[r][c]
                }
            }
        }
        selectedTile = null
        lastPath = null
        gameState = GameState.PLAYING
        checkForDeadlock()
    }

    fun showHint() {
        if (!isHintAvailable) return

        clearHints()
        val moves = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        for (r1 in 0 until rows) {
            for (c1 in 0 until cols) {
                if (board[r1][c1].isRemoved) continue
                for (r2 in 0 until rows) {
                    for (c2 in 0 until cols) {
                        if ((r1 == r2 && c1 == c2) || board[r2][c2].isRemoved) continue
                        if (board[r1][c1].imageName == board[r2][c2].imageName && findConnectionPath(r1, c1, r2, c2) != null) {
                            moves.add((r1 to c1) to (r2 to c2))
                        }
                    }
                }
            }
        }
        if (moves.isNotEmpty()) {
            val hint = moves.random()
            updateTile(hint.first.first, hint.first.second, board[hint.first.first][hint.first.second].copy(isHint = true))
            updateTile(hint.second.first, hint.second.second, board[hint.second.first][hint.second.second].copy(isHint = true))

            lastHintTime = timeSeconds
        }
    }

    private fun clearHints() {
        board = board.map { row -> row.map { it.copy(isHint = false) } }
    }

    private fun checkWinCondition(): Boolean {
        if (board.all { row -> row.all { it.isRemoved } }) {
            playSound(victorySoundId)
            gameState = GameState.WON
            return true
        }
        return false
    }

    fun saveScore(name: String, time: Long) {
        val key = "game_scores_${rows}_${cols}_${boardMode}"
        val savedString = prefs.getString(key, "") ?: ""
        val scoreList = if (savedString.isEmpty()) mutableListOf<Pair<String, Long>>()
        else savedString.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
        }.toMutableList()

        scoreList.add(name.uppercase().take(3) to time)
        val topTen = scoreList.sortedBy { it.second }.take(10)

        val resultString = topTen.joinToString(",") { "${it.first}:${it.second}" }
        prefs.edit { putString(key, resultString) }
    }

    fun getTopScores(r: Int, c: Int, mode: String = boardMode): List<Pair<String, String>> {
        val key = "game_scores_${r}_${c}_${mode}"
        val savedString = prefs.getString(key, "") ?: ""
        return if (savedString.isEmpty()) emptyList()
        else savedString.split(",")
            .mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) parts[0] to formatGivenTime(parts[1].toLongOrNull() ?: 0L) else null
            }
    }

    fun clearScores(r: Int, c: Int, mode: String = boardMode) {
        val key = "game_scores_${r}_${c}_${mode}"
        prefs.edit { remove(key) }
    }

    private fun updateTile(row: Int, col: Int, newTile: Tile) {
        board = board.mapIndexed { r, list -> if (r == row) list.toMutableList().apply { this[col] = newTile } else list }
    }

    private fun isWalkable(r: Int, c: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return true
        return board[r][c].isRemoved
    }

    private fun findConnectionPath(r1: Int, c1: Int, r2: Int, c2: Int): List<Pair<Int, Int>>? {
        if (isLineEmpty(r1, c1, r2, c2)) return listOf(r1 to c1, r2 to c2)

        for (r in -1..rows) {
            if (isWalkable(r, c1) && isWalkable(r, c2)) {
                if (isLineEmpty(r1, c1, r, c1) && isLineEmpty(r, c1, r, c2) && isLineEmpty(r, c2, r2, c2)) {
                    return listOf(r1 to c1, r to c1, r to c2, r2 to c2)
                }
            }
        }

        for (c in -1..cols) {
            if (isWalkable(r1, c) && isWalkable(r2, c)) {
                if (isLineEmpty(r1, c1, r1, c) && isLineEmpty(r1, c, r2, c) && isLineEmpty(r2, c, r2, c2)) {
                    return listOf(r1 to c1, r1 to c, r2 to c, r2 to c2)
                }
            }
        }
        return null
    }

    private fun isLineEmpty(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (r1 == r2) {
            val (s, e) = if (c1 < c2) c1 to c2 else c2 to c1
            for (c in s + 1 until e) if (!isWalkable(r1, c)) return false
            return true
        } else if (c1 == c2) {
            val (s, e) = if (r1 < r2) r1 to r2 else r2 to r1
            for (r in s + 1 until e) if (!isWalkable(r, c1)) return false
            return true
        }
        return false
    }

    fun formatTime(): String = formatGivenTime(timeSeconds)
    private fun formatGivenTime(s: Long): String = "%02d:%02d".format(s / 60, s % 60)
}