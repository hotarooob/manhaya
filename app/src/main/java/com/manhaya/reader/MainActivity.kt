package com.manhaya.reader

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val Purple = Color(0xFFA98BFA)
private val DarkBg = Color(0xFF0C0E18)
private val DarkCard = Color(0xFF1A1D2B)
private data class SourceInfo(val id: String, val name: String, val language: String, val active: Boolean)
private val sources = listOf(
    SourceInfo("mangadex", "MangaDex", "AR", true),
    SourceInfo("3asq", "3asq · العاشق", "AR", false),
    SourceInfo("azorafly", "AzoraFly", "AR", false),
    SourceInfo("asura", "Asura Scans", "EN", false),
    SourceInfo("vortex", "Vortex Scans", "EN", false),
    SourceInfo("mangalik", "MangaLik", "AR", false),
    SourceInfo("hijala", "Hijala", "AR", false),
    SourceInfo("mangatek", "MangaTek", "AR", false),
    SourceInfo("kenmanga", "KenManga", "AR", false),
    SourceInfo("kingofshojo", "King of Shojo", "EN", false),
    SourceInfo("lagoon", "Lagoon Scans", "EN", false),
    SourceInfo("olympus", "Olympus Staff", "EN", false),
    SourceInfo("mangatuk", "MangaTuk", "AR", false)
)
private data class Book(val id: String, val name: String, val desc: String, val cover: String?, val source: String = "mangadex")
private data class Chapter(val id: String, val number: String, val title: String)
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun localized(o: JSONObject): String {
    for (k in listOf("ar", "en", "ko-ro", "ja-ro")) o.optString(k).takeIf { it.isNotBlank() }?.let { return it }
    val keys = o.keys(); return if (keys.hasNext()) o.optString(keys.next()) else ""
}
private fun json(url: String): JSONObject {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 12000; c.readTimeout = 20000
    c.setRequestProperty("User-Agent", "Manhaya/0.4 (Android)")
    c.setRequestProperty("Accept", "application/json")
    try {
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } finally { c.disconnect() }
}
private object Catalog {
    private const val api = "https://api.mangadex.org"
    fun books(q: String): List<Book> {
        val url = "$api/manga?limit=36&includes[]=cover_art&availableTranslatedLanguage[]=ar&contentRating[]=safe&order[latestUploadedChapter]=desc" +
            (if (q.isBlank()) "" else "&title=${enc(q)}")
        val arr = json(url).getJSONArray("data")
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i); val a = obj.getJSONObject("attributes"); val id = obj.getString("id")
            val rels = obj.optJSONArray("relationships"); var filename: String? = null
            if (rels != null) for (j in 0 until rels.length()) {
                val rel = rels.getJSONObject(j)
                if (rel.optString("type") == "cover_art") filename = rel.optJSONObject("attributes")?.optString("fileName")
            }
            Book(id, localized(a.getJSONObject("title")), localized(a.optJSONObject("description") ?: JSONObject()),
                filename?.takeIf { it.isNotBlank() }?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" })
        }
    }
    fun chapters(id: String): List<Chapter> {
        val out = mutableListOf<Chapter>(); var offset = 0
        while (offset < 500) {
            val j = json("$api/chapter?manga=$id&translatedLanguage[]=ar&limit=100&offset=$offset&order[chapter]=desc&contentRating[]=safe")
            val a = j.getJSONArray("data")
            for (i in 0 until a.length()) {
                val obj = a.getJSONObject(i); val attr = obj.getJSONObject("attributes")
                out.add(Chapter(obj.getString("id"), attr.optString("chapter").ifBlank { "?" }, attr.optString("title")))
            }
            offset += a.length(); if (a.length() == 0 || offset >= j.optInt("total", offset)) break
        }
        return out.distinctBy { it.id }
    }
    fun pages(id: String): List<String> {
        val j = json("$api/at-home/server/$id"); val ch = j.getJSONObject("chapter")
        val a = ch.getJSONArray("dataSaver")
        return (0 until a.length()).map { "${j.getString("baseUrl")}/data-saver/${ch.getString("hash")}/${a.getString(it)}" }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12, 14, 24)
        window.navigationBarColor = android.graphics.Color.rgb(12, 14, 24)
        setContent { Manhaya() }
    }
}

