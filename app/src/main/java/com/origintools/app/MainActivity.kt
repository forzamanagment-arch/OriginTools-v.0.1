package com.origintools.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.rememberCoroutineScope
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.materials.HazeMaterials
import com.origintools.app.service.IRemoteCommand
import com.origintools.app.service.ShellUserService
import com.origintools.app.ui.theme.OriginTheme
import rikka.shizuku.Shizuku
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val REQUEST_SHIZUKU = 4201
private const val FINGERPRINT_KEY = "fingerprint_icon_style_resource_a"
private val PageBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF121212)
private val SecondaryText = Color(0xFF929292)

class MainActivity : ComponentActivity() {
    private var shizukuGranted by mutableStateOf(false)
    private var remoteCommand: IRemoteCommand? = null
    private var serviceBinding = false
    private val commandQueue = ArrayDeque<Pair<Array<String>, Array<String>>>()
    private val commandExecutor = Executors.newSingleThreadExecutor()

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("commands")
            .debuggable(BuildConfig.DEBUG)
            .version(2)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteCommand = IRemoteCommand.Stub.asInterface(service)
            serviceBinding = false
            drainCommandQueue()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteCommand = null
            serviceBinding = false
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener { refreshShizukuState() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_SHIZUKU) {
            refreshShizukuState()
            val message = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                "Права Shizuku предоставлены"
            } else {
                "Доступ Shizuku не предоставлен"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizukuState()

        setContent {
            OriginTheme {
                OriginApp(
                    shizukuGranted = shizukuGranted,
                    onRequestShizuku = ::requestShizuku,
                    onWriteFingerprintSetting = ::writeFingerprintSetting
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuState()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        runCatching { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) }
        commandExecutor.shutdown()
        super.onDestroy()
    }

    private fun refreshShizukuState() {
        shizukuGranted = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Сначала запустите Shizuku", Toast.LENGTH_LONG).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshShizukuState()
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU)
            }
        } catch (error: Throwable) {
            Toast.makeText(
                this,
                "Shizuku: ${error.message ?: "ошибка подключения"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun writeFingerprintSetting(value: String?) {
        if (!shizukuGranted) {
            Toast.makeText(this, "Сначала предоставьте права Shizuku", Toast.LENGTH_SHORT).show()
            return
        }

        val writeCommand = if (value == null) {
            arrayOf("settings", "delete", "secure", FINGERPRINT_KEY)
        } else {
            // A single argv item preserves the intentional one-space value.
            arrayOf("settings", "put", "secure", FINGERPRINT_KEY, value)
        }
        val refreshCommand = arrayOf(
            "am", "broadcast", "-a",
            "android.intent.action.CONFIGURATION_CHANGED"
        )

        synchronized(commandQueue) {
            commandQueue.addLast(writeCommand to refreshCommand)
        }
        ensureUserService()
    }

    private fun ensureUserService() {
        if (remoteCommand != null) {
            drainCommandQueue()
            return
        }
        if (serviceBinding) return
        try {
            serviceBinding = true
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (error: Throwable) {
            serviceBinding = false
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Не удалось запустить Shizuku shell: ${error.message ?: "ошибка"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun drainCommandQueue() {
        val service = remoteCommand ?: return
        val tasks = mutableListOf<Pair<Array<String>, Array<String>>>()
        synchronized(commandQueue) {
            while (commandQueue.isNotEmpty()) tasks.add(commandQueue.removeFirst())
        }
        if (tasks.isEmpty()) return

        commandExecutor.execute {
            tasks.forEach { (writeCommand, refreshCommand) ->
                val writeResult = runCatching { service.execute(writeCommand) }.getOrDefault(-1)
                val refreshResult = if (writeResult == 0) {
                    runCatching { service.execute(refreshCommand) }.getOrDefault(-1)
                } else {
                    -1
                }
                runOnUiThread {
                    val message = when {
                        writeResult != 0 -> "Система отклонила запись настройки (код $writeResult)"
                        refreshResult != 0 -> "Значение записано, но обновление интерфейса не подтверждено"
                        else -> "Настройка записана и отправлено обновление конфигурации"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

private enum class AppTab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Filled.Home),
    MODS("Модификации", Icons.Filled.Extension),
    SETTINGS("Настройки", Icons.Filled.Settings),
    ABOUT("Информация", Icons.Filled.Info)
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun OriginApp(
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onWriteFingerprintSetting: (String?) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { AppTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val hazeState = rememberHazeState()

    Box(Modifier.fillMaxSize().background(PageBackground)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            key = { it }
        ) { page ->
            when (AppTab.entries[page]) {
                AppTab.HOME -> HomeScreen(shizukuGranted, onRequestShizuku)
                AppTab.MODS -> ModsScreen(shizukuGranted, onRequestShizuku, onWriteFingerprintSetting)
                AppTab.SETTINGS -> SettingsScreen()
                AppTab.ABOUT -> AboutScreen()
            }
        }

        GlassNavigationBar(
            pagerState = pagerState,
            onSelect = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HomeScreen(
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    val kernelVersion = System.getProperty("os.version") ?: "Неизвестно"
    val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
        .ifBlank { "Неизвестно" }
    val osVersion = "Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY.ifBlank { "API ${Build.VERSION.SDK_INT}" }}"

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                "Origin Tools v0.01 BETA",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
        item { ShizukuStatusCard(shizukuGranted, onRequestShizuku) }
        item {
            Card(
                onClick = { showInfo = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 19.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "一键越狱",
                        color = Color(0xFF68A7FF),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF68A7FF))
                }
            }
        }
        item {
            Text(
                "СИСТЕМА",
                color = Color(0xFF858585),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    TechnicalRow(Icons.Filled.Memory, "内核版本", kernelVersion)
                    HorizontalDivider(color = Color(0xFF292929), thickness = 0.7.dp)
                    TechnicalRow(
                        Icons.Filled.Code,
                        "漏洞利用方式",
                        if (shizukuGranted) "Shizuku / ADB shell" else "Не активен"
                    )
                    HorizontalDivider(color = Color(0xFF292929), thickness = 0.7.dp)
                    TechnicalRow(Icons.Filled.Smartphone, "设备型号", deviceModel)
                    HorizontalDivider(color = Color(0xFF292929), thickness = 0.7.dp)
                    TechnicalRow(Icons.Filled.Info, "Версия ОС", osVersion)
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("一键越狱") },
            text = {
                Text("Этот пункт пока информационный. OriginTools не запускает эксплойты и не пытается получать Root автоматически.")
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Понятно") }
            },
            containerColor = Color(0xFF202020),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD0D0D0)
        )
    }
}

@Composable
private fun ShizukuStatusCard(granted: Boolean, onRequest: () -> Unit) {
    val green = Color(0xFF39D353)
    val red = Color(0xFFFF4D4D)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (granted) Color(0xFF1A2F1A) else Color(0xFF351919)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (granted) "Shizuku работает" else "Требуется Shizuku",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    if (granted) "Shizuku запущен, все функции работают"
                    else "Запустите Shizuku и предоставьте доступ приложению",
                    color = Color(0xFFB8B8B8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRequest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB83232),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Предоставить права", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(58.dp).clip(CircleShape)
                    .background(if (granted) green else Color(0xFF542020)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = if (granted) "Активно" else "Неактивно",
                    tint = if (granted) Color.White else red,
                    modifier = Modifier.size(39.dp)
                )
            }
        }
    }
}

@Composable
private fun TechnicalRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF969696), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = Color(0xFFE0E0E0),
            fontSize = 13.sp,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            value,
            color = Color(0xFF969696),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun ModsScreen(
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onWriteSetting: (String?) -> Unit
) {
    var removeIcon by remember { mutableStateOf(false) }
    var iconValue by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Модификации", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text("Настройки интерфейса устройства", color = SecondaryText)
            }
        }
        if (!shizukuGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF351919)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Функции заблокированы", color = Color(0xFFFF7777), fontWeight = FontWeight.Bold)
                        Text(
                            "Элементы модификаций станут доступны после выдачи разрешения Shizuku.",
                            color = Color(0xFFE0D4D4)
                        )
                        Button(
                            onClick = onRequestShizuku,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Подключить Shizuku")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(18.dp).then(
                        if (shizukuGranted) Modifier else Modifier.background(
                            Color.Transparent
                        )
                    ),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Удалить иконку отпечатка",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = removeIcon,
                            enabled = shizukuGranted,
                            onCheckedChange = {
                                removeIcon = it
                                onWriteSetting(if (it) " " else null)
                            }
                        )
                    }
                    Text(
                        "Переключатель записывает пробел в secure setting. При отключении ключ удаляется.",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                    HorizontalDivider(color = Color(0xFF303030))
                    Text("Поменять иконку отпечатка", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = iconValue,
                        onValueChange = { newValue ->
                            iconValue = newValue.take(3)
                        },
                        enabled = shizukuGranted,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Значение, максимум 3 символа") },
                        supportingText = { Text("${iconValue.length}/3") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = { removeIcon = false; onWriteSetting(iconValue) },
                        enabled = shizukuGranted && iconValue.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Применить")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { Text("Настройки", style = MaterialTheme.typography.headlineLarge, color = Color.White) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Язык приложения", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Русский", color = SecondaryText, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { Text("Информация", style = MaterialTheme.typography.headlineLarge, color = Color.White) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Origin Tools", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("0.01 BETA", color = Color(0xFF39D353), fontWeight = FontWeight.Bold)
                    Text("Инструменты диагностики и настройки Android.", color = Color(0xFFB7B7B7))
                }
            }
        }
        item {
            LinkRow(Icons.Filled.Code, "GitHub репозиторий", "Исходный код OriginTools") {
                openUrl(context, "https://github.com/forzamanagment-arch/OriginTools-v.0.1")
            }
        }
        item {
            LinkRow(Icons.Filled.Info, "Профиль разработчика", "forzamanagment-arch") {
                openUrl(context, "https://github.com/forzamanagment-arch")
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFD8D8D8))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = SecondaryText, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = SecondaryText)
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun GlassNavigationBar(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(36.dp)
    Box(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .height(76.dp)
            .shadow(24.dp, shape)
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(containerColor = Color(0xCC17191C))
            ) {
                blurRadius = 28.dp
            }
            .background(Color(0x9917191C), shape)
            .border(1.dp, Color.White.copy(alpha = 0.19f), shape)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
            val itemWidth = maxWidth / AppTab.entries.size
            val density = LocalDensity.current
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (AppTab.entries.size - 1).toFloat())
            Box(
                modifier = Modifier.offset {
                    IntOffset((itemWidthPx * position).roundToInt(), 0)
                }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape)
                    .zIndex(0f)
            )
            Row(Modifier.fillMaxSize().zIndex(1f)) {
                AppTab.entries.forEachIndexed { index, tab ->
                    val selected = kotlin.math.abs(position - index) < 0.5f
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(CircleShape)
                            .clickable { onSelect(index) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.title,
                            tint = if (selected) Color.White else Color(0xFFAAAAAA),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.title,
                            color = if (selected) Color.White else Color(0xFFAAAAAA),
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}
