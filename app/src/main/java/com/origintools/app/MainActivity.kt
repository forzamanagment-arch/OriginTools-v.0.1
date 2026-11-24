package com.origintools.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Puzzle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.origintools.app.ui.theme.OriginTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeMaterials
import dev.chrisbanes.haze.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import rikka.shizuku.Shizuku
import java.io.File

private const val REQUEST_SHIZUKU = 4201
private const val FINGERPRINT_KEY = "fingerprint_icon_style_resource_a"

class MainActivity : ComponentActivity() {
    private var shizukuGranted by mutableStateOf(false)

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
        super.onDestroy()
    }

    private fun refreshShizukuState() {
        shizukuGranted = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
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
            Toast.makeText(this, "Shizuku: ${error.message ?: "ошибка подключения"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeFingerprintSetting(value: String?) {
        if (!shizukuGranted) {
            Toast.makeText(this, "Сначала предоставьте права Shizuku", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val command = if (value == null) {
                    arrayOf("settings", "delete", "system", FINGERPRINT_KEY)
                } else {
                    arrayOf("settings", "put", "system", FINGERPRINT_KEY, value)
                }
                val process = Shizuku.newProcess(command, null, null)
                val exitCode = process.waitFor()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (exitCode == 0) "Настройка применена" else "Не удалось изменить системную настройку",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка: ${error.message ?: "операция не выполнена"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Filled.Home),
    MODS("Модификации", Icons.Filled.Puzzle),
    SETTINGS("Настройки", Icons.Filled.Settings),
    ABOUT("Информация", Icons.Filled.Info)
}

@Composable
private fun OriginApp(
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onWriteFingerprintSetting: (String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    val hazeState = rememberHazeState()

    Scaffold(
        containerColor = Color(0xFF090A0B),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeBlur(input = HazeInput.Backdrop(hazeState), style = HazeMaterials.thin()),
                containerColor = Color(0xB81B1D1F),
                contentColor = Color.White,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 10.sp) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "origin_tab"
            ) { tab ->
                when (tab) {
                    Tab.HOME -> HomeScreen(shizukuGranted, onRequestShizuku) { selectedTab = Tab.MODS }
                    Tab.MODS -> ModsScreen(shizukuGranted, onRequestShizuku, onWriteFingerprintSetting)
                    Tab.SETTINGS -> SettingsScreen()
                    Tab.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onOpenMods: () -> Unit
) {
    var infoDialog by remember { mutableStateOf<String?>(null) }
    val rootDetected = remember { detectRoot() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("OriginTools", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text("Панель системных инструментов", color = Color(0xFF92979D), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            ShizukuStatusCard(shizukuGranted, onRequestShizuku)
        }
        item {
            Card(
                onClick = { infoDialog = "Диагностический раздел. OriginTools не запускает эксплойты и не пытается получать Root автоматически." },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171F2C)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("一键越狱", color = Color(0xFF78A9FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF78A9FF))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeActionRow(Icons.Filled.Security, "ROOT менеджер", "KernelSU") {
                    infoDialog = "KernelSU — отдельный менеджер Root. OriginTools только отображает состояние устройства и не устанавливает Root."
                }
                HomeActionRow(Icons.Filled.Tune, "Расширенные функции", "Системные настройки", onOpenMods)
                HomeActionRow(Icons.Filled.Code, "Руководство пользователя", "Справка и ограничения") {
                    infoDialog = "Для системных изменений требуется активный Shizuku и отдельно выданное приложению разрешение."
                }
            }
        }
        item {
            Text("СИСТЕМА", color = Color(0xFF858B91), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171819)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    TechnicalRow(Icons.Filled.Memory, "Версия ядра", System.getProperty("os.version") ?: "Неизвестно")
                    TechnicalRow(Icons.Filled.Terminal, "Метод доступа", if (shizukuGranted) "Shizuku (разрешён)" else "Не подключён")
                    TechnicalRow(Icons.Filled.Security, "Модель устройства", Build.MODEL.ifBlank { "Неизвестно" })
                    TechnicalRow(Icons.Filled.Refresh, "Версия Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    TechnicalRow(Icons.Filled.Info, "Версия ОС", Build.DISPLAY.ifBlank { Build.VERSION.INCREMENTAL })
                    TechnicalRow(Icons.Filled.Fingerprint, "Наличие Root", if (rootDetected) "Обнаружены признаки" else "Не обнаружен")
                }
            }
        }
    }

    infoDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text("OriginTools") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("Понятно") } },
            containerColor = Color(0xFF202224),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD2D5D8)
        )
    }
}