@Composable
private fun Manhaya() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("manhaya_v02", Context.MODE_PRIVATE) }
    var lang by remember { mutableStateOf(prefs.getString("language", "ar") ?: "ar") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Book?>(null) }
    var current by remember { mutableStateOf<Chapter?>(null) }
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var results by remember { mutableStateOf<List<Book>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var pages by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val saved = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("online_favorites", emptySet()) ?: emptySet()) } }
    val cached = remember { mutableStateMapOf<String, Book>().apply {
        try { val a = JSONArray(prefs.getString("online_book_cache", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); put(o.getString("id"), Book(o.getString("id"), o.getString("name"), o.optString("desc"), o.optString("cover").takeIf { it.isNotBlank() })) }
        } catch (_: Exception) { }
    } }
    fun rememberBooks(list: List<Book>) {
        list.forEach { cached[it.id] = it }
        val a = JSONArray()
        cached.values.take(250).forEach { b -> a.put(JSONObject().put("id", b.id).put("name", b.name).put("desc", b.desc).put("cover", b.cover ?: "")) }
        prefs.edit().putString("online_book_cache", a.toString()).apply()
    }
    var enabledExtras by remember { mutableStateOf(prefs.getStringSet("enabled_extra_sources", emptySet()) ?: emptySet()) }
    val ar = lang == "ar"
    fun t(a: String, b: String) = if (ar) a else b
    fun back() { when { current != null -> { current = null; pages = emptyList() }; selected != null -> { selected = null; chapters = emptyList() }; else -> tab = 0 } }
    BackHandler(current != null || selected != null) { back() }
    LaunchedEffect(tab, search, selected?.id, current?.id, refresh) {
        issue = null
        if (tab == 4 && selected == null) return@LaunchedEffect
        if (tab == 3 && selected == null) return@LaunchedEffect
        if (tab == 2 && selected == null) return@LaunchedEffect
        if (tab == 1 && selected == null) delay(450)
        loading = true
        try {
            when {
                current != null -> pages = withContext(Dispatchers.IO) { Catalog.pages(current!!.id) }
                selected != null -> chapters = withContext(Dispatchers.IO) { Catalog.chapters(selected!!.id) }
                tab == 1 -> { results = withContext(Dispatchers.IO) { Catalog.books(search.trim()) }; rememberBooks(results) }
                tab == 0 -> { books = withContext(Dispatchers.IO) { Catalog.books("") }; rememberBooks(books) }
            }
        } catch (e: Exception) { issue = e.message ?: t("تعذر الاتصال", "Connection failed") }
        loading = false
    }
    val bg = if (dark) DarkBg else Color(0xFFF7F6FC)
    val surface = if (dark) DarkCard else Color.White
    val muted = if (dark) Color(0xFFABB0C5) else Color(0xFF666B81)
    val scheme = if (dark) darkColorScheme(primary = Purple, background = bg, surface = surface, onSurface = Color.White, onBackground = Color.White)
        else lightColorScheme(primary = Color(0xFF7352D2), background = bg, surface = surface)
    val tabs = listOf(t("الرئيسية", "Home"), t("اكتشف", "Discover"), t("مكتبتي", "Library"), t("التنزيلات", "Downloads"), t("الإعدادات", "Settings"))
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Bookmarks, Icons.Default.Download, Icons.Default.Settings)
    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = scheme) {
            Scaffold(containerColor = bg, bottomBar = {
                if (selected == null) NavigationBar(containerColor = surface) {
                    tabs.forEachIndexed { i, name -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) },
                        label = { Text(name, fontSize = 10.sp, maxLines = 1) }) }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (selected != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { back() }) { Icon(Icons.Default.ArrowBack, t("رجوع", "Back")) }
                            Text(current?.let { t("الفصل ${it.number}", "Chapter ${it.number}") } ?: selected!!.name,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    issue?.let { err -> Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("تعذر التحميل: ", "Could not load: ") + err, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { refresh++ }) { Text(t("إعادة", "Retry")) }
                    } }
                    when {
                        current != null -> {
                            val index = chapters.indexOfFirst { it.id == current!!.id }
                            LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (pages.isEmpty() && !loading) item { Empty(t("ما في صفحات متاحة لهالفصل", "No pages available"), muted) }
                                items(pages) { url -> AsyncImage(model = url, contentDescription = t("صفحة الفصل", "Chapter page"),
                                    contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                                item { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { if (index + 1 < chapters.size) current = chapters[index + 1] }, enabled = index >= 0 && index + 1 < chapters.size, modifier = Modifier.weight(1f)) { Text(t("السابق", "Previous")) }
                                    Button(onClick = { if (index > 0) current = chapters[index - 1] }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text(t("التالي", "Next")) }
                                } }
                            }
                        }
                        selected != null -> {
                            val book = selected!!
                            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                item { Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                                    Cover(book.cover, Modifier.width(125.dp).height(174.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Text(book.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                        AssistChip(onClick = {}, label = { Text("MangaDex · AR") })
                                        Text(t("فصول مترجمة للعربية", "Arabic-translated chapters"), color = muted, fontSize = 12.sp)
                                    }
                                } }
                                item { Text(book.desc.ifBlank { t("لا يوجد وصف", "No description") }.take(750), color = muted, fontSize = 13.sp) }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Button(onClick = { if (book.id in saved) saved.remove(book.id) else saved.add(book.id); prefs.edit().putStringSet("online_favorites", saved.toSet()).apply() }) {
                                        Icon(if (book.id in saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null); Spacer(Modifier.width(6.dp)); Text(t("مكتبتي", "Library"))
                                    }
                                    if (chapters.isNotEmpty()) OutlinedButton(onClick = { current = chapters.last(); prefs.edit().putString("last_${book.id}", current!!.id).apply() }) { Text(t("ابدأ القراءة", "Start reading")) }
                                } }
                                item { Text(t("الفصول العربية", "Arabic chapters"), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                                if (chapters.isEmpty() && !loading) item { Empty(t("ما في فصول متاحة", "No chapters available"), muted) }
                                items(chapters) { ch -> Surface(onClick = { current = ch; prefs.edit().putString("last_${book.id}", ch.id).apply() }, color = surface, shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text(t("الفصل ${ch.number}", "Chapter ${ch.number}"), fontWeight = FontWeight.SemiBold); if (ch.title.isNotBlank()) Text(ch.title, color = muted, fontSize = 12.sp) }
                                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                } }
                            }
                        }
                        tab == 0 -> LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Heading("Manhaya", t("عالم المانهوا بين إيديك", "Your manhwa universe"), muted) }
                            item { Text(t("أحدث الأعمال المترجمة", "Latest translated titles"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            if (books.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(books.take(12)) { b -> Column(Modifier.width(142.dp).clickable { selected = b }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Cover(b.cover, Modifier.fillMaxWidth().height(195.dp)); Text(b.name, maxLines = 2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                } }
                            } }
                            item { Text(t("تصفّح المانهوا", "Explore manhwa"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            items(books) { b -> BookRow(b, surface, muted) { selected = b } }
                            if (books.isEmpty() && !loading && issue == null) item { Empty(t("ما في نتائج حالياً", "No titles yet"), muted) }
                            item { Text(t("المصدر المتصل حالياً: MangaDex · العربية. باقي المصادر مسجّلة بانتظار ربطها.", "Connected: MangaDex Arabic. Other sources await integration."), color = muted, fontSize = 11.sp) }
                        }
                        tab == 1 -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading(t("اكتشف", "Discover"), t("بحث موحّد عبر المصادر المتصلة", "Search connected sources"), muted)
                            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text(t("ابحث عن مانهوا", "Search titles")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, null) } })
                            Text(t("المصدر المتصل: MangaDex", "Connected source: MangaDex"), fontSize = 12.sp, color = muted)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(results) { b -> BookRow(b, surface, muted) { selected = b } }
                                if (results.isEmpty() && !loading && issue == null) item { Empty(t("ما لقينا نتائج", "No results"), muted) }
                            }
                        }
                        tab == 2 -> LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { Heading(t("مكتبتي", "Library"), t("أعمالك المحفوظة", "Your saved titles"), muted) }
                            val favorites = saved.mapNotNull { cached[it] }
                            if (favorites.isEmpty()) item { Empty(t("ما في أعمال محفوظة", "No saved titles"), muted) }
                            items(favorites) { b -> BookRow(b, surface, muted) { selected = b } }
                        }
                        tab == 3 -> Column(Modifier.fillMaxSize().padding(18.dp)) {
                            Heading(t("التنزيلات", "Downloads"), t("قراءة أونلاين حالياً", "Online reading for now"), muted)
                            Empty(t("تنزيل الفصول للاستخدام دون إنترنت مش متاح بهالنسخة", "Offline downloads are not available in this release"), muted)
                        }
                        tab == 4 -> LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Heading(t("الإعدادات", "Settings"), "Manhaya v0.4", muted) }
                            item { Text(t("اللغة", "Language"), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = ar, onClick = { lang = "ar"; prefs.edit().putString("language", lang).apply() }, label = { Text("العربية") })
                                FilterChip(selected = !ar, onClick = { lang = "en"; prefs.edit().putString("language", lang).apply() }, label = { Text("English") })
                            } }
                            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(t("الوضع الداكن", "Dark mode"), modifier = Modifier.weight(1f))
                                Switch(checked = dark, onCheckedChange = { dark = it; prefs.edit().putBoolean("dark", dark).apply() })
                            } }
                            item { HorizontalDivider(); Spacer(Modifier.height(10.dp)); Text(t("المصادر", "Sources"), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                            item { Text(t("المصادر غير المتصلة ظاهرة للشفافية فقط، وتفعيلها ما بيخليها تجيب بيانات قبل إضافة محركها.", "Unconnected sources are listed for visibility; toggling them does not enable fetching until their adapters exist."), color = muted, fontSize = 12.sp) }
                            items(sources) { src -> Surface(color = surface, shape = RoundedCornerShape(13.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(src.name, fontWeight = FontWeight.SemiBold); Text(if (src.active) t("متصل", "Connected") else t("غير متصل · بانتظار محرك", "Unavailable · adapter needed"), color = muted, fontSize = 11.sp) }
                                    if (src.active) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    else Checkbox(checked = src.id in enabledExtras, onCheckedChange = { checked ->
                                        enabledExtras = if (checked) enabledExtras + src.id else enabledExtras - src.id
                                        prefs.edit().putStringSet("enabled_extra_sources", enabledExtras).apply()
                                    })
                                }
                            } }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Heading(title: String, subtitle: String, muted: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = muted, fontSize = 13.sp) }
}
@Composable private fun Cover(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF35304F))) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
@Composable private fun BookRow(book: Book, surface: Color, muted: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(book.cover, Modifier.width(83.dp).height(115.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.desc.ifBlank { "MangaDex" }, color = muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("MangaDex · AR", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = muted)
        }
    }
}
@Composable private fun Empty(msg: String, muted: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 55.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.AutoStories, null, tint = muted, modifier = Modifier.size(36.dp)); Text(msg, color = muted, fontSize = 13.sp)
        }
    }
}
