package me.semoro.spygame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import spygame.composeapp.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadWordSets(): Map<String, List<String>> {
    val bytes = Res.readBytes("files/words.json")
    val jsonObject = Json.parseToJsonElement(bytes.decodeToString()) as JsonObject
    return jsonObject.mapValues { (_, value) ->
        value.jsonArray.map { it.jsonPrimitive.content }
    }
}

private enum class GameScreen {
    SETUP, PLAYER_READY, PLAYER_REVEAL, GAME_ACTIVE
}

private enum class PlayerRole {
    NORMAL, SPY, DOUBLE_AGENT, FOOL
}

@Composable
fun App() {
    MaterialTheme {
        var wordSets by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

        LaunchedEffect(Unit) {
            wordSets = loadWordSets()
        }

        var screen by remember { mutableStateOf(GameScreen.SETUP) }
        var playerCount by remember { mutableStateOf(4) }
        var spyCount by remember { mutableStateOf(1) }
        var selectedCategories by remember { mutableStateOf(setOf<String>()) }
        var randomWords by remember { mutableStateOf(false) }
        var doubleAgentEnabled by remember { mutableStateOf(false) }
        var foolEnabled by remember { mutableStateOf(false) }
        var currentPlayerIndex by remember { mutableStateOf(0) }
        var playerRoles by remember { mutableStateOf(mapOf<Int, PlayerRole>()) }
        var playerWordMap by remember { mutableStateOf(mapOf<Int, String>()) }
        var mainWord by remember { mutableStateOf("") }

        // Select all categories once words are loaded
        if (selectedCategories.isEmpty() && wordSets.isNotEmpty()) {
            selectedCategories = wordSets.keys.toSet()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (wordSets.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (screen) {
                    GameScreen.SETUP -> SetupScreen(
                        wordSets = wordSets,
                        playerCount = playerCount,
                        spyCount = spyCount,
                        selectedCategories = selectedCategories,
                        randomWords = randomWords,
                        doubleAgentEnabled = doubleAgentEnabled,
                        foolEnabled = foolEnabled,
                        onPlayerCountChange = {
                            playerCount = it
                            spyCount = spyCount.coerceAtMost(it)
                        },
                        onSpyCountChange = { spyCount = it.coerceIn(0, playerCount) },
                        onToggleCategory = { category ->
                            selectedCategories = if (category in selectedCategories) {
                                if (selectedCategories.size > 1) selectedCategories - category
                                else selectedCategories
                            } else {
                                selectedCategories + category
                            }
                        },
                        onRandomWordsChange = { randomWords = it },
                        onDoubleAgentEnabledChange = { doubleAgentEnabled = it },
                        onFoolEnabledChange = { foolEnabled = it },
                        onStartGame = {
                            val words = selectedCategories.flatMap { wordSets[it].orEmpty() }
                            val shuffled = (0 until playerCount).toList().shuffled()
                            val roles = mutableMapOf<Int, PlayerRole>()
                            var assignIdx = 0
                            repeat(spyCount) {
                                if (assignIdx < shuffled.size) {
                                    roles[shuffled[assignIdx]] = PlayerRole.SPY
                                    assignIdx++
                                }
                            }
                            if (doubleAgentEnabled && assignIdx < shuffled.size) {
                                roles[shuffled[assignIdx]] = PlayerRole.DOUBLE_AGENT
                                assignIdx++
                            }
                            if (foolEnabled && assignIdx < shuffled.size) {
                                roles[shuffled[assignIdx]] = PlayerRole.FOOL
                                assignIdx++
                            }
                            while (assignIdx < shuffled.size) {
                                roles[shuffled[assignIdx]] = PlayerRole.NORMAL
                                assignIdx++
                            }
                            playerRoles = roles

                            val selectedWord = words.random()
                            mainWord = selectedWord
                            playerWordMap = (0 until playerCount).associateWith { idx ->
                                when (roles[idx]) {
                                    PlayerRole.SPY -> ""
                                    PlayerRole.DOUBLE_AGENT -> selectedWord
                                    PlayerRole.FOOL -> {
                                        val otherWords = words.filter { it != selectedWord }
                                        if (otherWords.isNotEmpty()) otherWords.random() else selectedWord
                                    }
                                    else -> if (randomWords) words.random() else selectedWord
                                }
                            }
                            currentPlayerIndex = 0
                            screen = GameScreen.PLAYER_READY
                        }
                    )

                    GameScreen.PLAYER_READY -> PlayerReadyScreen(
                        playerNumber = currentPlayerIndex + 1,
                        onReveal = { screen = GameScreen.PLAYER_REVEAL }
                    )

                    GameScreen.PLAYER_REVEAL -> PlayerRevealScreen(
                        role = playerRoles[currentPlayerIndex] ?: PlayerRole.NORMAL,
                        word = playerWordMap[currentPlayerIndex].orEmpty(),
                        onNext = {
                            if (currentPlayerIndex + 1 < playerCount) {
                                currentPlayerIndex++
                                screen = GameScreen.PLAYER_READY
                            } else {
                                screen = GameScreen.GAME_ACTIVE
                            }
                        }
                    )

                    GameScreen.GAME_ACTIVE -> GameActiveScreen(
                        word = mainWord,
                        randomWords = randomWords,
                        onNewGame = {
                            screen = GameScreen.SETUP
                            currentPlayerIndex = 0
                            playerRoles = emptyMap()
                            playerWordMap = emptyMap()
                            mainWord = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    wordSets: Map<String, List<String>>,
    playerCount: Int,
    spyCount: Int,
    selectedCategories: Set<String>,
    randomWords: Boolean,
    doubleAgentEnabled: Boolean,
    foolEnabled: Boolean,
    onPlayerCountChange: (Int) -> Unit,
    onSpyCountChange: (Int) -> Unit,
    onToggleCategory: (String) -> Unit,
    onRandomWordsChange: (Boolean) -> Unit,
    onDoubleAgentEnabledChange: (Boolean) -> Unit,
    onFoolEnabledChange: (Boolean) -> Unit,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "\u0418\u0433\u0440\u0430 \u0428\u043F\u0438\u043E\u043D",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        CounterCard(
            label = "\u0418\u0433\u0440\u043E\u043A\u0438",
            count = playerCount,
            min = 2,
            max = 20,
            onCountChange = onPlayerCountChange
        )

        CounterCard(
            label = "\u0428\u043F\u0438\u043E\u043D\u044B",
            count = spyCount,
            min = 0,
            max = playerCount,
            onCountChange = onSpyCountChange
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            var categoriesExpanded by remember { mutableStateOf(false) }
            val totalWords = selectedCategories.sumOf { wordSets[it]?.size ?: 0 }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoriesExpanded = !categoriesExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Категории слов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${selectedCategories.size}/${wordSets.size} · $totalWords слов  ${if (categoriesExpanded) "▲" else "▼"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = categoriesExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        wordSets.forEach { (category, words) ->
                            val isSelected = category in selectedCategories
                            if (isSelected) {
                                Button(
                                    onClick = { onToggleCategory(category) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("$category (${words.size})")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onToggleCategory(category) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("$category (${words.size})")
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Социальные эксперименты",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SwitchRow(
                    title = "Случайное слово для каждого",
                    subtitle = "У каждого игрока своё слово",
                    checked = randomWords,
                    onCheckedChange = onRandomWordsChange
                )
                SwitchRow(
                    title = "Двойной агент",
                    subtitle = "Знает слово, но играет за шпионов",
                    checked = doubleAgentEnabled,
                    onCheckedChange = onDoubleAgentEnabledChange
                )
                SwitchRow(
                    title = "Простак",
                    subtitle = "Получает другое слово, не зная об этом",
                    checked = foolEnabled,
                    onCheckedChange = onFoolEnabledChange
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "\u041D\u0430\u0447\u0430\u0442\u044C \u0438\u0433\u0440\u0443",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CounterCard(
    label: String,
    count: Int,
    min: Int,
    max: Int,
    onCountChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { onCountChange((count - 1).coerceAtLeast(min)) },
                    enabled = count > min,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("\u2212", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = TextAlign.Center
                )
                FilledTonalButton(
                    onClick = { onCountChange((count + 1).coerceAtMost(max)) },
                    enabled = count < max,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PlayerReadyScreen(
    playerNumber: Int,
    onReveal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onReveal
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u0418\u0433\u0440\u043E\u043A $playerNumber",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "\u041D\u0430\u0436\u043C\u0438\u0442\u0435, \u0447\u0442\u043E\u0431\u044B \u0443\u0437\u043D\u0430\u0442\u044C \u0441\u0432\u043E\u044E \u0440\u043E\u043B\u044C",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerRevealScreen(
    role: PlayerRole,
    word: String,
    onNext: () -> Unit
) {
    val backgroundColor = when (role) {
        PlayerRole.SPY -> MaterialTheme.colorScheme.errorContainer
        PlayerRole.DOUBLE_AGENT -> MaterialTheme.colorScheme.tertiaryContainer
        PlayerRole.NORMAL, PlayerRole.FOOL -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (role) {
        PlayerRole.SPY -> MaterialTheme.colorScheme.onErrorContainer
        PlayerRole.DOUBLE_AGENT -> MaterialTheme.colorScheme.onTertiaryContainer
        PlayerRole.NORMAL, PlayerRole.FOOL -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (role) {
                PlayerRole.SPY -> {
                    Text(
                        text = "Ты шпион!",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Попробуй угадать секретное слово",
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                PlayerRole.DOUBLE_AGENT -> {
                    Text(
                        text = "Двойной агент!",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Ты знаешь слово, но играешь за шпионов - помоги им понять слово",
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = word,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                }
                PlayerRole.FOOL, PlayerRole.NORMAL -> {
                    Text(
                        text = "Твоё слово:",
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = word,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = contentColor,
                    contentColor = backgroundColor
                )
            ) {
                Text("Понятно", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameActiveScreen(
    word: String,
    randomWords: Boolean,
    onNewGame: () -> Unit
) {
    var wordRevealed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u0418\u0433\u0440\u0430 \u043D\u0430\u0447\u0430\u043B\u0430\u0441\u044C!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "\u041E\u0431\u0441\u0443\u0436\u0434\u0430\u0439\u0442\u0435 \u0438 \u043D\u0430\u0439\u0434\u0438\u0442\u0435 \u0448\u043F\u0438\u043E\u043D\u0430!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            if (!randomWords) {
                OutlinedButton(
                    onClick = { wordRevealed = !wordRevealed },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (wordRevealed) "\u0421\u043B\u043E\u0432\u043E: $word" else "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u0441\u043B\u043E\u0432\u043E",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onNewGame,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("\u041D\u043E\u0432\u0430\u044F \u0438\u0433\u0440\u0430", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
