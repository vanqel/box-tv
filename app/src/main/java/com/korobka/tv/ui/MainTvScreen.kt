package com.korobka.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "Коробка",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                .padding(horizontal = 48.dp, vertical = 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Продолжить просмотр",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            if (history.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF222222))
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Вы ещё ничего не смотрели. Найдите фильм или сериал через поиск!",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    items(history) { h ->
                                        PremiumContinueCard(
                                            item = h,
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
                                .padding(horizontal = 48.dp, vertical = 20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Избранное",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            if (favorites.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF222222))
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Список избранного пуст. Добавляйте фильмы во время просмотра!",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    items(favorites) { f ->
                                        PremiumPosterCard(
                                            item = f,
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    NativeSearchKeyboard(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            scope.launch {
                                isSearching = true
                                searchResults = CdnEngine.search(searchQuery)
                                isSearching = false
                            }
                        }
                    )

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "Результаты (${searchResults.size}):",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(searchResults) { m ->
                                PremiumPosterCard(
                                    item = m,
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
fun PremiumHeroBanner(item: PlaybackHistoryItem, onResume: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .padding(horizontal = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF222222))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onResume() }
            .focusable(interactionSource = interactionSource)
    ) {
        if (item.poster != null) {
            AsyncImage(
                model = item.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF5141414),
                            Color(0xB3141414),
                            Color(0x33141414),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Text(
                text = if (item.season != null && item.episode != null) "Сезон ${item.season}, Серия ${item.episode}" else "Фильм",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAAAAAA)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Продолжить просмотр",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumContinueCard(item: PlaybackHistoryItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(220.dp)
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
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141414))
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) Color.Black else Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            maxLines = 1,
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = if (item.season != null) "С${item.season} • Серия ${item.episode}" else "Фильм",
            color = if (isFocused) Color(0xFF333333) else Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PremiumPosterCard(item: MediaItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(160.dp)
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
                .background(Color(0xFF141414))
        ) {
            if (item.poster != null) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            maxLines = 1,
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun NativeSearchKeyboard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val rows = listOf(
        listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш", "Щ", "З", "Х"),
        listOf("Ф", "Ы", "В", "А", "П", "Р", "О", "Л", "Д", "Ж", "Э"),
        listOf("Я", "Ч", "С", "М", "И", "Т", "Ь", "Б", "Ю", "1", "2")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF222222))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (query.isEmpty()) "Введите название фильма..." else query,
            fontSize = 20.sp,
            color = if (query.isEmpty()) Color(0xFF737373) else Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
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
