package com.korobka.tv.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.korobka.tv.data.AppStorage
import com.korobka.tv.data.MediaItem
import com.korobka.tv.data.PlaybackHistoryItem
import com.korobka.tv.engine.CdnEngine
import com.korobka.tv.player.NativePlayerActivity
import kotlinx.coroutines.launch

@Composable
fun MainTvScreen() {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("home") }
    var history by remember { mutableStateOf(storage.getHistory()) }
    var favorites by remember { mutableStateOf(storage.getFavorites()) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val latestWatched = history.firstOrNull()

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val screenHorizontalPadding = if (isPortrait) 16.dp else 48.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = screenHorizontalPadding,
                        end = screenHorizontalPadding,
                        top = if (isPortrait) 36.dp else 24.dp,
                        bottom = if (isPortrait) 16.dp else 24.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isPortrait) 32.dp else 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                        )
                    }

                    Text(
                        text = "Коробка",
                        fontSize = if (isPortrait) 20.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumNavTab(
                        title = "Главная",
                        icon = Icons.Default.Home,
                        isSelected = activeTab == "home",
                        onClick = {
                            activeTab = "home"
                            history = storage.getHistory()
                            favorites = storage.getFavorites()
                        }
                    )
                    PremiumNavTab(
                        title = "Поиск",
                        icon = Icons.Default.Search,
                        isSelected = activeTab == "search",
                        onClick = { activeTab = "search" }
                    )
                }
            }

            if (activeTab == "home") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 56.dp)
                ) {
                    // Big Hero Card (latest watched item)
                    if (latestWatched != null) {
                        item {
                            PremiumHeroBanner(
                                item = latestWatched,
                                isPortrait = isPortrait,
                                onResume = {
                                    NativePlayerActivity.start(
                                        context = context,
                                        itemId = latestWatched.itemId,
                                        title = latestWatched.title,
                                        poster = latestWatched.poster,
                                        isSeries = latestWatched.season != null,
                                        season = latestWatched.season ?: 1,
                                        episode = latestWatched.episode ?: 1,
                                        initialPositionMs = latestWatched.positionMs,
                                        initialTranslation = latestWatched.translation
                                    )
                                }
                            )
                        }
                    }

                    // Section 1: Continue Watching
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenHorizontalPadding, vertical = if (isPortrait) 12.dp else 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                                )
                                Text(
                                    text = "Продолжить просмотр",
                                    fontSize = if (isPortrait) 17.sp else 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (history.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF222222))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Вы ещё ничего не смотрели. Найдите фильм или сериал через поиск!",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 14.dp else 20.dp)) {
                                    items(history) { h ->
                                        PremiumContinueCard(
                                            item = h,
                                            isPortrait = isPortrait,
                                            onClick = {
                                                NativePlayerActivity.start(
                                                    context = context,
                                                    itemId = h.itemId,
                                                    title = h.title,
                                                    poster = h.poster,
                                                    isSeries = h.season != null,
                                                    season = h.season ?: 1,
                                                    episode = h.episode ?: 1,
                                                    initialPositionMs = h.positionMs,
                                                    initialTranslation = h.translation
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Favorites
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenHorizontalPadding, vertical = if (isPortrait) 12.dp else 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                                )
                                Text(
                                    text = "Избранное",
                                    fontSize = if (isPortrait) 17.sp else 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (favorites.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF222222))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Список избранного пуст. Добавляйте фильмы во время просмотра!",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 14.dp else 20.dp)) {
                                    items(favorites) { f ->
                                        PremiumPosterCard(
                                            item = f,
                                            isPortrait = isPortrait,
                                            onClick = {
                                                NativePlayerActivity.start(
                                                    context = context,
                                                    itemId = f.id,
                                                    title = f.title,
                                                    poster = f.poster,
                                                    isSeries = f.isSeries
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Search View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 64.dp)
                ) {
                    item {
                        NativeSearchKeyboard(
                            query = searchQuery,
                            isPortrait = isPortrait,
                            onQueryChange = { searchQuery = it },
                            onSearch = {
                                scope.launch {
                                    isSearching = true
                                    searchResults = CdnEngine.search(searchQuery)
                                    isSearching = false
                                }
                            }
                        )
                    }

                    if (isSearching) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    } else if (searchResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Результаты (${searchResults.size}):",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        if (isPortrait) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    val chunked = searchResults.chunked(2)
                                    chunked.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            rowItems.forEach { m ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    PremiumPosterCard(
                                                        item = m,
                                                        isPortrait = true,
                                                        onClick = {
                                                            NativePlayerActivity.start(
                                                                context = context,
                                                                itemId = m.id,
                                                                title = m.title,
                                                                poster = m.poster,
                                                                isSeries = m.isSeries
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(searchResults) { m ->
                                        PremiumPosterCard(
                                            item = m,
                                            isPortrait = false,
                                            onClick = {
                                                NativePlayerActivity.start(
                                                    context = context,
                                                    itemId = m.id,
                                                    title = m.title,
                                                    poster = m.poster,
                                                    isSeries = m.isSeries
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumNavTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else if (isSelected) Color(0xFF333333) else Color(0xFF222222))
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 10.dp)
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
                text = title,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun PremiumHeroBanner(item: PlaybackHistoryItem, isPortrait: Boolean = false, onResume: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isPortrait) 220.dp else 290.dp)
            .padding(horizontal = if (isPortrait) 16.dp else 48.dp)
            .clip(RoundedCornerShape(if (isPortrait) 16.dp else 22.dp))
            .background(Color(0xFF141414))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(if (isPortrait) 16.dp else 22.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onResume() }
            .focusable(interactionSource = interactionSource)
    ) {
        // 1. Blurred Poster Background (heavily blurred backdrop)
        if (item.poster != null) {
            AsyncImage(
                model = item.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 32.dp)
            )
        }

        // 2. Cinematic Gradient overlay: dark on the left for text readability, fading softly to the right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFA121212),
                            Color(0xEE121212),
                            Color(0xB3121212),
                            Color(0x66121212)
                        )
                    )
                )
        )

        // 3. Foreground Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPortrait) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Content: Title, season/episode, play button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = if (isPortrait) 12.dp else 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    fontSize = if (isPortrait) 20.sp else 30.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = if (isPortrait) 24.sp else 36.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (item.season != null && item.episode != null) "Сезон ${item.season} • Серия ${item.episode}" else "Фильм",
                    fontSize = if (isPortrait) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFAAAAAA)
                )

                Spacer(modifier = Modifier.height(if (isPortrait) 14.dp else 22.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isFocused) Color.White else Color(0xFFE50914))
                        .padding(horizontal = if (isPortrait) 18.dp else 26.dp, vertical = if (isPortrait) 9.dp else 13.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White,
                            modifier = Modifier.size(if (isPortrait) 18.dp else 22.dp)
                        )
                        Text(
                            text = "Смотреть",
                            color = if (isFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isPortrait) 13.sp else 15.sp
                        )
                    }
                }
            }

            // Right Poster: Full vertical 2:3 poster sharp and clean
            if (item.poster != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumContinueCard(item: PlaybackHistoryItem, isPortrait: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .then(if (isPortrait) Modifier.width(140.dp) else Modifier.width(160.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color.White else Color(0xFF262626))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (item.season != null) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (item.season != null) "Сериал" else "Фильм",
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress bar line at the bottom of the poster
            if (item.durationMs > 0) {
                val progress = (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x88000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Color(0xFFE50914))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            maxLines = 2,
            minLines = 2,
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
        Text(
            text = if (item.season != null) "С${item.season} • Серия ${item.episode}" else "Фильм",
            maxLines = 1,
            color = if (isFocused) Color(0xFF444444) else Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PremiumPosterCard(item: MediaItem, isPortrait: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .then(if (isPortrait) Modifier.fillMaxWidth() else Modifier.width(160.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color.White else Color(0xFF262626))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (item.isSeries) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (item.isSeries) "Сериал" else "Фильм",
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            maxLines = 2,
            minLines = 2,
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
        if (item.description != null) {
            Text(
                text = item.description,
                maxLines = 1,
                color = if (isFocused) Color(0xFF444444) else Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NativeSearchKeyboard(
    query: String,
    isPortrait: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF222222))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(if (isPortrait) 14.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Native touch text input for smartphones & tablets
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск фильмов и сериалов...", color = Color(0xFF737373)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0x44FFFFFF),
                cursorColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    onSearch()
                }
            ),
            trailingIcon = {
                IconButton(onClick = {
                    keyboardController?.hide()
                    onSearch()
                }) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Поиск", tint = Color.White)
                }
            }
        )

        // D-pad onscreen keyboard (shown on landscape / TV screen)
        if (!isPortrait) {
            val rows = listOf(
                listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш", "Щ", "З", "Х"),
                listOf("Ф", "Ы", "В", "А", "П", "Р", "О", "Л", "Д", "Ж", "Э"),
                listOf("Я", "Ч", "С", "М", "И", "Т", "Ь", "Б", "Ю", "1", "2")
            )

            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { char ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) Color.White else Color(0xFF333333))
                                .border(
                                    width = if (isFocused) 1.5.dp else 0.5.dp,
                                    color = if (isFocused) Color.White else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(interactionSource = interactionSource, indication = null) { onQueryChange(query + char) }
                                .focusable(interactionSource = interactionSource),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                color = if (isFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val spaceInteractionSource = remember { MutableInteractionSource() }
                val isSpaceFocused by spaceInteractionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSpaceFocused) Color.White else Color(0xFF333333))
                        .border(if (isSpaceFocused) 1.5.dp else 1.dp, if (isSpaceFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                        .clickable(interactionSource = spaceInteractionSource, indication = null) { onQueryChange("$query ") }
                        .focusable(interactionSource = spaceInteractionSource),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Пробел",
                        color = if (isSpaceFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                val delInteractionSource = remember { MutableInteractionSource() }
                val isDelFocused by delInteractionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDelFocused) Color.White else Color(0xFF333333))
                        .border(if (isDelFocused) 1.5.dp else 1.dp, if (isDelFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                        .clickable(interactionSource = delInteractionSource, indication = null) { if (query.isNotEmpty()) onQueryChange(query.dropLast(1)) }
                        .focusable(interactionSource = delInteractionSource),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Стереть",
                        color = if (isDelFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                val searchInteractionSource = remember { MutableInteractionSource() }
                val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSearchFocused) Color.White else Color(0xFF444444))
                        .border(if (isSearchFocused) 1.5.dp else 1.dp, if (isSearchFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                        .clickable(interactionSource = searchInteractionSource, indication = null) { onSearch() }
                        .focusable(interactionSource = searchInteractionSource),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Найти",
                        color = if (isSearchFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
