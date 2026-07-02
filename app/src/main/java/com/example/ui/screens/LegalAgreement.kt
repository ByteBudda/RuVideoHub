package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary

/**
 * ПОЛЬЗОВАТЕЛЬСКОЕ СОГЛАШЕНИЕ
 * 
 * Версия: 1.0.0
 * Дата: 01.07.2026
 */

data class LegalSection(
    val id: String,
    val title: String,
    val content: String,
    val subsections: List<LegalSubsection> = emptyList()
)

data class LegalSubsection(
    val title: String,
    val content: String
)

object LegalContent {
    
    const val APP_NAME = "RuVideoHub"
    const val GITHUB_URL = "https://github.com/ByteBudda/RuVideoHub"
    const val LICENSE = "GNU General Public License v3.0"
    const val VERSION = "3.4.54"
    const val CONTACT_EMAIL = "anonymous"
    const val COPYRIGHT_YEAR = "2026"

    val sections = listOf(
        LegalSection(
            id = "disclaimer",
            title = "⚠️ ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ",
            content = """
                Данное приложение является НЕОФИЦИАЛЬНЫМ клиентом для видеохостинга RUTUBE.
                
                Приложение разработано независимым разработчиком (сообществом разработчиков) в образовательных и исследовательских целях.
                
                Приложение НЕ связано, НЕ аффилировано и НЕ поддерживается:
                • RUTUBE
                • Газпром-Медиа Холдинг
                • Любыми другими дочерними компаниями или юридическими лицами
                
                Все товарные знаки, логотипы и бренды являются собственностью их соответствующих владельцев.
            """.trimIndent(),
            subsections = listOf(
                LegalSubsection(
                    title = "Статус приложения",
                    content = """
                        Приложение является ОТКРЫТЫМ ПРОГРАММНЫМ ОБЕСПЕЧЕНИЕМ (Open Source).
                        Исходный код доступен для ознакомления, модификации и распространения в соответствии с условиями лицензии GNU GPL v3.0.
                        
                        Приложение НЕ является официальным продуктом RUTUBE и НЕ предоставляет никаких гарантий, явных или подразумеваемых.
                    """.trimIndent()
                ),
                LegalSubsection(
                    title = "Использование API",
                    content = """
                        Приложение использует открытый API видеохостинга RUTUBE для отображения публично доступного контента.
                        
                        Разработчик НЕ несет ответственности за:
                        • Доступность API
                        • Изменения в работе API
                        • Блокировки или ограничения со стороны RUTUBE
                        • Любые убытки, связанные с использованием приложения
                    """.trimIndent()
                )
            )
        ),
        
        LegalSection(
            id = "opensource",
            title = "📦 OPEN SOURCE ЛИЦЕНЗИЯ",
            content = """
                Данное приложение распространяется под лицензией GNU GPL v3.0.
                
                Авторские права (c) $COPYRIGHT_YEAR RuVideoHub
                
                Это свободное программное обеспечение: вы можете распространять и/или изменять его на условиях GNU General Public License в том виде, в каком она опубликована Фондом свободного программного обеспечения, либо версии 3 лицензии, либо (по вашему выбору) любой более поздней версии.
                
                КОММЕРЧЕСКОЕ ИСПОЛЬЗОВАНИЕ ЗАПРЕЩЕНО!
                Приложение предназначено исключительно для личного, некоммерческого использования.
            """.trimIndent(),
            subsections = listOf(
                LegalSubsection(
                    title = "Условия использования",
                    content = """
                        Вы можете свободно:
                        • Использовать приложение в личных целях
                        • Изучать исходный код
                        • Модифицировать код под свои нужды
                        • Распространять копии приложения БЕСПЛАТНО
                        
                        ЗАПРЕЩАЕТСЯ:
                        • Продажа приложения или его копий
                        • Использование в коммерческих целях
                        • Удаление уведомлений об авторских правах
                        • Использование без сохранения лицензии GPL
                    """.trimIndent()
                ),
                LegalSubsection(
                    title = "Отказ от гарантий",
                    content = """
                        ПРОГРАММНОЕ ОБЕСПЕЧЕНИЕ ПРЕДОСТАВЛЯЕТСЯ «КАК ЕСТЬ», БЕЗ КАКИХ-ЛИБО ГАРАНТИЙ, ЯВНЫХ ИЛИ ПОДРАЗУМЕВАЕМЫХ, ВКЛЮЧАЯ, НО НЕ ОГРАНИЧИВАЯСЬ ГАРАНТИЯМИ ТОВАРНОЙ ПРИГОДНОСТИ, СООТВЕТСТВИЯ ОПРЕДЕЛЕННОМУ НАЗНАЧЕНИЮ И НЕНАРУШЕНИЯ ПРАВ.
                        
                        Ни при каких обстоятельствах авторы или правообладатели не несут ответственности ни перед кем за любой ущерб, включая общий, специальный, случайный или косвенный ущерб, возникший в результате использования или невозможности использования программного обеспечения.
                    """.trimIndent()
                )
            )
        ),
        
        LegalSection(
            id = "non_commercial",
            title = "🚫 НЕКОММЕРЧЕСКОЕ ИСПОЛЬЗОВАНИЕ",
            content = """
                Приложение распространяется БЕСПЛАТНО и предназначено ИСКЛЮЧИТЕЛЬНО для ЛИЧНОГО НЕКОММЕРЧЕСКОГО использования.
                
                КАТЕГОРИЧЕСКИ ЗАПРЕЩАЕТСЯ:
                • Продажа приложения (полная или частичная)
                • Использование приложения в коммерческих проектах
                • Встраивание приложения в коммерческие продукты
                • Использование для получения прибыли любым способом
                • Сублицензирование и передача прав на коммерческой основе
            """.trimIndent(),
            subsections = listOf(
                LegalSubsection(
                    title = "Исключения",
                    content = """
                        Единственным исключением является возможность получения добровольных пожертвований на развитие приложения.
                        Пожертвования НЕ являются платой за использование и НЕ дают коммерческих прав на приложение.
                    """.trimIndent()
                )
            )
        ),
        
        LegalSection(
            id = "privacy",
            title = "🔒 КОНФИДЕНЦИАЛЬНОСТЬ",
            content = """
                Приложение НЕ собирает, НЕ хранит и НЕ передает персональные данные пользователей.Приложение не отправляет метрики и аналитику. 
                
                Приложение может использовать:
                • Локальное хранение настроек (SharedPreferences)
                • Кэширование данных для улучшения производительности
                • API видеохостинга для получения публичного контента
            """.trimIndent(),
            subsections = listOf(
                LegalSubsection(
                    title = "Данные пользователя",
                    content = """
                        Приложение НЕ запрашивает и НЕ хранит:
                        • Логин/пароль от RUTUBE
                        • Персональные данные (ФИО, адрес, телефон и т.д.)
                        • Данные банковских карт
                        • Историю просмотров (за исключением локального кэша)
                        
                        Вся информация, сохраняемая приложением, хранится локально на устройстве пользователя.
                    """.trimIndent()
                )
            )
        ),
        
        LegalSection(
            id = "limitations",
            title = "⚖️ ОГРАНИЧЕНИЕ ОТВЕТСТВЕННОСТИ",
            content = """
                Разработчик НЕ несет ответственности за:
                • Прямые или косвенные убытки, возникшие при использовании приложения
                • Потерю данных
                • Несовместимость с устройствами или операционными системами
                • Блокировку или ограничение доступа к видеохостингу
                • Действия третьих лиц
                • Содержание видео, отображаемого через API RUTUBE
            """.trimIndent(),
            subsections = listOf(
                LegalSubsection(
                    title = "Пределы ответственности",
                    content = """
                        В случае возникновения любых споров, претензий или требований, связанных с использованием приложения, максимальная ответственность разработчика ограничивается стоимостью приложения (которая равна 0, так как приложение распространяется БЕСПЛАТНО).
                    """.trimIndent()
                )
            )
        ),
        
        LegalSection(
            id = "third_party",
            title = "🔗 СТОРОННИЕ СЕРВИСЫ",
            content = """
                Приложение взаимодействует со сторонними сервисами:
                
                1. RUTUBE API - для получения видео и данных

                
                Разработчик НЕ контролирует и НЕ несет ответственности за содержание, политику конфиденциальности или действия этих сторонних сервисов.
            """.trimIndent(),
            subsections = listOf(
                LegalSubsection(
                    title = "Ссылки на сторонние ресурсы",
                    content = """
                        Приложение может содержать ссылки на сторонние ресурсы.
                        Переход по таким ссылкам осуществляется на усмотрение пользователя. Разработчик НЕ несет ответственности за содержание этих ресурсов.
                    """.trimIndent()
                )
            )
        ),
        
        LegalSection(
            id = "copyright",
            title = "©️ АВТОРСКИЕ ПРАВА",
            content = """
                Все права на контент, отображаемый в приложении (видео, изображения, названия, описания), принадлежат их законным владельцам.
                
                Приложение НЕ загружает и НЕ хранит контент на своих серверах.
                Весь контент транслируется напрямую с серверов RUTUBE.
                
                В случае нарушения авторских прав просьба обращаться непосредственно к RUTUBE.
            """.trimIndent()
        ),
        
        LegalSection(
            id = "changes",
            title = "📝 ИЗМЕНЕНИЯ В СОГЛАШЕНИИ",
            content = """
                Разработчик оставляет за собой право вносить изменения в данное пользовательское соглашение без предварительного уведомления.
                
                Актуальная версия соглашения всегда доступна в приложении.
                Продолжая использовать приложение после внесения изменений, вы автоматически принимаете новые условия.
            """.trimIndent()
        ),
        
        LegalSection(
            id = "contact",
            title = "📧 КОНТАКТЫ",
            content = """
                По всем вопросам, связанным с данным приложением, вы можете обратиться:
                
                • Email: $CONTACT_EMAIL
                • GitHub: $GITHUB_URL
                
                По вопросам авторских прав или нарушений просьба обращаться непосредственно к правообладателям.
                
                Разработчик открыт к сотрудничеству и конструктивному диалогу.
            """.trimIndent()
        )
    )
}

