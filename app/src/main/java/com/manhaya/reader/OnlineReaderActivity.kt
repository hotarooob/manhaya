package com.manhaya.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// A provider interface allows additional independently tested sources later.
private interface CatalogProvider {
    val name: String
    fun search(query: String): List<OnlineTitle>
    fun chapters(mangaId: String): List<OnlineChapter>
    fun pages(chapterId: String): List<String>
}
private data class OnlineTitle(val id: String, val title: String, val description: String, val cover: String?)
private data class OnlineChapter(val id: String, val label: String)

private fun requestJson(url: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 12000
    conn.readTimeout = 16000
    conn.setRequestProperty("User-Agent", "Manhaya/0.3 (Android; MangaDex API client)")
    conn.setRequestProperty("Accept", "application/json")
    try {
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    } finally { conn.disconnect() }
}
private fun encoded(s: String) = URLEncoder.encode(s, "UTF-8")
private fun localized(json: JSONObject): String {
    for (key in listOf("ar", "en", "ja-ro", "ko-ro")) {
        val v = json.optString(key)
        if (v.isNotBlank()) return v
    }
    val keys = json.keys()
    return if (keys.hasNext()) json.optString(keys.next()) else ""
}
private object MangaDexProvider : CatalogProvider {
    override val name = "MangaDex · عربي"
    private const val api = "https://api.mangadex.org"
    override fun search(query: String): List<OnlineTitle> {
        val url = "$api/manga?limit=30&includes[]=cover_art&availableTranslatedLanguage[]=ar&contentRating[]=safe&contentRating[]=suggestive&order[latestUploadedChapter]=desc" +
            (if (query.isBlank()) "" else "&title=${encoded(query)}")
        val arr = requestJson(url).getJSONArray("data")
        return (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            val attr = item.getJSONObject("attributes")
            val relations = item.optJSONArray("relationships")
            var fileName: String? = null
            if (relations != null) for (j in 0 until relations.length()) {
                val rel = relations.getJSONObject(j)
                if (rel.optString("type") == "cover_art") fileName = rel.optJSONObject("attributes")?.optString("fileName")
            }
            OnlineTitle(item.getString("id"), localized(attr.getJSONObject("title")), localized(attr.optJSONObject("description") ?: JSONObject()),
                fileName?.takeIf { it.isNotBlank() }?.let { "https://uploads.mangadex.org/covers/${item.getString("id")}/$it.256.jpg" })
        }
    }
    override fun chapters(mangaId: String): List<OnlineChapter> {
        val results = mutableListOf<OnlineChapter>()
        var offset = 0
        while (offset < 500) {
            val url = "$api/chapter?manga=$mangaId&translatedLanguage[]=ar&limit=100&offset=$offset&order[chapter]=desc&contentRating[]=safe&contentRating[]=suggestive"
            val json = requestJson(url)
            val data = json.getJSONArray("data")
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val a = obj.getJSONObject("attributes")
                val number = a.optString("chapter").ifBlank { "?" }
                val title = a.optString("title")
                results.add(OnlineChapter(obj.getString("id"), "الفصل $number${if (title.isBlank()) "" else " · $title"}"))
            }
            offset += data.length()
            if (data.length() == 0 || offset >= json.optInt("total", offset)) break
        }
        return results.distinctBy { it.id }
    }
    override fun pages(chapterId: String): List<String> {
        val json = requestJson("$api/at-home/server/$chapterId")
        val base = json.getString("baseUrl")
        val chapter = json.getJSONObject("chapter")
        val hash = chapter.getString("hash")
        val files = chapter.getJSONArray("dataSaver")
        return (0 until files.length()).map { "$base/data-saver/$hash/${files.getString(it)}" }
    }
}

class OnlineReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OnlineScreen() }
    }
}

@Composable
private fun OnlineScreen() {
    val provider: CatalogProvider = MangaDexProvider
    var query by remember { mutableStateOf("") }
    var titles by remember { mutableStateOf<List<OnlineTitle>>(emptyList()) }
    var selected by remember { mutableStateOf<OnlineTitle?>(null) }
    var chapters by remember { mutableStateOf<List<OnlineChapter>>(emptyList()) }
    var selectedChapter by remember { mutableStateOf<OnlineChapter?>(null) }
    var pages by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    fun back() {
        when {
            selectedChapter != null -> { selectedChapter = null; pages = emptyList() }
            selected != null -> { selected = null; chapters = emptyList() }
        }
    }
    BackHandler(selected != null || selectedChapter != null) { back() }
    LaunchedEffect(selected?.id, selectedChapter?.id, refresh) {
        busy = true; error = null
        try {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                when {
                    selectedChapter != null -> Triple(emptyList<OnlineTitle>(), emptyList<OnlineChapter>(), provider.pages(selectedChapter!!.id))
                    selected != null -> Triple(emptyList<OnlineTitle>(), provider.chapters(selected!!.id), emptyList<String>())
                    else -> Triple(provider.search(query.trim()), emptyList<OnlineChapter>(), emptyList<String>())
                }
            }
            if (selectedChapter != null) pages = result.third
            else if (selected != null) chapters = result.second
            else titles = result.first
        } catch (ex: Exception) { error = ex.message ?: "Network error" }
        busy = false
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected != null) Text("‹", modifier = Modifier.clickable { back() }, style = MaterialTheme.typography.headlineSmall)
                    Text(selectedChapter?.label ?: selected?.title ?: "المصادر · ${provider.name}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp)) {
                if (selected == null) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("ابحث عن مانهوا") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { refresh++ }) { Text("بحث") } })
                    Text("أعمال لديها فصول مترجمة للعربية على MangaDex", style = MaterialTheme.typography.labelMedium)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text("تعذّر التحميل: $it", color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh++ }) { Text("إعادة المحاولة") } }
                if (!busy && error == null) {
                    when {
                        selectedChapter != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pages.isEmpty()) item { Text("لا توجد صور لهذا الفصل") }
                            items(pages) { url -> AsyncImage(model = url, contentDescription = "صفحة مانهوا", modifier = Modifier.fillMaxWidth()) }
                        }
                        selected != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { Text(selected!!.title, fontWeight = FontWeight.Bold); Text(selected!!.description.take(400)) }
                            if (chapters.isEmpty()) item { Text("لا توجد فصول عربية متاحة") }
                            items(chapters) { chapter -> ElevatedCard(onClick = { selectedChapter = chapter }) { Text(chapter.label, Modifier.fillMaxWidth().padding(16.dp)) } }
                        }
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(titles) { title -> ElevatedCard(onClick = { selected = title }) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(model = title.cover, contentDescription = null, modifier = Modifier.size(width = 72.dp, height = 96.dp))
                                    Column { Text(title.title, fontWeight = FontWeight.Bold); Text(title.description.take(110), maxLines = 3) }
                                }
                            } }
                        }
                    }
                }
            }
        }
    }
}
