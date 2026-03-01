package me.semoro.spygame

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
        var currentPlayerIndex by remember { mutableStateOf(0) }
        var spyIndices by remember { mutableStateOf(setOf<Int>()) }
        var playerWordMap by remember { mutableStateOf(mapOf<Int, String>()) }

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
                        onStartGame = {
                            val words = selectedCategories.flatMap { wordSets[it].orEmpty() }
                            spyIndices =
                                (0 until playerCount).toList().shuffled().take(spyCount).toSet()
                            if (randomWords) {
                                playerWordMap = (0 until playerCount).associateWith { words.random() }
                            } else {
                                val word = words.random()
                                playerWordMap = (0 until playerCount).associateWith { word }
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
                        isSpy = currentPlayerIndex in spyIndices,
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
                        word = playerWordMap.values.firstOrNull().orEmpty(),
                        randomWords = randomWords,
                        onNewGame = {
                            screen = GameScreen.SETUP
                            currentPlayerIndex = 0
                            spyIndices = emptySet()
                            playerWordMap = emptyMap()
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
    onPlayerCountChange: (Int) -> Unit,
    onSpyCountChange: (Int) -> Unit,
    onToggleCategory: (String) -> Unit,
    onRandomWordsChange: (Boolean) -> Unit,
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\u041A\u0430\u0442\u0435\u0433\u043E\u0440\u0438\u0438 \u0441\u043B\u043E\u0432",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\u0421\u043E\u0446\u0438\u0430\u043B\u044C\u043D\u044B\u0439 \u044D\u043A\u0441\u043F\u0435\u0440\u0438\u043C\u0435\u043D\u0442",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "\u0421\u043B\u0443\u0447\u0430\u0439\u043D\u043E\u0435 \u0441\u043B\u043E\u0432\u043E \u0434\u043B\u044F \u043A\u0430\u0436\u0434\u043E\u0433\u043E",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = randomWords,
                    onCheckedChange = onRandomWordsChange
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
    isSpy: Boolean,
    word: String,
    onNext: () -> Unit
) {
    val backgroundColor = if (isSpy) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isSpy) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
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
            if (isSpy) {
                Text(
                    text = "\u0422\u044B \u0448\u043F\u0438\u043E\u043D!",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "\u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439 \u0443\u0433\u0430\u0434\u0430\u0442\u044C \u0441\u0435\u043A\u0440\u0435\u0442\u043D\u043E\u0435 \u0441\u043B\u043E\u0432\u043E",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "\u0422\u0432\u043E\u0451 \u0441\u043B\u043E\u0432\u043E:",
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
                Text("\u041F\u043E\u043D\u044F\u0442\u043D\u043E", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
