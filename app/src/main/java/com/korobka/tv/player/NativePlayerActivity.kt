package com.korobka.tv.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.korobka.tv.data.AppStorage
import com.korobka.tv.data.PlaybackHistoryItem
import com.korobka.tv.data.StreamSource
import com.korobka.tv.engine.CdnEngine
import kotlinx.coroutines.delay

class NativePlayerActivity : ComponentActivity() {
    companion object {
        fun start(
            context: Context,
            itemId: String,
            title: String,
            poster: String?,
            isSeries: Boolean,
            season: Int = 1,
            episode: Int = 1,
            initialPositionMs: Long = 0,
            initialTranslation: String? = null
        ) {
            val intent = Intent(context, NativePlayerActivity::class.java).apply {
                putExtra("item_id", itemId)
                putExtra("title", title)
                putExtra("poster", poster)
                putExtra("is_series", isSeries)
                putExtra("season", season)
                putExtra("episode", episode)
                putExtra("position_ms", initialPositionMs)
                putExtra("translation", initialTranslation)
            }
            context.startActivity(intent)
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private var isDrawerOpenState = mutableStateOf(false)
    private var isDialogOpenState = mutableStateOf(false)
    private var showOverlayUntilMs = mutableLongStateOf(0L)
    private var isAtDrawerTopState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val itemId = intent.getStringExtra("item_id").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val poster = intent.getStringExtra("poster")
        val isSeries = intent.getBooleanExtra("is_series", false)
        val initialSeason = intent.getIntExtra("season", 1)
        val initialEpisode = intent.getIntExtra("episode", 1)
        val initialPositionMs = intent.getLongExtra("position_ms", 0L)
        val initialTranslation = intent.getStringExtra("translation")

        setContent {
            NativePlayerScreen(
                itemId = itemId,
                title = title,
                poster = poster,
                isSeries = isSeries,
                initialSeason = initialSeason,
                initialEpisode = initialEpisode,
                initialPositionMs = initialPositionMs,
                initialTranslation = initialTranslation,
                onClose = { finish() },
                onPlayerReady = { player -> exoPlayer = player },
                isDrawerOpenState = isDrawerOpenState,
                isDialogOpenState = isDialogOpenState,
                showOverlayUntilMs = showOverlayUntilMs,
                isAtDrawerTopState = isAtDrawerTopState
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isDialogOpenState.value) {
            return super.onKeyDown(keyCode, event)
        }

        showOverlayUntilMs.longValue = System.currentTimeMillis() + 3500L

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!isDrawerOpenState.value) {
                exoPlayer?.pause()
                isDrawerOpenState.value = true
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (isDrawerOpenState.value && isAtDrawerTopState.value) {
                isDrawerOpenState.value = false
                exoPlayer?.play()
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isDrawerOpenState.value) {
                isDrawerOpenState.value = false
                exoPlayer?.play()
                return true
            }
            finish()
            return true
        }

        if (!isDrawerOpenState.value) {
            exoPlayer?.let { p ->
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        p.seekTo(Math.max(0, p.currentPosition - 10000))
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        p.seekTo(Math.min(p.duration, p.currentPosition + 10000))
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (p.isPlaying) p.pause() else p.play()
                        return true
                    }
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun NativePlayerScreen(
    itemId: String,
    title: String,
    poster: String?,
    isSeries: Boolean,
    initialSeason: Int,
    initialEpisode: Int,
    initialPositionMs: Long,
    initialTranslation: String?,
    onClose: () -> Unit,
    onPlayerReady: (ExoPlayer) -> Unit,
    isDrawerOpenState: MutableState<Boolean>,
    isDialogOpenState: MutableState<Boolean>,
    showOverlayUntilMs: MutableLongState,
    isAtDrawerTopState: MutableState<Boolean>
) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }

    var currentSeason by remember { mutableIntStateOf(initialSeason) }
    var currentEpisode by remember { mutableIntStateOf(initialEpisode) }
    var selectedDrawerSeason by remember { mutableIntStateOf(initialSeason) }
    var showNextEpisodeDialog by remember { mutableStateOf(false) }
    var currentTranslation by remember { mutableStateOf(initialTranslation ?: "LostFilm") }
    var currentQuality by remember { mutableStateOf("1080p FHD") }
    var streamSource by remember { mutableStateOf<StreamSource?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var isFavorite by remember { mutableStateOf(storage.isFavorite(itemId)) }

    var curPosMs by remember { mutableLongStateOf(initialPositionMs) }
    var totalDurMs by remember { mutableLongStateOf(0L) }

    var activeDialog by remember { mutableStateOf<String?>(null) } // "audio" | "quality"
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(activeDialog, showNextEpisodeDialog) {
        isDialogOpenState.value = (activeDialog != null || showNextEpisodeDialog)
    }

    val firstDrawerFocusRequester = remember { FocusRequester() }
    val modalFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }

    val exoplayer = remember {
        val headers = hashMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { onPlayerReady(it) }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (isSeries) {
                        showNextEpisodeDialog = true
                    }
                }
            }
        }
        exoplayer.addListener(listener)
        onDispose {
            exoplayer.removeListener(listener)
            exoplayer.release()
        }
    }

    // Direct switch of HLS Audio Track in ExoPlayer by audio group index
    fun applyAudioTrack(audioGroupIndex: Int) {
        try {
            val currentTracks: Tracks = exoplayer.currentTracks
            android.util.Log.d("KorobkaAudio", "=== applyAudioTrack called with target audio group: $audioGroupIndex ===")
            val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audioGroupIndex in audioGroups.indices) {
                val targetTrackGroup = audioGroups[audioGroupIndex]
                val mediaTrackGroup = targetTrackGroup.mediaTrackGroup
                val trackIndices = (0 until mediaTrackGroup.length).toList()
                val override = TrackSelectionOverride(mediaTrackGroup, trackIndices)
                exoplayer.trackSelectionParameters = exoplayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setOverrideForType(override)
                    .build()
                android.util.Log.d("KorobkaAudio", "Successfully selected audio group #$audioGroupIndex (${mediaTrackGroup.getFormat(0).label})")
            } else {
                android.util.Log.w("KorobkaAudio", "Target audioGroupIndex $audioGroupIndex out of bounds (found ${audioGroups.size} audio groups)")
            }
        } catch (e: Exception) {
            android.util.Log.e("KorobkaAudio", "Error in applyAudioTrack", e)
        }
    }

    // Timer loop for time tracking & auto overlay fading
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            nowMs = System.currentTimeMillis()
            curPosMs = exoplayer.currentPosition
            totalDurMs = exoplayer.duration.coerceAtLeast(0L)
        }
    }

    // Load stream on (season, episode) change
    LaunchedEffect(itemId, currentSeason, currentEpisode) {
        isLoading = true
        val src = CdnEngine.resolveStream(itemId, isSeries, currentSeason, currentEpisode)
        streamSource = src
        isLoading = false

        if (src != null) {
            val mediaItem = MediaItem.fromUri(src.streamUrl)
            exoplayer.stop()
            exoplayer.setMediaItem(mediaItem)
            exoplayer.prepare()
            if (initialPositionMs > 0 && currentSeason == initialSeason && currentEpisode == initialEpisode) {
                exoplayer.seekTo(initialPositionMs)
            }
            exoplayer.playWhenReady = true

            // Automatically apply selected translation
            val translations = src.translations
            if (translations.isNotEmpty()) {
                val matchedIdx = translations.indexOfFirst { it.name.contains(currentTranslation, ignoreCase = true) }
                val targetIdx = if (matchedIdx != -1) matchedIdx else 0
                currentTranslation = translations[targetIdx].name

                delay(800)
                applyAudioTrack(targetIdx)
            }
        }
    }

    // Auto-save history every 4 seconds
    LaunchedEffect(itemId, currentSeason, currentEpisode, currentTranslation) {
        while (true) {
            delay(4000)
            if (exoplayer.currentPosition > 0 && exoplayer.duration > 0) {
                storage.saveHistoryItem(
                    PlaybackHistoryItem(
                        itemId = itemId,
                        title = title,
                        poster = poster,
                        season = if (isSeries) currentSeason else null,
                        episode = if (isSeries) currentEpisode else null,
                        translation = currentTranslation,
                        positionMs = exoplayer.currentPosition,
                        durationMs = exoplayer.duration,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    LaunchedEffect(isDrawerOpenState.value) {
        if (isDrawerOpenState.value) {
            delay(120)
            try {
                firstDrawerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(activeDialog) {
        if (activeDialog != null) {
            delay(120)
            try {
                modalFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showNextEpisodeDialog) {
        if (showNextEpisodeDialog) {
            delay(120)
            try {
                nextEpisodeFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val isTopOverlayVisible = !isDrawerOpenState.value && (nowMs < showOverlayUntilMs.longValue || !exoplayer.isPlaying)

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val horizontalPadding = if (isPortrait) 16.dp else 48.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .clickable {
                showOverlayUntilMs.longValue = System.currentTimeMillis() + 3500L
                if (!isDrawerOpenState.value) {
                    if (exoplayer.isPlaying) exoplayer.pause() else exoplayer.play()
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = exoplayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp)
            }
        }

        AnimatedVisibility(
            visible = isTopOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xDD000000), Color.Transparent)
                        )
                    )
                    .padding(horizontal = horizontalPadding, vertical = if (isPortrait) 36.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            fontSize = if (isPortrait) 16.sp else 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = if (isSeries) "Сезон $currentSeason, Серия $currentEpisode • $currentTranslation" else currentTranslation,
                            fontSize = 12.sp,
                            color = Color(0xFFCCCCCC),
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${formatDuration(curPosMs)} / ${formatDuration(totalDurMs)}",
                        fontSize = if (isPortrait) 12.sp else 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            isDrawerOpenState.value = !isDrawerOpenState.value
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Настройки",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (isDrawerOpenState.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC000000),
                                Color(0xFC000000)
                            )
                        )
                    )
                    .clickable {
                        isDrawerOpenState.value = false
                        exoplayer.play()
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = horizontalPadding, vertical = if (isPortrait) 16.dp else 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = formatDuration(curPosMs),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        val progress = if (totalDurMs > 0) curPosMs.toFloat() / totalDurMs.toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF262626))
                                .clickable {
                                    // Touch to play/pause or scrub
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }

                        Text(
                            text = formatDuration(totalDurMs),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAAAAAA)
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            HighContrastButton(
                                text = "Озвучка: $currentTranslation",
                                icon = Icons.Default.VolumeUp,
                                modifier = Modifier
                                    .focusRequester(firstDrawerFocusRequester)
                                    .onFocusChanged { if (it.isFocused) isAtDrawerTopState.value = true },
                                onClick = { activeDialog = "audio" }
                            )
                        }

                        item {
                            HighContrastButton(
                                text = "Качество: $currentQuality",
                                icon = Icons.Default.Settings,
                                modifier = Modifier.onFocusChanged { if (it.isFocused) isAtDrawerTopState.value = true },
                                onClick = { activeDialog = "quality" }
                            )
                        }

                        item {
                            HighContrastButton(
                                text = if (isFavorite) "В избранном" else "В избранное",
                                icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                modifier = Modifier.onFocusChanged { if (it.isFocused) isAtDrawerTopState.value = true },
                                onClick = {
                                    val res = storage.toggleFavorite(
                                        com.korobka.tv.data.MediaItem(
                                            id = itemId,
                                            title = title,
                                            poster = poster,
                                            isSeries = isSeries
                                        )
                                    )
                                    isFavorite = res
                                }
                            )
                        }

                        item {
                            HighContrastButton(
                                text = "Продолжить",
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.onFocusChanged { if (it.isFocused) isAtDrawerTopState.value = true },
                                onClick = {
                                    isDrawerOpenState.value = false
                                    exoplayer.play()
                                }
                            )
                        }
                    }

                    if (isSeries && streamSource?.seasons?.isNotEmpty() == true) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            items(streamSource!!.seasons) { s ->
                                val isSelected = selectedDrawerSeason == s.seasonNumber
                                val isPlayingSeason = currentSeason == s.seasonNumber
                                val interactionSource = remember { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isFocused) Color.White else if (isSelected) Color(0xFF333333) else Color.Transparent)
                                        .border(
                                            width = if (isFocused) 1.5.dp else if (isPlayingSeason) 1.dp else 0.dp,
                                            color = if (isFocused) Color.White else if (isPlayingSeason) Color(0x66FFFFFF) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                isAtDrawerTopState.value = false
                                                selectedDrawerSeason = s.seasonNumber
                                            }
                                        }
                                        .clickable(interactionSource = interactionSource, indication = null) {
                                            selectedDrawerSeason = s.seasonNumber
                                        }
                                        .focusable(interactionSource = interactionSource)
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Сезон ${s.seasonNumber}",
                                        color = if (isFocused) Color.Black else if (isSelected) Color.White else Color(0xFF737373),
                                        fontSize = 17.sp,
                                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        val episodes = streamSource?.seasons?.find { it.seasonNumber == selectedDrawerSeason }?.episodes.orEmpty()
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            items(episodes) { ep ->
                                val isCurrent = currentSeason == selectedDrawerSeason && currentEpisode == ep.episodeNumber
                                HighContrastEpisodeCard(
                                    episodeNumber = ep.episodeNumber,
                                    isCurrent = isCurrent,
                                    posterUrl = poster,
                                    onFocused = { isAtDrawerTopState.value = false },
                                    onClick = {
                                        currentSeason = selectedDrawerSeason
                                        currentEpisode = ep.episodeNumber
                                        isDrawerOpenState.value = false
                                        exoplayer.play()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Audio Selection Modal Dialog
        if (activeDialog == "audio") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .clickable { activeDialog = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isPortrait) 0.92f else 0.45f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(2.dp, Color(0xFF333333), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Выберите озвучку",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    streamSource?.translations?.forEachIndexed { idx, tr ->
                        val isSelected = currentTranslation.contains(tr.name, ignoreCase = true)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        val itemModifier = if (idx == 0) {
                            Modifier.focusRequester(modalFocusRequester)
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = itemModifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isFocused) Color.White else if (isSelected) Color(0xFF333333) else Color(0xFF141414))
                                .border(if (isFocused) 1.5.dp else 1.dp, if (isFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    currentTranslation = tr.name
                                    applyAudioTrack(idx)
                                    activeDialog = null
                                }
                                .focusable(interactionSource = interactionSource)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr.name,
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontWeight = if (isFocused || isSelected) FontWeight.Black else FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Quality Selection Modal Dialog
        if (activeDialog == "quality") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .clickable { activeDialog = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isPortrait) 0.88f else 0.4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(2.dp, Color(0xFF333333), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Качество видео",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    listOf("1080p FHD (Макс.)", "720p HD", "480p SD").forEachIndexed { idx, q ->
                        val isSelected = currentQuality.startsWith(q.take(5))
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        val itemModifier = if (idx == 0) {
                            Modifier.focusRequester(modalFocusRequester)
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = itemModifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isFocused) Color.White else if (isSelected) Color(0xFF333333) else Color(0xFF141414))
                                .border(if (isFocused) 1.5.dp else 1.dp, if (isFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    currentQuality = q
                                    activeDialog = null
                                }
                                .focusable(interactionSource = interactionSource)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = q,
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontWeight = if (isFocused || isSelected) FontWeight.Black else FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Next Episode Prompt Dialog
        if (showNextEpisodeDialog && isSeries) {
            val allSeasons = streamSource?.seasons.orEmpty()
            val currentSeasonData = allSeasons.find { it.seasonNumber == currentSeason }
            val currentEpIndex = currentSeasonData?.episodes?.indexOfFirst { it.episodeNumber == currentEpisode } ?: -1

            var nextS = currentSeason
            var nextEp = currentEpisode + 1
            var hasNext = false

            if (currentSeasonData != null && currentEpIndex != -1) {
                if (currentEpIndex + 1 < currentSeasonData.episodes.size) {
                    nextEp = currentSeasonData.episodes[currentEpIndex + 1].episodeNumber
                    hasNext = true
                } else {
                    val sIdx = allSeasons.indexOfFirst { it.seasonNumber == currentSeason }
                    if (sIdx != -1 && sIdx + 1 < allSeasons.size) {
                        nextS = allSeasons[sIdx + 1].seasonNumber
                        nextEp = allSeasons[sIdx + 1].episodes.firstOrNull()?.episodeNumber ?: 1
                        hasNext = true
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .clickable { showNextEpisodeDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isPortrait) 0.92f else 0.5f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(2.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (hasNext) "Серия закончилась" else "Сериал просмотрен",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Text(
                        text = if (hasNext) "Перейти к следующей серии (Сезон $nextS, Серия $nextEp)?" else "Вы досмотрели все доступные серии.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFAAAAAA),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (hasNext) {
                            val okInteraction = remember { MutableInteractionSource() }
                            val okFocused by okInteraction.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(nextEpisodeFocusRequester)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (okFocused) Color.White else Color(0xFFE50914))
                                    .border(
                                        width = if (okFocused) 1.5.dp else 0.dp,
                                        color = if (okFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(interactionSource = okInteraction, indication = null) {
                                        showNextEpisodeDialog = false
                                        currentSeason = nextS
                                        selectedDrawerSeason = nextS
                                        currentEpisode = nextEp
                                    }
                                    .focusable(interactionSource = okInteraction)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Да, включить",
                                    color = if (okFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        val cancelInteraction = remember { MutableInteractionSource() }
                        val cancelFocused by cancelInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(if (!hasNext) Modifier.focusRequester(nextEpisodeFocusRequester) else Modifier)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (cancelFocused) Color.White else Color(0xFF2A2A2A))
                                .border(
                                    width = if (cancelFocused) 1.5.dp else 1.dp,
                                    color = if (cancelFocused) Color.White else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(interactionSource = cancelInteraction, indication = null) {
                                    showNextEpisodeDialog = false
                                }
                                .focusable(interactionSource = cancelInteraction)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hasNext) "Отмена" else "Закрыть",
                                color = if (cancelFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighContrastButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color(0xFF222222))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF383838),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun HighContrastEpisodeCard(
    episodeNumber: Int,
    isCurrent: Boolean,
    posterUrl: String?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color.Transparent)
            .border(
                width = if (isFocused) 1.5.dp else if (isCurrent) 1.dp else 0.5.dp,
                color = if (isFocused) Color.White else if (isCurrent) Color(0x99FFFFFF) else Color(0xFF2E2E2E),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(if (isFocused) 4.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) Color.Black else Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) Color.Black else Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Сейчас",
                        color = if (isFocused) Color.White else Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Серия $episodeNumber",
            color = if (isFocused) Color.Black else Color(0xFFAAAAAA),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hrs = totalSec / 3600
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
