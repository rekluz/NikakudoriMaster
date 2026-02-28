package com.example.ricosshisen_sho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.ricosshisen_sho.ui.theme.RicosShisenShoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var game: ShisenShoGame

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        game = ShisenShoGame(initialRows = 8, initialCols = 17, context = this)
        game.bgThemeColor = Color(0xFF002147)

        enableEdgeToEdge()
        setContent {
            RicosShisenShoTheme {
                val currentGame = remember { game }
                var isVisible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(150)
                    isVisible = true
                }

                DisposableEffect(Unit) { onDispose { currentGame.releaseSounds() } }

                LaunchedEffect(currentGame.gameState) {
                    while (isActive && currentGame.gameState == GameState.PLAYING) {
                        delay(1000)
                        currentGame.timeSeconds++
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = currentGame.bgThemeColor
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isVisible) {
                            ShisenShoScreen(game = currentGame, onExit = { finish() })
                        }
                        if (!isVisible) {
                            Box(modifier = Modifier.fillMaxSize().background(currentGame.bgThemeColor))
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::game.isInitialized && game.gameState == GameState.PLAYING) {
            game.gameState = GameState.PAUSED
        }
    }
}

@Composable
fun ShisenShoScreen(game: ShisenShoGame, onExit: () -> Unit) {
    val isPaused = game.gameState == GameState.PAUSED
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var matchingTiles by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }

    Row(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                .onGloballyPositioned { containerSize = it.size }
        ) {
            if (containerSize.width > 0) {
                val widthPx = containerSize.width.toFloat()
                val heightPx = containerSize.height.toFloat()
                val widthDp = with(density) { widthPx.toDp() }
                val heightDp = with(density) { heightPx.toDp() }

                val scaledWidthDp = widthDp * game.boardWidthScale
                val slotWidthDp = scaledWidthDp / game.cols
                val hOverlap = 0.96f
                val tileWidth = slotWidthDp * 1.15f

                val verticalCompression = 0.92f
                val slotHeightDp = (heightDp / game.rows) * verticalCompression
                val vStretch = 1.25f
                val tileHeight = slotHeightDp * vStretch

                // Calculate vertical offset to center the grid within the height
                val totalGridHeight = slotHeightDp * (game.rows - 1) + tileHeight
                val verticalOffset = (heightDp - totalGridHeight) / 2f

                val boardOffsetX = if (game.boardWidthScale < 1f) (widthDp - scaledWidthDp) / 2f else 0.dp

                Box(modifier = Modifier.fillMaxSize().offset(x = boardOffsetX, y = verticalOffset)) {
                    for (r in 0 until game.rows) {
                        for (c in 0 until game.cols) {
                            val tile = game.board[r][c]
                            val isMatching = matchingTiles.contains(r to c)

                            TileView(
                                tile = tile,
                                modifier = Modifier
                                    .size(tileWidth, tileHeight)
                                    .offset(x = (slotWidthDp * hOverlap * c), y = (slotHeightDp * r))
                                    .zIndex(if (isMatching) 101f else r.toFloat() + (c.toFloat() * 0.01f)),
                                isPaused = isPaused,
                                isMatching = isMatching,
                                onClick = {
                                    val prevSelected = game.selectedTile
                                    game.onTileClick(r, c, view)

                                    if (game.lastPath != null && prevSelected != null) {
                                        val pair = setOf(prevSelected, r to c)
                                        coroutineScope.launch {
                                            matchingTiles = matchingTiles + pair
                                            delay(500)
                                            matchingTiles = matchingTiles - pair
                                        }
                                    }
                                }
                            )
                        }
                    }

                    game.lastPath?.let { path ->
                        val slotWidthPx = (widthPx * game.boardWidthScale) / game.cols
                        val slotHeightPx = (heightPx / game.rows) * verticalCompression
                        Canvas(modifier = Modifier.fillMaxSize().zIndex(100f)) {
                            val points = path.map { (r, c) ->
                                Offset(
                                    x = (slotWidthPx * hOverlap * c) + (with(density) { tileWidth.toPx() } * 0.45f),
                                    y = (slotHeightPx * r) + (with(density) { tileHeight.toPx() } * 0.45f)
                                )
                            }
                            for (i in 0 until points.size - 1) {
                                drawLine(color = Color.Yellow, start = points[i], end = points[i + 1], strokeWidth = 8f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.width(120.dp).fillMaxHeight().padding(vertical = 12.dp, horizontal = 4.dp)
                .background(Color(0x66000000), RoundedCornerShape(16.dp)).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
        ) {
            TimerPill(game.formatTime())
            MenuPillButton("MENU") { game.gameState = GameState.PAUSED }
            val hintAvailable = game.isHintAvailable
            val hintLabel = if (hintAvailable) "HINT" else "HINT (${game.hintSecondsRemaining})"
            MenuPillButton(hintLabel, enabled = hintAvailable) { game.showHint() }
            MenuPillButton("SHUFFLE") { game.shuffleBoard() }
            MenuPillButton("OPTIONS") { game.gameState = GameState.OPTIONS }
            MenuPillButton("ABOUT") { game.gameState = GameState.ABOUT }
        }
    }

    when (game.gameState) {
        GameState.PAUSED -> PauseDialog(onResume = { game.gameState = GameState.PLAYING }, onRetry = { game.initializeBoard() }, onNewGame = { game.initializeBoard() }, onExit = onExit)
        GameState.OPTIONS -> OptionsDialog(game = game, onDone = { game.gameState = GameState.PLAYING })
        GameState.SCORE -> ScoreDialog(game = game)
        GameState.WON -> WinDialog(game = game)
        GameState.NO_MOVES -> NoMovesDialog(onShuffle = { game.shuffleBoard() }, onNewGame = { game.initializeBoard() })
        GameState.ABOUT -> AboutDialog(onDismiss = { game.gameState = GameState.PLAYING })
        else -> {}
    }
}

@Composable
fun TileView(tile: Tile, modifier: Modifier, isPaused: Boolean, isMatching: Boolean, onClick: () -> Unit) {
    if (tile.isRemoved && !isMatching) return
    val context = LocalContext.current
    val tileImageId = remember(tile.imageName) { context.resources.getIdentifier(tile.imageName, "drawable", context.packageName) }
    val backImageId = remember { context.resources.getIdentifier("tile_back", "drawable", context.packageName) }
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(250, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )
    Box(modifier = modifier.alpha(if (isMatching) alpha else 1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }) {
        val imageId = if (isPaused) backImageId else tileImageId
        if (imageId != 0) {
            Image(painter = painterResource(id = imageId), contentDescription = "Game Tile", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            if (!isPaused && (tile.isSelected || tile.isHint)) {
                val highlightColor = if (tile.isSelected) Color(0x6600BFFF) else Color(0x44FFFF00)
                Box(modifier = Modifier.fillMaxSize().padding(end = 4.dp, bottom = 6.dp).background(highlightColor, RoundedCornerShape(4.dp)).border(if (tile.isHint) 2.dp else 0.dp, Color.Yellow, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
fun ScoreDialog(game: ShisenShoGame) {
    var selectedSize by remember { mutableStateOf(game.rows to game.cols to game.boardMode) }
    val topScores = game.getTopScores(selectedSize.first.first, selectedSize.first.second, selectedSize.second)
    val sizes = listOf(Triple(5, 14, "standard"), Triple(7, 16, "standard"), Triple(8, 17, "standard"), Triple(8, 17, "custom"))
    OverlayContainer {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HALL OF FAME", color = Color.Yellow, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                sizes.forEach { (r, c, mode) ->
                    val isSelected = selectedSize == (r to c to mode)
                    Text(text = game.getDifficultyLabel(r, c, mode), color = if (isSelected) Color.Yellow else Color.Gray, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { selectedSize = r to c to mode }.padding(4.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().background(Color(0x33FFFFFF)).padding(4.dp)) {
                Text("RANK", Modifier.weight(0.2f), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("PLAYER", Modifier.weight(0.5f), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("TIME", Modifier.weight(0.3f), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(10) { index ->
                    val score = topScores.getOrNull(index)
                    Row(modifier = Modifier.fillMaxWidth().background(if (index % 2 == 0) Color.Transparent else Color(0x11FFFFFF)).padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", Modifier.weight(0.2f), color = if (index < 3) Color.Yellow else Color.White, fontWeight = FontWeight.Bold)
                        Text(score?.first ?: "---", Modifier.weight(0.5f), color = Color.White, fontSize = 14.sp)
                        Text(score?.second ?: "--:--", Modifier.weight(0.3f), color = if (score != null) Color.Cyan else Color.DarkGray, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DialogButton("Clear", { game.clearScores(selectedSize.first.first, selectedSize.first.second, selectedSize.second) }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton("Done", { game.gameState = GameState.PLAYING }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun WinDialog(game: ShisenShoGame) {
    var name by remember { mutableStateOf("") }
    OverlayContainer {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("YOU WON!", color = Color.Yellow, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Time: ${game.formatTime()}", color = Color.White, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("ENTER INITIALS", color = Color.Gray, fontSize = 12.sp)
            Box(modifier = Modifier.width(120.dp).padding(8.dp).background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)).border(1.dp, Color.Yellow, RoundedCornerShape(4.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                BasicTextField(value = name, onValueChange = { if (it.length <= 3) name = it.uppercase() }, textStyle = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 4.sp), singleLine = true, cursorBrush = SolidColor(Color.Yellow), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                if (name.isEmpty()) Text("___", color = Color(0x66FFFFFF), fontSize = 24.sp, letterSpacing = 4.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            DialogButton("Save & View Scores", onClick = {
                game.saveScore(if (name.isBlank()) "???" else name, game.timeSeconds)
                game.gameState = GameState.SCORE
            })
        }
    }
}

@Composable
fun NoMovesDialog(onShuffle: () -> Unit, onNewGame: () -> Unit) {
    OverlayContainer {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NO MORE MOVES!", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            DialogButton("Shuffle Board", onShuffle)
            DialogButton("Start New Game", onNewGame)
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { "1.1" } }
    OverlayContainer {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("ABOUT", color = Color.Yellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.app_name), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = "Version $versionName", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.about_description), color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(id = R.string.about_developer), color = Color.Yellow, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(24.dp))
            DialogButton("Close", onDismiss)
        }
    }
}

@Composable
fun TimerPill(time: String) {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = "TIME", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(text = time, color = Color.Yellow, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MenuPillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.98f else 1f, label = "pillScale")
    val bgColor = if (!enabled) Color(0x11FFFFFF) else if (isPressed) Color(0x66444444) else Color(0x33FFFFFF)
    val textColor = if (!enabled) Color.Gray else Color.White
    val borderColor = if (!enabled) Color(0x22FFFFFF) else Color(0x66FFFFFF)
    Box(modifier = Modifier.fillMaxWidth().height(36.dp).scale(scale).clip(CircleShape).background(bgColor).border(1.dp, borderColor, CircleShape).clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text = label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
fun PauseDialog(onResume: () -> Unit, onRetry: () -> Unit, onNewGame: () -> Unit, onExit: () -> Unit) {
    OverlayContainer {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Menu", color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
            DialogButton("New Game", onNewGame)
            DialogButton("Retry Game", onRetry)
            DialogButton("Resume", onResume)
            DialogButton("Exit", onExit)
        }
    }
}

@Composable
fun OptionsDialog(game: ShisenShoGame, onDone: () -> Unit) {
    OverlayContainer {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Text("Options", color = Color.White, fontSize = 24.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SettingsRow(label = "Sound", isOn = game.isSoundEnabled, onToggle = { game.toggleSound(it) })
                GridSizeToggle(game)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Background Color", color = Color.White, fontSize = 14.sp)
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorOptionButton("Blue", Color(0xFF002147), game)
                    ColorOptionButton("Green", Color(0xFF004D00), game)
                    ColorOptionButton("Red", Color(0xFF4D0000), game)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("High Scores", { game.gameState = GameState.SCORE }, modifier = Modifier.weight(1f))
                DialogButton("Done", onDone, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GridSizeToggle(game: ShisenShoGame) {
    val sizes = listOf(Triple(5, 14, "standard"), Triple(7, 16, "standard"), Triple(8, 17, "standard"), Triple(8, 17, "custom"))
    val currentIndex = sizes.indexOfFirst { it.first == game.rows && it.second == game.cols && it.third == game.boardMode }.coerceAtLeast(0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Board Type", color = Color.White, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = Color.Yellow, fontSize = 32.sp, modifier = Modifier.padding(8.dp).clickable {
                val next = if (currentIndex == 0) sizes.size - 1 else currentIndex - 1
                val (rows, cols, mode) = sizes[next]
                game.updateGridSize(rows, cols, mode)
                game.gameState = GameState.OPTIONS
            })
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
                Text(game.getDifficultyLabel(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${game.cols}x${game.rows}", color = Color.Gray, fontSize = 11.sp)
            }
            Text(">", color = Color.Yellow, fontSize = 32.sp, modifier = Modifier.padding(8.dp).clickable {
                val next = (currentIndex + 1) % sizes.size
                val (rows, cols, mode) = sizes[next]
                game.updateGridSize(rows, cols, mode)
                game.gameState = GameState.OPTIONS
            })
        }
    }
}

@Composable
fun SettingsRow(label: String, isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onToggle(true) }, colors = ButtonDefaults.buttonColors(containerColor = if (isOn) Color(0xFF00BFFF) else Color.Gray)) { Text("On", color = Color.White) }
            Button(onClick = { onToggle(false) }, colors = ButtonDefaults.buttonColors(containerColor = if (!isOn) Color(0xFF00BFFF) else Color.Gray)) { Text("Off", color = Color.White) }
        }
    }
}

@Composable
fun ColorOptionButton(label: String, color: Color, game: ShisenShoGame) {
    val isSelected = game.bgThemeColor == color
    Button(onClick = { game.bgThemeColor = color }, colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color(0xFF00BFFF) else Color.Gray)) { Text(label, color = Color.White) }
}

@Composable
fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(0.8f)) {
    Button(onClick = onClick, modifier = modifier.padding(vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFFF))) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
fun OverlayContainer(content: @Composable () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.size(480.dp, 350.dp).background(Color(0xCC222222), RoundedCornerShape(8.dp)).border(2.dp, Color.Gray).padding(16.dp), contentAlignment = Alignment.Center) { content() }
    }
}