package com.manhaya.reader

import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Night = Color(0xFF0B0D16)
private val NightCard = Color(0xFF191C2B)
private val Violet = Color(0xFFAD91FF)
private data class Story(val id: String, val title: String, val genre: String, val chapters: Int, val symbol: String, val shades: List<Color>)
private val stories = listOf(
    Story("moon", "Moonlight Archive", "Fantasy · Action", 42, "✦", listOf(Color(0xFF5541A0), Color(0xFF1C284F))),
    Story("guardian", "The Last Guardian", "Adventure · Mystery", 28, "◈", listOf(Color(0xFF136E75), Color(0xFF192D49))),
    Story("beyond", "A World Beyond", "Romance · Fantasy", 64, "✧", listOf(Color(0xFF9A467A), Color(0xFF43285F))),
    Story("kingdom", "Silent Kingdom", "Drama · Action", 17, "◆", listOf(Color(0xFF795D3B), Color(0xFF332D4D)))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 13, 22)
        window.navigationBarColor = android.graphics.Color.rgb(11, 13, 22)
        setContent { ManhayaApp() }
    }
}

@Composable
private fun ManhayaApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("manhaya_v02", Context.MODE_PRIVATE) }
    var lang by remember { mutableStateOf(prefs.getString("language", "ar") ?: "ar") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Story?>(null) }
    var chapter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val favorites = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet()) } }
    val progress = remember { mutableStateMapOf<String, Int>().apply { stories.forEach { story -> val n = prefs.getInt("chapter_${story.id}", 0); if (n > 0) put(story.id, n) } } }
    fun label(id: Int): String {
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale(lang))
        return ctx.createConfigurationContext(cfg).getString(id)
    }
    fun saveFavorite(story: Story) {
        if (story.id in favorites) favorites.remove(story.id) else favorites.add(story.id)
        prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
    }
    fun openChapter(story: Story, n: Int) {
        selected = story
        chapter = n.coerceIn(1, story.chapters)
        progress[story.id] = chapter
        prefs.edit().putInt("chapter_${story.id}", chapter).apply()
    }
    BackHandler(selected != null) { if (chapter > 0) chapter = 0 else selected = null }
    val bg = if (dark) Night else Color(0xFFF6F5FB)
    val card = if (dark) NightCard else Color.White
    val ink = if (dark) Color.White else Color(0xFF202135)
    val muted = if (dark) Color(0xFFA5A8BF) else Color(0xFF65697E)
    val scheme = if (dark) darkColorScheme(primary = Violet, background = bg, surface = card, onBackground = ink, onSurface = ink)
                 else lightColorScheme(primary = Color(0xFF7052C8), background = bg, surface = card, onBackground = ink, onSurface = ink)
    val tabs = listOf(R.string.home, R.string.search, R.string.library, R.string.downloads, R.string.settings)
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Bookmarks, Icons.Default.Download, Icons.Default.Settings)
    CompositionLocalProvider(LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = scheme) {
            Scaffold(containerColor = bg, bottomBar = {
                if (selected == null) NavigationBar(containerColor = card) {
                    tabs.forEachIndexed { index, id ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null) },
                            label = { Text(label(id), fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }) { pad ->
                Column(Modifier.fillMaxSize().padding(pad)) {
                    if (selected != null) {
                        val story = selected!!
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (chapter > 0) chapter = 0 else selected = null }) { Icon(Icons.Default.ArrowBack, label(R.string.back)) }
                            Text(if (chapter > 0) "${story.title} · $chapter" else story.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (chapter > 0) {
                            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item { Text("${label(R.string.chapters)} $chapter", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                                item {
                                    Surface(color = card, shape = RoundedCornerShape(20.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(label(R.string.reader_preview), color = muted)
                                            Text(label(R.string.sample_notice), color = muted, fontSize = 12.sp)
                                        }
                                    }
                                }
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = { openChapter(story, chapter - 1) }, enabled = chapter > 1, modifier = Modifier.weight(1f)) { Text(if (lang == "ar") "السابق" else "Previous") }
                                        Button(onClick = { openChapter(story, chapter + 1) }, enabled = chapter < story.chapters, modifier = Modifier.weight(1f)) { Text(if (lang == "ar") "التالي" else "Next") }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                item { Cover(story, Modifier.fillMaxWidth().height(205.dp)) }
                                item { Text(story.title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }
                                item { Text(story.genre, color = muted) }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = { openChapter(story, progress[story.id] ?: 1) }) { Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(7.dp)); Text(if (progress.containsKey(story.id)) label(R.string.continue_reading) else label(R.string.start_reading)) }
                                        OutlinedButton(onClick = { saveFavorite(story) }) { Icon(if (story.id in favorites) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null); Spacer(Modifier.width(6.dp)); Text(label(if (story.id in favorites) R.string.saved else R.string.favorite)) }
                                    }
                                }
                                item { Text(label(R.string.chapters), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                                items((1..story.chapters).toList().reversed()) { n ->
                                    Surface(onClick = { openChapter(story, n) }, color = card, shape = RoundedCornerShape(14.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${label(R.string.chapters)} $n", modifier = Modifier.weight(1f))
                                            if (progress[story.id] == n) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            else Icon(Icons.Default.ChevronRight, null, tint = muted)
                                        }
                                    }
                                }
                            }
                        }
                    } else when (tab) {
                        0 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                            item { Header("Manhaya", label(R.string.welcome), muted) }
                            item { Text(label(R.string.featured), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            item { Cover(stories[0], Modifier.fillMaxWidth().height(200.dp).clickable { selected = stories[0] }) }
                            if (progress.isNotEmpty()) {
                                item { Text(label(R.string.continue_reading), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                                items(stories.filter { progress.containsKey(it.id) }) { s -> StoryRow(s, card, muted, progress[s.id]) { openChapter(s, progress[s.id] ?: 1) } }
                            }
                            item { Text(label(R.string.latest), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            items(stories) { s -> StoryRow(s, card, muted, null) { selected = s } }
                            item { Text(label(R.string.sample_notice), color = muted, fontSize = 12.sp) }
                        }
                        1 -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Header(label(R.string.search), label(R.string.placeholder), muted)
                            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(label(R.string.search_hint)) }, singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } })
                            val filtered = stories.filter { it.title.contains(query.trim(), ignoreCase = true) || it.genre.contains(query.trim(), ignoreCase = true) }
                            if (filtered.isEmpty()) EmptyMessage(label(R.string.no_results), muted)
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered) { s -> StoryRow(s, card, muted, null) { selected = s } } }
                        }
                        2 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { Header(label(R.string.library), if (lang == "ar") "قصصك المحفوظة" else "Your saved stories", muted) }
                            if (favorites.isEmpty()) item { EmptyMessage(label(R.string.library_empty), muted) }
                            else items(stories.filter { it.id in favorites }) { s -> StoryRow(s, card, muted, progress[s.id]) { selected = s } }
                        }
                        3 -> Column(Modifier.padding(20.dp)) {
                            Header(label(R.string.downloads), label(R.string.placeholder), muted)
                            Spacer(Modifier.height(35.dp))
                            EmptyMessage(label(R.string.downloads_empty), muted)
                        }
                        4 -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Header(label(R.string.settings), "Manhaya v0.3", muted)
                            Button(onClick = { ctx.startActivity(Intent(ctx, OnlineReaderActivity::class.java)) }) { Text(if (lang == "ar") "تصفح المانهوا العربية" else "Browse Arabic manhwa") }
                            Text(label(R.string.language), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(selected = lang == "ar", onClick = { lang = "ar"; prefs.edit().putString("language", lang).apply() }, label = { Text(label(R.string.arabic)) })
                                FilterChip(selected = lang == "en", onClick = { lang = "en"; prefs.edit().putString("language", lang).apply() }, label = { Text(label(R.string.english)) })
                            }
                            Text(label(R.string.appearance), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(label(R.string.dark_mode), modifier = Modifier.weight(1f))
                                Switch(checked = dark, onCheckedChange = { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                            }
                            HorizontalDivider()
                            Text(label(R.string.sample_notice), color = muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Header(title: String, subtitle: String, muted: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = muted, fontSize = 13.sp)
    }
}
@Composable private fun Cover(story: Story, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(story.shades), RoundedCornerShape(20.dp))) {
        Text(story.symbol, fontSize = 76.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(story.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(story.genre, color = Color(0xFFE0DDF4), fontSize = 12.sp)
        }
    }
}
@Composable private fun StoryRow(story: Story, card: Color, muted: Color, currentChapter: Int?, onClick: () -> Unit) {
    Surface(onClick = onClick, color = card, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(story, Modifier.width(88.dp).height(112.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(story.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(story.genre, fontSize = 12.sp, color = muted)
                Text(if (currentChapter != null) "Chapter $currentChapter / ${story.chapters}" else "${story.chapters} chapters", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = muted)
        }
    }
}
@Composable private fun EmptyMessage(message: String, muted: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 55.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(40.dp), tint = muted)
            Text(message, color = muted)
        }
    }
}
