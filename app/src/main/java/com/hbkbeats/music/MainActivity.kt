package com.hbkbeats.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

val Orange = Color(0xFFFF6A00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerManager.init(this)
        PrivateStore.init(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Orange)) { App() }
        }
    }

    override fun onStart() {
        super.onStart()
        PrivateStore.keepOpen = false
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (home, screen off, other app) locks the private space.
        if (!isChangingConfigurations && !PrivateStore.keepOpen) PrivateStore.lock()
    }
}

private fun hasMedia(ctx: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
}

fun openVideo(ctx: Context, index: Int, priv: Boolean) {
    PrivateStore.keepOpen = true
    ctx.startActivity(
        Intent(ctx, VideoPlayerActivity::class.java)
            .putExtra("index", index)
            .putExtra("private", priv)
    )
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var granted by remember { mutableStateOf(hasMedia(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasMedia(ctx) }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    LaunchedEffect(granted) { if (granted) MediaRepository.load(ctx) }

    var tab by remember { mutableIntStateOf(0) }

    // Normal lists never contain private items.
    val songs = remember(MediaRepository.songs, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.songs.filter { !PrivateStore.isPrivate(it) }
    }
    val videos = remember(MediaRepository.videos, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.videos.filter { !PrivateStore.isPrivate(it) }
    }

    // Block screenshots and hide the recents thumbnail while the private tab is open.
    val secure = tab == 2 && PrivateStore.unlocked
    LaunchedEffect(secure) {
        val w = (ctx as? ComponentActivity)?.window
        if (secure) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    Scaffold(bottomBar = { MiniPlayer(allowPrivate = tab == 2) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "HbkBeats Music",
                style = MaterialTheme.typography.headlineSmall,
                color = Orange,
                modifier = Modifier.padding(16.dp)
            )
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Music") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Videos") })
                Tab(tab == 2, { tab = 2 }, text = { Text("🔒 Private") })
            }
            if (!granted) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Allow access to your music and videos to see them here.")
                    Button(onClick = { launcher.launch(perms) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Grant access")
                    }
                }
            } else when (tab) {
                0 -> TrackList(songs, Icons.Default.MusicNote, privateView = false) { i ->
                    PlayerManager.play(songs, i)
                }
                1 -> TrackList(videos, Icons.Default.Movie, privateView = false) { i ->
                    openVideo(ctx, i, false)
                }
                else -> if (PrivateStore.unlocked) PrivateScreen() else PinGate()
            }
        }
    }
}

@Composable
fun PinGate() {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val creating = !PrivateStore.hasPin
    val keys = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, null, tint = Orange)
        Text(
            if (creating) "Create a PIN for your private space (4-8 digits)" else "Enter your PIN",
            modifier = Modifier.padding(vertical = 12.dp)
        )
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); error = "" },
            label = { Text("PIN") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = keys
        )
        if (creating) {
            Spacer(Modifier.padding(4.dp))
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(8); error = "" },
                label = { Text("Confirm PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = keys
            )
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Button(
            onClick = {
                if (creating) {
                    when {
                        pin.length < 4 -> error = "Use at least 4 digits"
                        pin != confirm -> error = "PINs don't match"
                        else -> { PrivateStore.setPin(pin); PrivateStore.unlock(pin) }
                    }
                } else if (!PrivateStore.unlock(pin)) {
                    error = "Wrong PIN"; pin = ""
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text(if (creating) "Create & unlock" else "Unlock") }
    }
}

@Composable
fun PrivateScreen() {
    val ctx = LocalContext.current
    var sub by remember { mutableIntStateOf(0) }
    val pv = remember(MediaRepository.videos, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.videos.filter { PrivateStore.isPrivate(it) }
    }
    val ps = remember(MediaRepository.songs, PrivateStore.hiddenFolders, PrivateStore.hiddenItems) {
        MediaRepository.songs.filter { PrivateStore.isPrivate(it) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ sub = 0 }) { Text("Videos (${pv.size})", color = if (sub == 0) Orange else Color.Gray) }
            TextButton({ sub = 1 }) { Text("Music (${ps.size})", color = if (sub == 1) Orange else Color.Gray) }
            Spacer(Modifier.weight(1f))
            TextButton({ PrivateStore.lock() }) {
                Icon(Icons.Default.Lock, null, tint = Orange)
                Text("  Lock", color = Orange)
            }
        }
        val empty = "Nothing private yet. Tap ⋮ on any file in Music or Videos to hide it here."
        if (sub == 0) TrackList(pv, Icons.Default.Movie, privateView = true, emptyText = empty) { i ->
            openVideo(ctx, i, true)
        } else TrackList(ps, Icons.Default.MusicNote, privateView = true, emptyText = empty) { i ->
            PlayerManager.play(ps, i)
        }
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    icon: ImageVector,
    privateView: Boolean,
    emptyText: String = "Nothing found yet",
    onClick: (Int) -> Unit
) {
    val ctx = LocalContext.current
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(emptyText) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> (if (t.isVideo) "v" else "a") + t.id }) { i, t ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { onClick(i) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Orange)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (t.isVideo) "${t.folderName} • ${fmt(t.durationMs)}"
                        else "${t.artist} • ${fmt(t.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!privateView) {
                            DropdownMenuItem(
                                text = { Text("Make private") },
                                onClick = {
                                    menu = false
                                    if (!PrivateStore.hasPin)
                                        Toast.makeText(ctx, "Create a PIN in the Private tab first", Toast.LENGTH_LONG).show()
                                    else PrivateStore.hideItem(t)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Make folder “${t.folderName}” private") },
                                onClick = {
                                    menu = false
                                    if (!PrivateStore.hasPin)
                                        Toast.makeText(ctx, "Create a PIN in the Private tab first", Toast.LENGTH_LONG).show()
                                    else PrivateStore.hideFolder(t)
                                }
                            )
                        } else {
                            if (PrivateStore.isItemHidden(t)) DropdownMenuItem(
                                text = { Text("Remove from private") },
                                onClick = { menu = false; PrivateStore.unhideItem(t) }
                            )
                            if (PrivateStore.isFolderHidden(t)) DropdownMenuItem(
                                text = { Text("Unhide folder “${t.folderName}”") },
                                onClick = { menu = false; PrivateStore.unhideFolder(t) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(allowPrivate: Boolean) {
    val t = PlayerManager.current
    if (t == null || t.isVideo) return
    // Private songs never show in the mini player outside the unlocked Private tab.
    if (PrivateStore.isPrivate(t) && !(allowPrivate && PrivateStore.unlocked)) return
    Surface(tonalElevation = 6.dp) {
        Column {
            val progress = if (PlayerManager.duration > 0)
                (PlayerManager.position.toFloat() / PlayerManager.duration).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Orange)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                }
                IconButton({ PlayerManager.prev() }) { Icon(Icons.Default.SkipPrevious, "Previous") }
                IconButton({ PlayerManager.toggle() }) {
                    Icon(if (PlayerManager.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                }
                IconButton({ PlayerManager.next() }) { Icon(Icons.Default.SkipNext, "Next") }
            }
        }
    }
}
