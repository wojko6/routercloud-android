package com.wojko6.routercloud

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wojko6.routercloud.network.RouterCloudClient
import com.wojko6.routercloud.network.RouterCloudDirectory
import com.wojko6.routercloud.network.RouterCloudEntry
import com.wojko6.routercloud.ui.theme.RouterCloudTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RouterCloudTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RouterCloudApp()
                }
            }
        }
    }
}

@Composable
private fun RouterCloudApp() {
    val context = LocalContext.current
    val client = remember { RouterCloudClient() }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf<RouterCloudDirectory?>(null) }
    var currentPath by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var downloadingFile by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun remotePath(entry: RouterCloudEntry): String {
        return listOf(
            currentPath.trim('/'),
            entry.name.trim('/'),
        )
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    fun loadDirectory(path: String) {
        scope.launch {
            loading = true
            error = null

            try {
                val result = withContext(Dispatchers.IO) {
                    client.listDirectory(path)
                }

                currentPath = path
                directory = result
            } catch (e: Exception) {
                error = e.message ?: "Nie udało się pobrać katalogu."
            } finally {
                loading = false
            }
        }
    }

    fun downloadAndOpen(entry: RouterCloudEntry) {
        scope.launch {
            downloadingFile = entry.name
            error = null

            try {
                val downloaded = withContext(Dispatchers.IO) {
                    val cacheDir = File(
                        context.cacheDir,
                        "routercloud-downloads",
                    )

                    cacheDir.mkdirs()

                    val safeName = entry.name
                        .substringAfterLast('/')
                        .ifBlank { "routercloud-file" }

                    val destination = File(cacheDir, safeName)

                    client.downloadFile(
                        path = remotePath(entry),
                        destination = destination,
                    )

                    destination
                }

                openDownloadedFile(
                    context = context,
                    file = downloaded,
                )
            } catch (e: ActivityNotFoundException) {
                error = "Brak aplikacji, która potrafi otworzyć ten typ pliku."
            } catch (e: Exception) {
                error = e.message ?: "Nie udało się pobrać pliku."
            } finally {
                downloadingFile = null
            }
        }
    }

    if (directory == null) {
        LoginScreen(
            username = username,
            password = password,
            loading = loading,
            error = error,
            onUsernameChange = {
                username = it
                error = null
            },
            onPasswordChange = {
                password = it
                error = null
            },
            onLogin = {
                if (username.isBlank() || password.isEmpty()) {
                    error = "Podaj login/e-mail i hasło."
                    return@LoginScreen
                }

                scope.launch {
                    loading = true
                    error = null

                    try {
                        val result = withContext(Dispatchers.IO) {
                            client.login(username, password)
                            client.listDirectory()
                        }

                        password = ""
                        currentPath = ""
                        directory = result
                    } catch (e: Exception) {
                        error = e.message
                            ?: "Nie udało się połączyć z RouterCloud."
                    } finally {
                        loading = false
                    }
                }
            },
        )
    } else {
        BackHandler(enabled = currentPath.isNotEmpty()) {
            val parent = currentPath
                .trim('/')
                .substringBeforeLast('/', "")

            loadDirectory(parent)
        }

        FilesScreen(
            directory = directory!!,
            currentPath = currentPath,
            loading = loading,
            downloadingFile = downloadingFile,
            error = error,
            onEntryClick = { entry ->
                if (entry.isDirectory) {
                    loadDirectory(remotePath(entry))
                } else {
                    downloadAndOpen(entry)
                }
            },
            onBack = {
                val parent = currentPath
                    .trim('/')
                    .substringBeforeLast('/', "")

                loadDirectory(parent)
            },
            onLogout = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { client.logout() }
                    }

                    password = ""
                    currentPath = ""
                    directory = null
                    error = null
                }
            },
        )
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    loading: Boolean,
    error: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "RouterCloud",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Prywatna chmura",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "Połączenie szyfrowane z cloud.home.arpa",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            singleLine = true,
            label = { Text("Login lub e-mail") },
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            singleLine = true,
            label = { Text("Hasło") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("Zaloguj")
            }
        }
    }
}

@Composable
private fun FilesScreen(
    directory: RouterCloudDirectory,
    currentPath: String,
    loading: Boolean,
    downloadingFile: String?,
    error: String?,
    onEntryClick: (RouterCloudEntry) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "RouterCloud",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = if (currentPath.isEmpty()) "/" else "/$currentPath",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "${directory.entries.size} elementów",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onLogout) {
                Text("Wyloguj")
            }
        }

        if (currentPath.isNotEmpty()) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                enabled = !loading && downloadingFile == null,
            ) {
                Text("← Wstecz")
            }
        }

        if (loading) {
            Text(
                text = "Wczytywanie katalogu…",
                modifier = Modifier.padding(20.dp),
            )
        }

        if (downloadingFile != null) {
            Text(
                text = "Pobieranie: $downloadingFile",
                modifier = Modifier.padding(20.dp),
            )
        }

        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = directory.entries,
                key = { "${it.pathType}:${it.name}" },
            ) { entry ->
                FileRow(
                    entry = entry,
                    enabled = !loading && downloadingFile == null,
                    onClick = {
                        onEntryClick(entry)
                    },
                )

                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: RouterCloudEntry,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (entry.isDirectory) {
                "📁 ${entry.name}"
            } else {
                "📄 ${entry.name}"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )

        Text(
            text = if (entry.isDirectory) {
                "Katalog"
            } else {
                formatBytes(entry.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openDownloadedFile(
    context: Context,
    file: File,
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val extension = file.extension.lowercase()

    val mimeType = MimeTypeMap
        .getSingleton()
        .getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(
            intent,
            "Otwórz plik RouterCloud",
        )
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)

    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)

    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
