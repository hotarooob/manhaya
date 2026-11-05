package com.manhaya.reader

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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

private val Bg = Color(0xFF0C0D13)
private val Surface = Color(0xFF191A25)
private val Accent = Color(0xFFA78BFA)
private val Muted = Color(0xFFADB0C3)
private data class Story(val title: String, val genre: String, val chapters: Int, val mark: String)
private val stories = listOf(
    Story("Moonlight Archive", "Fantasy · Action", 42, "✦"),
    Story("The Last Guardian", "Adventure · Mystery", 28, "◈"),
    Story("A World Beyond", "Romance · Fantasy", 64, "✧"),
    Story("Silent Kingdom", "Drama · Action", 17, "◆")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ManhayaApp() }
    }
}

@Composable
private fun ManhayaApp() {
    var lang by remember { mutableStateOf("ar") }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Story?>(null) }
    var reading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val favorites = remember { mutableStateListOf<String>() }
    val ctx = LocalContext.current
    fun label(id: Int): String {
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale(lang))
        return ctx.createConfigurationContext(cfg).getString(id)
    }
    val dir = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    val tabs = listOf(R.string.home, R.string.search, R.string.library, R.string.downloads, R.string.settings)
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Bookmarks, Icons.Default.Download, Icons.Default.Settings)
    val scheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface, onBackground = Color.White, onSurface = Color.White)
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        MaterialTheme(colorScheme = scheme) {
            Scaffold(containerColor = Bg, bottomBar = {
                if (selected == null) NavigationBar(containerColor = Surface) {
                    tabs.forEachIndexed { index, id ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null) },
                            label = { Text(label(id), fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Color(0xFF302844)))
                    }
                }
            }) { pad ->
                Column(Modifier.fillMaxSize().padding(pad)) {
                    if (selected != null) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (reading) reading = false else selected = null }) { Icon(Icons.Default.ArrowBack, null) }
                            Text(if (reading) selected!!.title else label(R.string.back), fontWeight = FontWeight.Bold)
                        }
                        if (reading) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(label(R.string.reader_preview), color = Muted)
                        } else {
                            val story = selected!!
                            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                item { Cover(story, Modifier.fillMaxWidth().height(180.dp)) }
                                item { Text(story.title, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                                item { Text(story.genre, color = Muted) }
                                item { Button(onClick = { if (favorites.contains(story.title)) favorites.remove(story.title) else favorites.add(story.title) }) {
                                    Text(label(if (favorites.contains(story.title)) R.string.saved else R.string.favorite))
                                } }
                                item { Text(label(R.string.chapters), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                                items((1..story.chapters).toList()) { chapter ->
                                    Surface(onClick = { reading = true }, color = Surface, shape = RoundedCornerShape(12.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${label(R.string.chapters)} $chapter")
                                            Icon(Icons.Default.ChevronRight, null, tint = Accent)
                                        }
                                    }
                                }
                            }
                        }
                    } else when (tab) {
                        0 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Header("Manhaya", label(R.string.welcome)) }
                            item { Text(label(R.string.featured), fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                            item { Cover(stories[0], Modifier.fillMaxWidth().height(185.dp).clickable { selected = stories[0] }) }
                            item { Text(label(R.string.latest), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            items(stories) { StoryRow(it) { selected = it } }
                            item { Text(label(R.string.sample_notice), color = Muted, fontSize = 12.sp) }
                        }
                        1 -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Header(label(R.string.search), label(R.string.placeholder))
                            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(label(R.string.search_hint)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
                            val filtered = stories.filter { it.title.contains(query, ignoreCase = true) }
                            if (filtered.isEmpty()) Text(label(R.string.no_results), color = Muted)
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered) { StoryRow(it) { selected = it } } }
                        }
                        2 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { Header(label(R.string.library), label(R.string.placeholder)) }
                            if (favorites.isEmpty()) item { EmptyMessage(label(R.string.library_empty)) }
                            else items(stories.filter { favorites.contains(it.title) }) { StoryRow(it) { selected = it } }
                        }
                        3 -> Column(Modifier.padding(20.dp)) {
                            Header(label(R.string.downloads), label(R.string.placeholder))
                            Spacer(Modifier.height(50.dp))
                            EmptyMessage(label(R.string.downloads_empty))
                        }
                        4 -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                            Header(label(R.string.settings), "Manhaya v0.1")
                            Text(label(R.string.language), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(selected = lang == "ar", onClick = { lang = "ar" }, label = { Text(label(R.string.arabic)) })
                                FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text(label(R.string.english)) })
                            }
                            Text(label(R.string.appearance), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(label(R.string.dark_mode), color = Muted)
                            Text(label(R.string.sample_notice), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}
@Composable private fun Cover(story: Story, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF392F70), Color(0xFF151A34), Color(0xFF593658))), RoundedCornerShape(18.dp))) {
        Text(story.mark, fontSize = 85.sp, color = Accent.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(story.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(story.genre, color = Color(0xFFDBD5EF), fontSize = 12.sp)
        }
    }
}
@Composable private fun StoryRow(story: Story, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(story, Modifier.width(88.dp).height(110.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(story.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(story.genre, fontSize = 12.sp, color = Muted)
                Text("${story.chapters} chapters", fontSize = 12.sp, color = Accent)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}
@Composable private fun EmptyMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Muted)
    }
}
