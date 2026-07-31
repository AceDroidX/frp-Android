package io.github.acedroidx.frp

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.acedroidx.frp.ui.theme.FrpTheme
import io.github.acedroidx.frp.ui.theme.ThemeModeKeys
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    private val isStartup = MutableStateFlow(false)
    private val isStartupLaunch = MutableStateFlow(false)
    private val isStartupBroadcast = MutableStateFlow(false)
    private val isStopBroadcast = MutableStateFlow(false)
    private val isStartupBroadcastExtra = MutableStateFlow(false)
    private val themeMode = MutableStateFlow("")
    private val allowTasker = MutableStateFlow(false)
    private val excludeFromRecents = MutableStateFlow(false)
    private val hideServiceToast = MutableStateFlow(false)
    private val allowConfigRead = MutableStateFlow(false)
    private val allowConfigWrite = MutableStateFlow(false)
    private val httpProxyEnabled = MutableStateFlow(false)
    private val quickTileConfig = MutableStateFlow<FrpConfig?>(null)
    private lateinit var preferences: SharedPreferences

    // 配置列表
    private val allConfigs = MutableStateFlow<List<FrpConfig>>(emptyList())

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val result = exportBackupToUri(uri)
                if (result.isSuccess) {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.backup_export_success),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val reason = result.exceptionOrNull()?.localizedMessage ?: ""
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.backup_export_failed, reason),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("SettingsActivity", "Export backup failed", result.exceptionOrNull())
                }
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val result = importBackupFromUri(uri)
                if (result.isSuccess) {
                    refreshStateAfterImport()
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.backup_import_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    restartAppAfterImport()
                } else {
                    val reason = result.exceptionOrNull()?.localizedMessage ?: ""
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.backup_import_failed, reason),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("SettingsActivity", "Import backup failed", result.exceptionOrNull())
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        loadPreferencesIntoState()

        // 加载配置列表
        loadConfigList()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle(themeMode.collectAsState().value.ifEmpty { ThemeModeKeys.FOLLOW_SYSTEM })
            FrpTheme(themeMode = currentTheme) {
                Scaffold(topBar = {
                    TopAppBar(title = {
                        Text(stringResource(R.string.settings_title))
                    }, navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                contentDescription = stringResource(R.string.content_desc_back)
                            )
                        }
                    })
                }) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsContent()
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun SettingsContent() {
        val isAutoStart by isStartup.collectAsStateWithLifecycle(false)
        val currentTheme by themeMode.collectAsStateWithLifecycle(themeMode.collectAsState().value.ifEmpty { ThemeModeKeys.FOLLOW_SYSTEM })
        val isTaskerAllowed by allowTasker.collectAsStateWithLifecycle(false)
        val isExcludeFromRecents by excludeFromRecents.collectAsStateWithLifecycle(false)
        val isHideServiceToast by hideServiceToast.collectAsStateWithLifecycle(false)
        val isConfigReadAllowed by allowConfigRead.collectAsStateWithLifecycle(false)
        val isConfigWriteAllowed by allowConfigWrite.collectAsStateWithLifecycle(false)
        val isHttpProxyEnabled by httpProxyEnabled.collectAsStateWithLifecycle(false)
        val currentQuickTileConfig by quickTileConfig.collectAsStateWithLifecycle(null)
        val configs by allConfigs.collectAsStateWithLifecycle(emptyList())

        var showAutoStartHelp by remember { mutableStateOf(false) }
        var showConfigIoHelp by remember { mutableStateOf(false) }
        var showHttpProxyHelp by remember { mutableStateOf(false) }

        val themeOptions = listOf(
            ThemeModeKeys.DARK to stringResource(R.string.theme_mode_dark),
            ThemeModeKeys.LIGHT to stringResource(R.string.theme_mode_light),
            ThemeModeKeys.FOLLOW_SYSTEM to stringResource(R.string.theme_mode_follow_system)
        )

        if (showAutoStartHelp) {
            HelpDialog(
                title = stringResource(R.string.auto_start_title),
                message = stringResource(R.string.auto_start_help_message),
                onDismiss = { showAutoStartHelp = false })
        }

        if (showConfigIoHelp) {
            HelpDialog(
                title = stringResource(R.string.config_io_title),
                message = stringResource(R.string.config_io_help_message),
                onDismiss = { showConfigIoHelp = false })
        }

        if (showHttpProxyHelp) {
            HelpDialog(
                title = stringResource(R.string.http_proxy_title),
                message = stringResource(R.string.http_proxy_help, HttpProxyService.PORT),
                onDismiss = { showHttpProxyHelp = false })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 主题/快捷/Tasker/最近任务分组（Card）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(2.dp)) {
                    // 主题切换设置项
                    SettingItemWithDropdown(
                        title = stringResource(R.string.theme_mode_title),
                        currentKey = currentTheme,
                        options = themeOptions,
                        onValueChange = { newThemeKey ->
                            preferences.edit {
                                putString(PreferencesKey.THEME_MODE, newThemeKey)
                            }
                            themeMode.value = ThemeModeKeys.normalize(newThemeKey)
                        })

                    Spacer(modifier = Modifier.height(8.dp))
                    // 快捷开关配置选择
                    SettingItemWithConfigSelector(
                        title = stringResource(R.string.quick_tile_config),
                        currentConfig = currentQuickTileConfig,
                        configs = configs,
                        onConfigChange = { config ->
                            preferences.edit {
                                if (config != null) {
                                    putString(
                                        PreferencesKey.QUICK_TILE_CONFIG_TYPE, config.type.name
                                    )
                                    putString(
                                        PreferencesKey.QUICK_TILE_CONFIG_NAME, config.fileName
                                    )
                                } else {
                                    remove(PreferencesKey.QUICK_TILE_CONFIG_TYPE)
                                    remove(PreferencesKey.QUICK_TILE_CONFIG_NAME)
                                }
                            }
                            quickTileConfig.value = config
                        })

                    // 允许 Tasker 调用设置项
                    SettingItemWithSwitch(
                        title = stringResource(R.string.allow_tasker_title),
                        checked = isTaskerAllowed,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.ALLOW_TASKER, checked)
                            }
                            allowTasker.value = checked
                        })

                    // 最近任务中排除设置项
                    SettingItemWithSwitch(
                        title = stringResource(R.string.exclude_from_recents_title),
                        checked = isExcludeFromRecents,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, checked)
                            }
                            excludeFromRecents.value = checked

                            // 立即应用设置，不需要重启
                            try {
                                val am =
                                    getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                                val appTasks = am.appTasks
                                android.util.Log.d(
                                    "SettingsActivity", "appTasks size: ${appTasks.size}"
                                )
                                if (appTasks.isNotEmpty()) {
                                    for (task in appTasks) {
                                        task.setExcludeFromRecents(checked)
                                        android.util.Log.d(
                                            "SettingsActivity", "Set excludeFromRecents to $checked"
                                        )
                                    }
                                } else {
                                    android.util.Log.w("SettingsActivity", "appTasks is empty")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "SettingsActivity",
                                    "Failed to set excludeFromRecents: ${e.message}"
                                )
                            }
                        })

                    // 隐藏服务启停提示设置项
                    SettingItemWithSwitch(
                        title = stringResource(R.string.hide_service_toast_title),
                        checked = isHideServiceToast,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.HIDE_SERVICE_TOAST, checked)
                            }
                            hideServiceToast.value = checked
                        })

                }
            }

            // Native HTTP/HTTPS CONNECT 代理。默认监听所有接口，便于本机和局域网客户端使用。
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(2.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.http_proxy_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.http_proxy_endpoint,
                                    HttpProxyService.PORT
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showHttpProxyHelp = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.help_24px),
                                contentDescription = stringResource(R.string.content_desc_help)
                            )
                        }
                    }
                    HorizontalDivider()
                    SettingItemWithSwitch(
                        title = stringResource(R.string.http_proxy_switch),
                        checked = isHttpProxyEnabled,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.HTTP_PROXY_ENABLED, checked)
                            }
                            httpProxyEnabled.value = checked
                            if (checked) {
                                HttpProxyService.start(this@SettingsActivity)
                            } else {
                                HttpProxyService.stop(this@SettingsActivity)
                            }
                        }
                    )
                }
            }

            // frp 自启动设置分类（Card）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(2.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.auto_start_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showAutoStartHelp = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.help_24px),
                                contentDescription = stringResource(R.string.content_desc_help)
                            )
                        }
                    }
                    HorizontalDivider()

                    // 开机自启动（移动到自启动分类中）
                    SettingItemWithSwitch(
                        title = stringResource(R.string.auto_start_boot),
                        checked = isAutoStart,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.AUTO_START, checked)
                            }
                            isStartup.value = checked
                        })


                    // 在打开应用时启动
                    val isAutoStartLaunch by isStartupLaunch.collectAsStateWithLifecycle(false)
                    SettingItemWithSwitch(
                        title = stringResource(R.string.auto_start_launch),
                        checked = isAutoStartLaunch,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferencesKey.AUTO_START_LAUNCH, checked
                                )
                            }
                            isStartupLaunch.value = checked
                        })

                    // 在收到广播时启动
                    val isAutoStartBroadcast by isStartupBroadcast.collectAsStateWithLifecycle(false)
                    SettingItemWithSwitchAndHelp(
                        title = stringResource(R.string.auto_start_broadcast),
                        helpText = stringResource(
                            R.string.auto_start_broadcast_help_message, BroadcastAction.START
                        ),
                        checked = isAutoStartBroadcast,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferencesKey.AUTO_START_BROADCAST, checked
                                )
                            }
                            isStartupBroadcast.value = checked
                        })

                    // 在收到广播时关闭
                    val isAutoStopBroadcast by isStopBroadcast.collectAsStateWithLifecycle(false)
                    SettingItemWithSwitchAndHelp(
                        title = stringResource(R.string.auto_stop_broadcast),
                        helpText = stringResource(
                            R.string.auto_stop_broadcast_help_message, BroadcastAction.STOP
                        ),
                        checked = isAutoStopBroadcast,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferencesKey.AUTO_STOP_BROADCAST, checked
                                )
                            }
                            isStopBroadcast.value = checked
                        })

                    // 允许带参数的广播
                    val isAutoStartBroadcastExtra by isStartupBroadcastExtra.collectAsStateWithLifecycle(
                        false
                    )
                    SettingItemWithSwitchAndHelp(
                        title = stringResource(R.string.auto_start_broadcast_extra),
                        helpText = stringResource(
                            R.string.auto_start_broadcast_extra_help_message,
                            BroadcastAction.START,
                            BroadcastExtraKey.TYPE,
                            BroadcastExtraKey.NAME,
                            BroadcastAction.STOP
                        ),
                        checked = isAutoStartBroadcastExtra,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(
                                    PreferencesKey.AUTO_START_BROADCAST_EXTRA, checked
                                )
                            }
                            isStartupBroadcastExtra.value = checked
                        })
                }
            }

            // frp 配置读写接口
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(2.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.config_io_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showConfigIoHelp = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.help_24px),
                                contentDescription = stringResource(R.string.content_desc_help)
                            )
                        }
                    }
                    HorizontalDivider()

                    SettingItemWithSwitch(
                        title = stringResource(R.string.config_read_title),
                        checked = isConfigReadAllowed,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.ALLOW_CONFIG_READ, checked)
                            }
                            allowConfigRead.value = checked
                        })

                    SettingItemWithSwitch(
                        title = stringResource(R.string.config_write_title),
                        checked = isConfigWriteAllowed,
                        onCheckedChange = { checked ->
                            preferences.edit {
                                putBoolean(PreferencesKey.ALLOW_CONFIG_WRITE, checked)
                            }
                            allowConfigWrite.value = checked
                        })
                }
            }

            // 备份与恢复（Card）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(modifier = Modifier.weight(1f), onClick = { startImportBackup() }) {
                            Text(text = stringResource(R.string.backup_import_button))
                        }
                        Button(modifier = Modifier.weight(1f), onClick = { startExportBackup() }) {
                            Text(text = stringResource(R.string.backup_export_button))
                        }
                    }
                }
            }

            // 关于设置项
            // 关于设置项（Card）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(2.dp)) {
                    SettingItemClickable(
                        title = stringResource(R.string.settings_reopen_onboarding), onClick = {
                            startActivity(
                                Intent(
                                    this@SettingsActivity, OnboardingActivity::class.java
                                )
                            )
                        })
                    SettingItemClickable(
                        title = stringResource(R.string.aboutButton), onClick = {
                            startActivity(Intent(this@SettingsActivity, AboutActivity::class.java))
                        })
                }
            }
        }
    }

    @Composable
    fun SettingItemWithSwitch(
        title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = checked, onCheckedChange = onCheckedChange
            )
        }
    }

    @Composable
    fun SettingItemWithSwitchAndHelp(
        title: String, helpText: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
    ) {
        var showHelp by remember { mutableStateOf(false) }

        if (showHelp) {
            HelpDialog(title = title, message = helpText, onDismiss = { showHelp = false })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { showHelp = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.help_24px),
                        contentDescription = stringResource(R.string.content_desc_help)
                    )
                }
            }
            Switch(
                checked = checked, onCheckedChange = onCheckedChange
            )
        }
    }

    @Composable
    fun SettingItemWithDropdown(
        title: String,
        currentKey: String,
        options: List<Pair<String, String>>, // key to display label
        onValueChange: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        val currentLabel = options.firstOrNull { it.first == currentKey }?.second ?: stringResource(
            R.string.theme_mode_follow_system
        )

        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenu(
                    expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option.second) }, onClick = {
                            onValueChange(option.first)
                            expanded = false
                        })
                    }
                }
            }
        }
    }

    @Composable
    fun SettingItemClickable(
        title: String, onClick: () -> Unit
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    @Composable
    fun SettingItemWithConfigSelector(
        title: String,
        currentConfig: FrpConfig?,
        configs: List<FrpConfig>,
        onConfigChange: (FrpConfig?) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        val displayValue = currentConfig?.let {
            stringResource(
                R.string.config_display_value, it.type.typeName, it.fileName.removeSuffix(".toml")
            )
        } ?: stringResource(R.string.quick_tile_not_selected)

        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenu(
                    expanded = expanded, onDismissRequest = { expanded = false }) {
                    // 不选择选项
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.quick_tile_not_selected)) },
                        onClick = {
                            onConfigChange(null)
                            expanded = false
                        })
                    // 配置列表
                    configs.forEach { config ->
                        DropdownMenuItem(text = {
                            Text(
                                stringResource(
                                    R.string.config_display_value,
                                    config.type.typeName,
                                    config.fileName.removeSuffix(".toml")
                                )
                            )
                        }, onClick = {
                            onConfigChange(config)
                            expanded = false
                        })
                    }
                }
            }
        }
    }

    private fun startExportBackup() {
        val fileName =
            "frp-android-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(
                Date()
            ) + ".zip"
        exportBackupLauncher.launch(fileName)
    }

    private fun startImportBackup() {
        importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    // 读取 SharedPreferences、主题等状态，便于导入备份后快速刷新 UI
    private fun loadPreferencesIntoState() {
        isStartup.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        isStartupLaunch.value = preferences.getBoolean(PreferencesKey.AUTO_START_LAUNCH, false)
        isStartupBroadcast.value =
            preferences.getBoolean(PreferencesKey.AUTO_START_BROADCAST, false)
        isStopBroadcast.value = preferences.getBoolean(PreferencesKey.AUTO_STOP_BROADCAST, false)
        isStartupBroadcastExtra.value =
            preferences.getBoolean(PreferencesKey.AUTO_START_BROADCAST_EXTRA, false)

        val rawTheme = preferences.getString(PreferencesKey.THEME_MODE, ThemeModeKeys.FOLLOW_SYSTEM)
        themeMode.value = ThemeModeKeys.normalize(rawTheme)

        allowTasker.value = preferences.getBoolean(PreferencesKey.ALLOW_TASKER, false)
        excludeFromRecents.value =
            preferences.getBoolean(PreferencesKey.EXCLUDE_FROM_RECENTS, false)
        hideServiceToast.value = preferences.getBoolean(PreferencesKey.HIDE_SERVICE_TOAST, false)
        allowConfigRead.value = preferences.getBoolean(PreferencesKey.ALLOW_CONFIG_READ, false)
        allowConfigWrite.value = preferences.getBoolean(PreferencesKey.ALLOW_CONFIG_WRITE, false)
        httpProxyEnabled.value =
            preferences.getBoolean(PreferencesKey.HTTP_PROXY_ENABLED, false)

        // 读取快捷开关配置
        loadQuickTileConfig()
    }

    private fun refreshStateAfterImport() {
        preferences = getSharedPreferences("data", MODE_PRIVATE)
        loadPreferencesIntoState()
        loadConfigList()
    }

    // 强行停止并重开启应用，确保导入的配置与偏好生效
    private fun restartAppAfterImport() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    // 使用 SAF 导出配置与设置为 zip 包，结构与 /files 与 shared_prefs 对齐
    private suspend fun exportBackupToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                    appendDirToZip(
                        FrpType.FRPC.getDir(this@SettingsActivity),
                        "files/${FrpType.FRPC.typeName}/",
                        zipOut
                    )
                    appendDirToZip(
                        FrpType.FRPS.getDir(this@SettingsActivity),
                        "files/${FrpType.FRPS.typeName}/",
                        zipOut
                    )

                    val prefsFile = File(applicationInfo.dataDir, "shared_prefs/data.xml")
                    if (prefsFile.exists()) {
                        appendFileToZip(prefsFile, "shared_prefs/data.xml", zipOut)
                    }
                }
            } ?: error("Cannot open output stream")
        }
    }

    // 使用 SAF 导入 zip 备份，覆盖 frpc/frps 配置与 data.xml
    private suspend fun importBackupFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val rawName = entry.name.replace("\\", "/")
                        if (!entry.isDirectory && !rawName.contains("..")) {
                            when {
                                rawName.startsWith("files/") -> {
                                    val relative = rawName.removePrefix("files/")
                                    if (relative.isNotBlank()) {
                                        val target = File(filesDir, relative)
                                        ensureInsideDir(target, filesDir)
                                        target.parentFile?.mkdirs()
                                        FileOutputStream(target).use { output ->
                                            zipIn.copyTo(output)
                                        }
                                    }
                                }

                                rawName == "shared_prefs/data.xml" -> {
                                    val prefsDir = File(applicationInfo.dataDir, "shared_prefs")
                                    prefsDir.mkdirs()
                                    val target = File(prefsDir, "data.xml")
                                    ensureInsideDir(
                                        target,
                                        prefsDir.parentFile ?: File(applicationInfo.dataDir)
                                    )
                                    FileOutputStream(target).use { output ->
                                        zipIn.copyTo(output)
                                    }
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: error("Cannot open input stream")
        }
    }

    // 压缩目录，递归收集内部文件
    private fun appendDirToZip(dir: File, basePath: String, zipOut: ZipOutputStream) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                appendDirToZip(file, basePath + file.name + "/", zipOut)
            } else {
                appendFileToZip(file, basePath + file.name, zipOut)
            }
        }
    }

    private fun appendFileToZip(file: File, entryName: String, zipOut: ZipOutputStream) {
        val entry = ZipEntry(entryName)
        entry.time = file.lastModified()
        zipOut.putNextEntry(entry)
        file.inputStream().use { input ->
            input.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }

    private fun ensureInsideDir(target: File, parent: File) {
        val parentCanonical = parent.canonicalFile
        val targetCanonical = target.canonicalFile
        if (!targetCanonical.path.startsWith(parentCanonical.path)) {
            throw IllegalArgumentException("Path traversal detected: ${target.path}")
        }
    }

    private fun loadConfigList() {
        val frpcConfigs = (FrpType.FRPC.getDir(this).list()?.toList() ?: emptyList()).map {
            FrpConfig(FrpType.FRPC, it)
        }
        val frpsConfigs = (FrpType.FRPS.getDir(this).list()?.toList() ?: emptyList()).map {
            FrpConfig(FrpType.FRPS, it)
        }
        allConfigs.value = frpcConfigs + frpsConfigs
    }

    private fun loadQuickTileConfig() {
        val configType = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_TYPE, null)
        val configName = preferences.getString(PreferencesKey.QUICK_TILE_CONFIG_NAME, null)

        if (configType != null && configName != null) {
            try {
                val type = FrpType.valueOf(configType)
                val config = FrpConfig(type, configName)
                // 检查配置文件是否存在
                if (config.getFile(this).exists()) {
                    quickTileConfig.value = config
                } else {
                    // 配置文件不存在，清除设置
                    preferences.edit().apply {
                        remove(PreferencesKey.QUICK_TILE_CONFIG_TYPE)
                        remove(PreferencesKey.QUICK_TILE_CONFIG_NAME)
                        apply()
                    }
                    quickTileConfig.value = null
                }
            } catch (e: IllegalArgumentException) {
                quickTileConfig.value = null
            }
        }
    }

    @Composable
    private fun HelpDialog(title: String, message: String, onDismiss: () -> Unit) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
            SelectionContainer {
                Text(text = message)
            }
        }, confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        })
    }
}