@Composable
private fun ShizukuStatusCard(granted: Boolean, onRequest: () -> Unit) {
    val green = Color(0xFF38D45A)
    val red = Color(0xFFF23D3D)
    Card(
        colors = CardDefaults.cardColors(containerColor = if (granted) Color(0xFF1E331F) else Color(0xFF3A1E1E)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (granted) green else red,
                    modifier = Modifier.size(27.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (granted) "Shizuku активен" else "Shizuku не подключён",
                        color = if (granted) green else red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        if (granted) "Разрешение для OriginTools получено" else "Для системных функций нужны права",
                        color = Color(0xFFD0D0D0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (!granted) {
                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Предоставить права (Shizuku)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222426)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFD8DDE2), modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF969CA2), fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF777E85))
        }
    }
}

@Composable
private fun TechnicalRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF9AA1A8), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, color = Color(0xFFB8BEC4), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Модификации", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("Настройки интерфейса устройства", color = Color(0xFF92979D))
        }
        if (!shizukuGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1E1E)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Функции заблокированы", color = Color(0xFFFF7777), fontWeight = FontWeight.Bold)
                        Text("Все элементы модификаций станут доступны после выдачи разрешения Shizuku.", color = Color(0xFFE0D4D4))
                        Button(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) {
                            Text("Подключить Shizuku")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222426)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().alpha(if (shizukuGranted) 1f else 0.42f)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("Удалить иконку отпечатка", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = removeIcon,
                            enabled = shizukuGranted,
                            onCheckedChange = {
                                removeIcon = it
                                onWriteSetting(if (it) " " else null)
                            }
                        )
                    }
                    Text("Скрывает системный ресурс иконки отпечатка.", color = Color(0xFF9DA3A9), fontSize = 13.sp)
                    HorizontalDivider(color = Color(0xFF3A3D40))
                    Text("Поменять иконку отпечатка", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = iconValue,
                        onValueChange = { if (it.length <= 3) iconValue = it },
                        enabled = shizukuGranted,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("До 3 символов") },
                        supportingText = { Text("${iconValue.length}/3") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = { onWriteSetting(iconValue.ifBlank { null }) },
                        enabled = shizukuGranted && iconValue.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Применить")
                    }
                }
            }
        }
        item {
            Text(
                "Изменение зависит от прошивки. Если ключ не поддерживается системой, OriginTools покажет ошибку, а не будет имитировать успешное применение.",
                color = Color(0xFF92979D),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SettingsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Настройки", style = MaterialTheme.typography.headlineLarge, color = Color.White) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222426)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Язык приложения", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Русский", color = Color(0xFF9DA3A9), fontSize = 13.sp)
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Информация", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222426)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OriginTools", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("0.1.0 BETA", color = Color(0xFF38D45A), fontWeight = FontWeight.Bold)
                    Text("Инструменты диагностики и настройки Android.", color = Color(0xFFB7BDC3))
                }
            }
        }
        item {
            HomeActionRow(Icons.Filled.Code, "GitHub репозиторий", "Исходный код OriginTools") {
                openUrl(context, "https://github.com/forzamanagment-arch/OriginTools-v.0.1")
            }
        }
        item {
            HomeActionRow(Icons.Filled.Info, "Профиль разработчика", "forzamanagment-arch") {
                openUrl(context, "https://github.com/forzamanagment-arch")
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

private fun detectRoot(): Boolean {
    val paths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system_ext/bin/su",
        "/vendor/bin/su",
        "/data/adb/magisk",
        "/data/adb/ksu"
    )
    return paths.any { File(it).exists() }
}
