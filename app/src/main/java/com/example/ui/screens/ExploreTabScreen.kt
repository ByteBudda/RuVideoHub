package com.example.ui.screens

import com.example.ui.screens.player.*

import com.example.viewmodel.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreyText
import com.example.ui.theme.SurfaceVariant
import com.example.viewmodel.VideoViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun ExploreTabScreen(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val realCategories by viewModel.realCategories.collectAsStateWithLifecycle()
    val isCategoriesLoading by viewModel.isCategoriesLoading.collectAsStateWithLifecycle()
    val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()

    val getCategoryGradient = { title: String ->
        val colors = listOf(
            listOf(Color(0xFF8B5CF6), Color(0xFF3B0764)), // Purple
            listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)), // Blue
            listOf(Color(0xFFEC4899), Color(0xFF9D174D)), // Pink
            listOf(Color(0xFF10B981), Color(0xFF047857)), // Emerald
            listOf(Color(0xFFF59E0B), Color(0xFFB45309)), // Amber
            listOf(Color(0xFFEF4444), Color(0xFF991B1B))  // Red
        )
        val index = kotlin.math.abs(title.hashCode()) % colors.size
        Brush.linearGradient(colors = colors[index])
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
            .verticalScroll(scrollState)
            .mouseDragScrollable(scrollState, isVertical = true)
    ) {
        Text(
            text = "Проводник",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Исследуйте полный каталог категорий, трансляций и шоу в реальном времени",
            fontSize = 12.sp,
            color = GreyText,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            lineHeight = 16.sp
        )

        if (isCategoriesLoading && realCategories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            val cols = if (isTvOptimized) LocalTvGridColumns.current else LocalMobileGridColumns.current
            val chunkedCategories = remember(realCategories, cols) { realCategories.chunked(cols) }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (rowItems in chunkedCategories) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(115.dp)
                                    .sleekTvFocus(shape = RoundedCornerShape(16.dp), scaleAmount = 1.05f, onEnter = {
                                        viewModel.selectCategory(item.title, item.target)
                                        viewModel.selectTab("home")
                                    })
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(getCategoryGradient(item.title))
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.1f),
                                                Color.Black.copy(alpha = 0.25f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        viewModel.selectCategory(item.title, item.target)
                                        viewModel.selectTab("home")
                                    }
                            ) {
                                AsyncImage(
                                    model = item.picture,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                                startY = 50f
                                            )
                                        )
                                )
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                        repeat(cols - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
