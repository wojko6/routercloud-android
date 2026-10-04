package com.wojko6.routercloud

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
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
import java.io.IOException

private data class TextPreviewState(
    val fileName: String,
    val remotePath: String,
    val content: String,
)

private data class UploadSource(
    val fileName: String,
    val size: Long?,
    val mimeType: String?,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUri = sharedUriFromIntent(intent)

        setContent {
            RouterCloudTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RouterCloudApp(
                        initialSharedUri = sharedUri,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun sharedUriFromIntent(intent: Intent): Uri? {
    if (intent.action != Intent.ACTION_SEND) {
        return null
    }

    return intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        ?: intent.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
}

@Composable
private fun RouterCloudApp(
    initialSharedUri: Uri?,
) {
    val context = LocalContext.current
    val client = remember { RouterCloudClient() }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf<RouterCloudDirectory?>(null) }
    var currentPath by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<TextPreviewState?>(null) }

    var loading by remember { mutableStateOf(false) }
    var downloadingFile by remember { mutableStateOf<String?>(null) }
    var uploadingFile by remember { mutableStateOf<String?>(null) }
    var pendingSharedUri by remember {
        mutableStateOf(initialSharedUri)
    }
    var error by remember { mutableStateOf<String?>(null) }

    fun joinRemotePath(name: String): String {
        return listOf(
            currentPath.trim('/'),
            name.trim('/'),
        )
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    fun remotePath(entry: RouterCloudEntry): String =
        joinRemotePath(entry.name)

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

    fun uploadUri(
        uri: Uri,
        clearPendingShare: Boolean = false,
    ) {
        scope.launch {
            error = null

            try {
                val source = withContext(Dispatchers.IO) {
                    queryUploadSource(context, uri)
                }

                val alreadyExists = directory
                    ?.entries
                    ?.any {
                        it.name.substringAfterLast('/') == source.fileName
                    }
                    ?: false

                if (alreadyExists) {
                    error = "Plik „${source.fileName}” już istnieje w tym katalogu."
                    return@launch
                }

                uploadingFile = source.fileName

                val refreshed = withContext(Dispatchers.IO) {
                    client.uploadFile(
                        path = joinRemotePath(source.fileName),
                        inputStreamProvider = {
                            context.contentResolver.openInputStream(uri)
                                ?: throw IOException(
                                    "Nie można otworzyć wybranego pliku."
                                )
                        },
                        contentLength = source.size,
                        mediaType = source.mimeType,
                    )

                    client.listDirectory(currentPath)
                }

                directory = refreshed

                if (clearPendingShare) {
                    pendingSharedUri = null
                }
            } catch (e: Exception) {
                error = e.message ?: "Nie udało się wysłać pliku."
            } finally {
                uploadingFile = null
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            uploadUri(uri)
        }
    }

    fun downloadAndOpen(
        fileName: String,
        path: String,
    ) {
        scope.launch {
            downloadingFile = fileName
            error = null

            try {
                val downloaded = withContext(Dispatchers.IO) {
                    val cacheDir = File(
                        context.cacheDir,
                        "routercloud-downloads",
                    )

                    cacheDir.mkdirs()

                    val safeName = fileName
                        .substringAfterLast('/')
                        .ifBlank { "routercloud-file" }

                    val destination = File(cacheDir, safeName)

                    client.downloadFile(
                        path = path,
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

    fun openTextPreview(entry: RouterCloudEntry) {
        val path = remotePath(entry)

        scope.launch {
            loading = true
            error = null

            try {
                val text = withContext(Dispatchers.IO) {
                    client.readTextFile(path)
                }

                preview = TextPreviewState(
                    fileName = entry.name,
                    remotePath = path,
                    content = text,
                )
            } catch (e: Exception) {
                error = e.message ?: "Nie udało się otworzyć podglądu."
            } finally {
                loading = false
            }
        }
    }

    when {
        directory == null -> {
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
        }

        preview != null -> {
            val currentPreview = preview!!

            BackHandler {
                preview = null
            }

            TextPreviewScreen(
                preview = currentPreview,
                downloading = downloadingFile != null,
                error = error,
                onBack = {
                    preview = null
                    error = null
                },
                onOpenExternal = {
                    downloadAndOpen(
                        fileName = currentPreview.fileName,
                        path = currentPreview.remotePath,
                    )
                },
            )
        }

        else -> {
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
                uploadingFile = uploadingFile,
                pendingSharedFile = pendingSharedUri != null,
                error = error,
                onUpload = {
                    filePicker.launch(arrayOf("*/*"))
                },
                onUploadSharedHere = {
                    pendingSharedUri?.let { uri ->
                        uploadUri(
                            uri = uri,
                            clearPendingShare = true,
                        )
                    }
                },
                onEntryClick = { entry ->
                    if (entry.isDirectory) {
                        loadDirectory(remotePath(entry))
                    } else if (supportsTextPreview(entry.name)) {
                        openTextPreview(entry)
                    } else {
                        downloadAndOpen(
                            fileName = entry.name,
                            path = remotePath(entry),
                        )
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
                        preview = null
                        directory = null
                        error = null
                    }
                },
            )
        }
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
    uploadingFile: String?,
    pendingSharedFile: Boolean,
    error: String?,
    onUpload: () -> Unit,
    onUploadSharedHere: () -> Unit,
    onEntryClick: (RouterCloudEntry) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val busy =
        loading ||
            downloadingFile != null ||
            uploadingFile != null

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

            TextButton(
                onClick = onLogout,
                enabled = !busy,
            ) {
                Text("Wyloguj")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentPath.isNotEmpty()) {
                TextButton(
                    onClick = onBack,
                    enabled = !busy,
                ) {
                    Text("← Wstecz")
                }
            }

            if (directory.allowUpload) {
                TextButton(
                    onClick = onUpload,
                    enabled = !busy,
                ) {
                    Text("↑ Wyślij plik")
                }
            }
        }

        if (pendingSharedFile) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Plik udostępniony z innej aplikacji",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onUploadSharedHere,
                    enabled = !busy && directory.allowUpload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Wyślij tutaj")
                }
            }
        }

        if (loading) {
            Text(
                text = "Wczytywanie…",
                modifier = Modifier.padding(20.dp),
            )
        }

        if (downloadingFile != null) {
            Text(
                text = "Pobieranie: $downloadingFile",
                modifier = Modifier.padding(20.dp),
            )
        }

        if (uploadingFile != null) {
            Text(
                text = "Wysyłanie: $uploadingFile",
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
                    enabled = !busy,
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
private fun TextPreviewScreen(
    preview: TextPreviewState,
    downloading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) {
                Text("← Wstecz")
            }

            TextButton(
                onClick = onOpenExternal,
                enabled = !downloading,
            ) {
                Text(
                    if (downloading) {
                        "Pobieranie…"
                    } else {
                        "Otwórz w…"
                    }
                )
            }
        }

        Text(
            text = preview.fileName,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()

        SelectionContainer {
            Text(
                text = preview.content,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
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

private fun queryUploadSource(
    context: Context,
    uri: Uri,
): UploadSource {
    var name: String? = null
    var size: Long? = null

    context.contentResolver.query(
        uri,
        arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameColumn =
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            val sizeColumn =
                cursor.getColumnIndex(OpenableColumns.SIZE)

            if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                name = cursor.getString(nameColumn)
            }

            if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                size = cursor.getLong(sizeColumn)
            }
        }
    }

    val safeName = (name ?: "routercloud-upload")
        .replace('/', '_')
        .replace('\\', '_')
        .trim()
        .ifBlank { "routercloud-upload" }

    return UploadSource(
        fileName = safeName,
        size = size,
        mimeType = context.contentResolver.getType(uri),
    )
}

private fun supportsTextPreview(fileName: String): Boolean {
    val extension = fileName
        .substringAfterLast('.', "")
        .lowercase()

    return extension in setOf(
        "txt",
        "md",
        "log",
        "json",
        "xml",
        "yaml",
        "yml",
        "csv",
        "ini",
        "conf",
        "cfg",
        "properties",
        "sh",
        "kt",
        "java",
        "py",
        "js",
        "css",
        "html",
    )
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

    val mimeType = MimeTypeMap
        .getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())
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