@Composable
fun TermsAgreementScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    var agreed by remember { mutableStateOf(false) }
    var showFullAgreement by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .systemBarsPadding()
    ) {
        // Заголовок
        Text(
            text = "Пользовательское соглашение",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Пожалуйста, ознакомьтесь с условиями использования",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Основной текст соглашения
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ключевые пункты
                item {
                    KeyPointCard(
                        icon = "⚠️",
                        title = "НЕОФИЦИАЛЬНОЕ ПРИЛОЖЕНИЕ",
                        description = "Приложение НЕ связано с RUTUBE или Газпром-Медиа"
                    )
                }
                
                item {
                    KeyPointCard(
                        icon = "📦",
                        title = "OPEN SOURCE",
                        description = "Исходный код открыт под лицензией GNU GPL v3.0"
                    )
                }
                
                item {
                    KeyPointCard(
                        icon = "🚫",
                        title = "НЕКОММЕРЧЕСКОЕ ИСПОЛЬЗОВАНИЕ",
                        description = "Продажа и коммерческое использование ЗАПРЕЩЕНЫ"
                    )
                }
                
                item {
                    KeyPointCard(
                        icon = "🔒",
                        title = "КОНФИДЕНЦИАЛЬНОСТЬ",
                        description = "Приложение НЕ собирает персональные данные"
                    )
                }
                
                item {
                    KeyPointCard(
                        icon = "⚖️",
                        title = "ОГРАНИЧЕНИЕ ОТВЕТСТВЕННОСТИ",
                        description = "Разработчик не несет ответственности за убытки"
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showFullAgreement = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_read_full_agreement")
                    ) {
                        Text("📄 Прочитать полное соглашение", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Чекбокс
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { agreed = !agreed }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = { agreed = it },
                modifier = Modifier.testTag("cb_accept_terms")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Я ознакомился(ась) и принимаю условия использования",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // Кнопки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f).testTag("btn_decline_terms"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Отказаться")
            }
            
            Button(
                onClick = onAgree,
                modifier = Modifier.weight(1f).testTag("btn_agree_terms"),
                enabled = agreed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Принимаю")
            }
        }
    }

    if (showFullAgreement) {
        FullAgreementDialog(onDismiss = { showFullAgreement = false })
    }
}

@Composable
fun KeyPointCard(
    icon: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineSmall
            )
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullAgreementDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Пользовательское соглашение",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_dialog")) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Приложение: ${LegalContent.APP_NAME}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Версия: ${LegalContent.VERSION} | Лицензия: ${LegalContent.LICENSE}",
                            fontSize = 11.sp,
                            color = GreyText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }

                items(LegalContent.sections) { section ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = section.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = section.content,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            if (section.subsections.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                section.subsections.forEach { sub ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = sub.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = sub.content,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(160.dp)
                        ) {
                            Text("Закрыть")
                        }
                    }
                }
            }
        }
    }
}
