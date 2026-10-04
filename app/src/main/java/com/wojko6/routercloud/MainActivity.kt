package com.wojko6.routercloud

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.wojko6.routercloud.network.RouterCloudClient
import com.wojko6.routercloud.network.RouterCloudDirectory
import com.wojko6.routercloud.network.RouterCloudEntry
import com.wojko6.routercloud.network.RouterCloudStorage
import com.wojko6.routercloud.security.BiometricSessionController
import com.wojko6.routercloud.ui.MetroActionGlyph
import com.wojko6.routercloud.ui.MetroActionGlyphType
import com.wojko6.routercloud.ui.MetroFileIcon
import com.wojko6.routercloud.ui.theme.RouterCloudTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUri = sharedUriFromIntent(intent)
        val biometricController =
            BiometricSessionController(this)

        setContent {
            RouterCloudTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RouterCloudApp(
                        initialSharedUri = sharedUri,
                        biometricController = biometricController,
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
    biometricController: BiometricSessionController,
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
    var showCreateDirectoryDialog by remember { mutableStateOf(false) }
    var renameEntry by remember { mutableStateOf<RouterCloudEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<RouterCloudEntry?>(null) }
    var pendingSharedUri by remember {
        mutableStateOf(initialSharedUri)
    }
    var error by remember { mutableStateOf<String?>(null) }

    var biometricBusy by remember {
        mutableStateOf(false)
    }

    var hasBiometricSession by remember {
        mutableStateOf(
            biometricController.hasSavedSession()
        )
    }

    val strongBiometricAvailable = remember {
        biometricController.isStrongBiometricAvailable()
    }

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

    fun renameItem(
        entry: RouterCloudEntry,
        newName: String,
    ) {
        val cleanName = newName.trim()
        val oldName = entry.name
            .trim('/')
            .substringAfterLast('/')

        if (
            cleanName.isEmpty() ||
            cleanName == "." ||
            cleanName == ".." ||
            '/' in cleanName ||
            '\\' in cleanName
        ) {
            error = "Nieprawidłowa nazwa."
            return
        }

        if (cleanName == oldName) {
            return
        }

        val alreadyExists = directory
            ?.entries
            ?.any {
                it.name
                    .trim('/')
                    .substringAfterLast('/') == cleanName
            }
            ?: false

        if (alreadyExists) {
            error = "Element „$cleanName” już istnieje."
            return
        }

        scope.launch {
            loading = true
            error = null

            try {
                val refreshed = withContext(Dispatchers.IO) {
                    client.rename(
                        sourcePath = remotePath(entry),
                        destinationPath = joinRemotePath(cleanName),
                    )

                    client.listDirectory(currentPath)
                }

                directory = refreshed
            } catch (e: Exception) {
                error = e.message
                    ?: "Nie udało się zmienić nazwy."
            } finally {
                loading = false
            }
        }
    }

    fun deleteItem(entry: RouterCloudEntry) {
        scope.launch {
            loading = true
            error = null

            try {
                val refreshed = withContext(Dispatchers.IO) {
                    client.delete(remotePath(entry))
                    client.listDirectory(currentPath)
                }

                directory = refreshed
            } catch (e: Exception) {
                error = e.message
                    ?: "Nie udało się usunąć elementu."
            } finally {
                loading = false
            }
        }
    }

    fun createDirectory(name: String) {
        val cleanName = name.trim()

        if (
            cleanName.isEmpty() ||
            cleanName == "." ||
            cleanName == ".." ||
            '/' in cleanName ||
            '\\' in cleanName
        ) {
            error = "Nieprawidłowa nazwa katalogu."
            return
        }

        val alreadyExists = directory
            ?.entries
            ?.any {
                it.name.substringAfterLast('/') == cleanName
            }
            ?: false

        if (alreadyExists) {
            error = "Element „$cleanName” już istnieje."
            return
        }

        scope.launch {
            loading = true
            error = null

            try {
                val refreshed = withContext(Dispatchers.IO) {
                    client.createDirectory(
                        joinRemotePath(cleanName)
                    )

                    client.listDirectory(currentPath)
                }

                directory = refreshed
            } catch (e: Exception) {
                error = e.message
                    ?: "Nie udało się utworzyć katalogu."
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

    fun unlockWithFingerprint() {
        if (
            biometricBusy ||
            loading ||
            !hasBiometricSession ||
            !strongBiometricAvailable
        ) {
            return
        }

        biometricBusy = true
        error = null

        biometricController.restoreSession(
            onSuccess = { cookies ->
                biometricBusy = false

                client.importSessionCookies(cookies)

                scope.launch {
                    loading = true
                    error = null

                    try {
                        val result =
                            withContext(Dispatchers.IO) {
                                client.listDirectory()
                            }

                        currentPath = ""
                        preview = null
                        directory = result
                    } catch (e: Exception) {
                        client.clearSession()
                        biometricController
                            .clearSavedSession()

                        hasBiometricSession = false

                        error =
                            "Zapisana sesja wygasła lub została odrzucona. " +
                                "Zaloguj się ponownie."
                    } finally {
                        loading = false
                    }
                }
            },
            onError = { message ->
                biometricBusy = false

                if (message != null) {
                    error = message

                    if (
                        !biometricController
                            .hasSavedSession()
                    ) {
                        hasBiometricSession = false
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        if (
            hasBiometricSession &&
            strongBiometricAvailable
        ) {
            unlockWithFingerprint()
        }
    }

    when {
        directory == null -> {
            LoginScreen(
                username = username,
                password = password,
                loading = loading || biometricBusy,
                error = error,
                fingerprintAvailable =
                    hasBiometricSession &&
                        strongBiometricAvailable,
                onUsernameChange = {
                    username = it
                    error = null
                },
                onPasswordChange = {
                    password = it
                    error = null
                },
                onFingerprintUnlock = {
                    unlockWithFingerprint()
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

                            val sessionCookies =
                                client.exportSessionCookies()

                            if (
                                strongBiometricAvailable &&
                                sessionCookies.isNotEmpty()
                            ) {
                                biometricController.saveSession(
                                    cookies = sessionCookies,
                                    onSuccess = {
                                        hasBiometricSession =
                                            true
                                    },
                                    onError = { message ->
                                        if (message != null) {
                                            error = message
                                        }
                                    },
                                )
                            }
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
                onCreateDirectory = {
                    showCreateDirectoryDialog = true
                },
                onRename = { entry ->
                    error = null
                    renameEntry = entry
                },
                onDelete = { entry ->
                    error = null
                    deleteEntry = entry
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

                        biometricController
                            .clearSavedSession()
                        hasBiometricSession = false

                        password = ""
                        currentPath = ""
                        preview = null
                        directory = null
                        error = null
                    }
                },
            )

            if (showCreateDirectoryDialog) {
                CreateDirectoryDialog(
                    onDismiss = {
                        showCreateDirectoryDialog = false
                    },
                    onCreate = { name ->
                        showCreateDirectoryDialog = false
                        createDirectory(name)
                    },
                )
            }

            renameEntry?.let { entry ->
                RenameDialog(
                    currentName = entry.name
                        .trim('/')
                        .substringAfterLast('/'),
                    onDismiss = {
                        renameEntry = null
                    },
                    onRename = { newName ->
                        renameEntry = null
                        renameItem(
                            entry = entry,
                            newName = newName,
                        )
                    },
                )
            }

            deleteEntry?.let { entry ->
                DeleteDialog(
                    entry = entry,
                    onDismiss = {
                        deleteEntry = null
                    },
                    onDelete = {
                        deleteEntry = null
                        deleteItem(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    loading: Boolean,
    error: String?,
    fingerprintAvailable: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFingerprintUnlock: () -> Unit,
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

        if (fingerprintAvailable) {
            Button(
                onClick = onFingerprintUnlock,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Text("Odblokuj odciskiem palca")
            }
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
    onCreateDirectory: () -> Unit,
    onRename: (RouterCloudEntry) -> Unit,
    onDelete: (RouterCloudEntry) -> Unit,
    onUploadSharedHere: () -> Unit,
    onEntryClick: (RouterCloudEntry) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val busy =
        loading ||
            downloadingFile != null ||
            uploadingFile != null

    val context = LocalContext.current

    var showMoreTiles by remember(context) {
        mutableStateOf(
            loadMetroShowMoreTiles(context),
        )
    }

    var layoutMenuExpanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 12.dp,
                    top = 18.dp,
                    bottom = 8.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "RouterCloud",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = if (currentPath.isEmpty()) "/" else "/$currentPath",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "${directory.entries.size} elementów",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(
                        onClick = {
                            layoutMenuExpanded = true
                        },
                    ) {
                        Text("Układ")
                    }

                    DropdownMenu(
                        expanded = layoutMenuExpanded,
                        onDismissRequest = {
                            layoutMenuExpanded = false
                        },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Pokaż więcej kafelków")
                            },
                            trailingIcon = {
                                Switch(
                                    checked = showMoreTiles,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = {
                                showMoreTiles =
                                    !showMoreTiles

                                saveMetroShowMoreTiles(
                                    context,
                                    showMoreTiles,
                                )

                                layoutMenuExpanded = false
                            },
                        )
                    }
                }

                TextButton(
                    onClick = onLogout,
                    enabled = !busy,
                ) {
                    Text("Wyloguj")
                }
            }
        }

        if (currentPath.isNotEmpty()) {
            TextButton(
                onClick = onBack,
                enabled = !busy,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text("← Wstecz")
            }
        }

        MetroTileDashboard(
            storage = directory.storage,
            allowUpload = directory.allowUpload,
            busy = busy,
            showMoreTiles = showMoreTiles,
            onUpload = onUpload,
            onCreateDirectory = onCreateDirectory,
        )

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
                    allowRename = directory.allowMove,
                    allowDelete = directory.allowDelete,
                    onClick = {
                        onEntryClick(entry)
                    },
                    onRename = {
                        onRename(entry)
                    },
                    onDelete = {
                        onDelete(entry)
                    },
                )

                HorizontalDivider()
            }
        }
    }
}

private const val METRO_TILE_PREFS =
    "routercloud-metro-tile-layout-v1"

private const val METRO_TILE_UPLOAD =
    "upload"

private const val METRO_TILE_DIRECTORY =
    "directory"

private const val METRO_TILE_STORAGE =
    "storage"

private const val METRO_SHOW_MORE_TILES =
    "show_more_tiles"


private enum class MetroTileSize(
    val displayName: String,
) {
    Small("Mały"),
    Medium("Średni"),
    Wide("Szeroki"),
}


private fun loadMetroShowMoreTiles(
    context: Context,
): Boolean {
    return context
        .getSharedPreferences(
            METRO_TILE_PREFS,
            Context.MODE_PRIVATE,
        )
        .getBoolean(
            METRO_SHOW_MORE_TILES,
            false,
        )
}


private fun saveMetroShowMoreTiles(
    context: Context,
    enabled: Boolean,
) {
    context
        .getSharedPreferences(
            METRO_TILE_PREFS,
            Context.MODE_PRIVATE,
        )
        .edit()
        .putBoolean(
            METRO_SHOW_MORE_TILES,
            enabled,
        )
        .apply()
}


private fun loadMetroTileSize(
    context: Context,
    key: String,
    default: MetroTileSize,
): MetroTileSize {
    val stored =
        context
            .getSharedPreferences(
                METRO_TILE_PREFS,
                Context.MODE_PRIVATE,
            )
            .getString(key, null)

    if (stored == "Large") {
        return MetroTileSize.Wide
    }

    return MetroTileSize
        .values()
        .firstOrNull { it.name == stored }
        ?: default
}


private fun saveMetroTileSize(
    context: Context,
    key: String,
    size: MetroTileSize,
) {
    context
        .getSharedPreferences(
            METRO_TILE_PREFS,
            Context.MODE_PRIVATE,
        )
        .edit()
        .putString(key, size.name)
        .apply()
}


private fun Modifier.metroTileDimensions(
    tileSize: MetroTileSize,
    cellSize: Dp,
    gap: Dp,
): Modifier {
    val widthUnits =
        when (tileSize) {
            MetroTileSize.Small -> 1
            MetroTileSize.Medium -> 2
            MetroTileSize.Wide -> 4
        }

    val heightUnits =
        when (tileSize) {
            MetroTileSize.Small -> 1
            MetroTileSize.Medium,
            MetroTileSize.Wide -> 2
        }

    val tileWidth =
        cellSize * widthUnits +
            gap * (widthUnits - 1)

    val tileHeight =
        cellSize * heightUnits +
            gap * (heightUnits - 1)

    return width(tileWidth)
        .height(tileHeight)
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetroTileDashboard(
    storage: RouterCloudStorage?,
    allowUpload: Boolean,
    busy: Boolean,
    showMoreTiles: Boolean,
    onUpload: () -> Unit,
    onCreateDirectory: () -> Unit,
) {
    if (!allowUpload && storage == null) {
        return
    }

    val context = LocalContext.current

    var uploadSize by remember(context) {
        mutableStateOf(
            loadMetroTileSize(
                context,
                METRO_TILE_UPLOAD,
                MetroTileSize.Medium,
            ),
        )
    }

    var directorySize by remember(context) {
        mutableStateOf(
            loadMetroTileSize(
                context,
                METRO_TILE_DIRECTORY,
                MetroTileSize.Medium,
            ),
        )
    }

    var storageSize by remember(context) {
        mutableStateOf(
            loadMetroTileSize(
                context,
                METRO_TILE_STORAGE,
                MetroTileSize.Wide,
            ),
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 8.dp,
            ),
    ) {
        val gap = 8.dp

        // Windows 10 Mobile-inspired Start layout.
        //
        // Default:
        // 6 small units = 3 medium tiles.
        //
        // "Show more tiles":
        // 8 small units = 4 medium tiles = 2 wide tiles.
        val gridUnits =
            if (showMoreTiles) {
                8
            } else {
                6
            }

        val cellSize =
            (
                maxWidth -
                    gap * (gridUnits - 1)
            ) / gridUnits

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(gap),
            verticalArrangement =
                Arrangement.spacedBy(gap),
        ) {
            if (allowUpload) {
                MetroActionTile(
                    icon = MetroActionGlyphType.Upload,
                    label = "Wyślij plik",
                    enabled = !busy,
                    tileSize = uploadSize,
                    onClick = onUpload,
                    onSizeChange = { newSize ->
                        uploadSize = newSize

                        saveMetroTileSize(
                            context,
                            METRO_TILE_UPLOAD,
                            newSize,
                        )
                    },
                    modifier = Modifier.metroTileDimensions(
                        uploadSize,
                        cellSize,
                        gap,
                    ),
                )

                MetroActionTile(
                    icon = MetroActionGlyphType.NewFolder,
                    label = "Katalog",
                    enabled = !busy,
                    tileSize = directorySize,
                    onClick = onCreateDirectory,
                    onSizeChange = { newSize ->
                        directorySize = newSize

                        saveMetroTileSize(
                            context,
                            METRO_TILE_DIRECTORY,
                            newSize,
                        )
                    },
                    modifier = Modifier.metroTileDimensions(
                        directorySize,
                        cellSize,
                        gap,
                    ),
                )
            }

            if (storage != null) {
                StorageTile(
                    storage = storage,
                    tileSize = storageSize,
                    onSizeChange = { newSize ->
                        storageSize = newSize

                        saveMetroTileSize(
                            context,
                            METRO_TILE_STORAGE,
                            newSize,
                        )
                    },
                    modifier = Modifier.metroTileDimensions(
                        storageSize,
                        cellSize,
                        gap,
                    ),
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetroActionTile(
    icon: MetroActionGlyphType,
    label: String,
    enabled: Boolean,
    tileSize: MetroTileSize,
    onClick: () -> Unit,
    onSizeChange: (MetroTileSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sizeMenuExpanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        if (enabled) {
                            onClick()
                        }
                    },
                    onLongClick = {
                        sizeMenuExpanded = true
                    },
                ),
            shape = RectangleShape,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.45f,
                    )
                },
            contentColor =
                MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            if (tileSize == MetroTileSize.Small) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroActionGlyph(
                        type = icon,
                        glyphSize = 18.dp,
                    )
                }
            } else {
                val compact =
                    tileSize == MetroTileSize.Medium

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            if (compact) 7.dp else 10.dp,
                        ),
                    verticalArrangement =
                        Arrangement.SpaceBetween,
                ) {
                    MetroActionGlyph(
                        type = icon,
                        glyphSize =
                            if (compact) 22.dp else 24.dp,
                    )

                    Text(
                        text = label,
                        style =
                            if (compact) {
                                MaterialTheme.typography.bodySmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        MetroTileSizeMenu(
            expanded = sizeMenuExpanded,
            currentSize = tileSize,
            onDismiss = {
                sizeMenuExpanded = false
            },
            onSizeChange = {
                sizeMenuExpanded = false
                onSizeChange(it)
            },
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageTile(
    storage: RouterCloudStorage,
    tileSize: MetroTileSize,
    onSizeChange: (MetroTileSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percentage =
        if (storage.total > 0L) {
            (
                storage.used.toDouble() /
                    storage.total.toDouble() *
                    100.0
            ).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

    var sizeMenuExpanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        sizeMenuExpanded = true
                    },
                ),
            tonalElevation = 3.dp,
            shadowElevation = 0.dp,
            shape = RectangleShape,
        ) {
            when (tileSize) {
                MetroTileSize.Small -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%.1f%%",
                                percentage,
                            ),
                            style =
                                MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                MetroTileSize.Medium -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(7.dp),
                        verticalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Pamięć",
                            style =
                                MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%.1f%%",
                                percentage,
                            ),
                            style =
                                MaterialTheme.typography.titleMedium,
                        )

                        Text(
                            text =
                                formatBytes(storage.used),
                            style =
                                MaterialTheme.typography.labelSmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                    }
                }

                MetroTileSize.Wide -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(9.dp),
                        verticalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Pamięć",
                                style =
                                    MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )

                            Text(
                                text = String.format(
                                    Locale.getDefault(),
                                    "%.1f%%",
                                    percentage,
                                ),
                                style =
                                    MaterialTheme.typography.titleMedium,
                            )
                        }

                        Text(
                            text =
                                "${formatBytes(storage.used)} zajęte",
                            style =
                                MaterialTheme.typography.bodyMedium,
                        )

                        Text(
                            text =
                                "${formatBytes(storage.available)} wolne",
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                    }
                }
            }
        }

        MetroTileSizeMenu(
            expanded = sizeMenuExpanded,
            currentSize = tileSize,
            onDismiss = {
                sizeMenuExpanded = false
            },
            onSizeChange = {
                sizeMenuExpanded = false
                onSizeChange(it)
            },
        )
    }
}


@Composable
private fun MetroTileSizeMenu(
    expanded: Boolean,
    currentSize: MetroTileSize,
    onDismiss: () -> Unit,
    onSizeChange: (MetroTileSize) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "Rozmiar kafelka",
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
            style = MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MetroTileSize.values().forEach { size ->
            DropdownMenuItem(
                text = {
                    Text(
                        text =
                            if (size == currentSize) {
                                "✓ ${size.displayName}"
                            } else {
                                size.displayName
                            },
                    )
                },
                onClick = {
                    onSizeChange(size)
                },
            )
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
private fun CreateDirectoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    val cleanName = name.trim()

    val valid =
        cleanName.isNotEmpty() &&
            cleanName != "." &&
            cleanName != ".." &&
            !cleanName.contains('/') &&
            !cleanName.contains('\\')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Nowy katalog")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = {
                    Text("Nazwa katalogu")
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(cleanName)
                },
                enabled = valid,
            ) {
                Text("Utwórz")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Anuluj")
            }
        },
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(currentName) {
        mutableStateOf(currentName)
    }

    val cleanName = name.trim()

    val valid =
        cleanName.isNotEmpty() &&
            cleanName != "." &&
            cleanName != ".." &&
            cleanName != currentName &&
            !cleanName.contains('/') &&
            !cleanName.contains('\\')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Zmień nazwę")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = {
                    Text("Nowa nazwa")
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(cleanName)
                },
                enabled = valid,
            ) {
                Text("Zmień")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Anuluj")
            }
        },
    )
}

@Composable
private fun DeleteDialog(
    entry: RouterCloudEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val displayName = entry.name
        .trim('/')
        .substringAfterLast('/')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entry.isDirectory) {
                    "Usuń katalog"
                } else {
                    "Usuń plik"
                }
            )
        },
        text = {
            Text(
                "Czy na pewno chcesz usunąć „$displayName”? " +
                    "Tej operacji nie można cofnąć."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDelete,
            ) {
                Text(
                    text = "Usuń",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Anuluj")
            }
        },
    )
}

@Composable
private fun FileRow(
    entry: RouterCloudEntry,
    enabled: Boolean,
    allowRename: Boolean,
    allowDelete: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(
                    start = 20.dp,
                    top = 14.dp,
                    bottom = 14.dp,
                    end = 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetroFileIcon(
                    entry = entry,
                )

                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

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

        if (allowRename || allowDelete) {
            Box {
                TextButton(
                    onClick = {
                        menuExpanded = true
                    },
                    enabled = enabled,
                ) {
                    Text("⋮")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                    },
                ) {
                    if (allowRename) {
                        DropdownMenuItem(
                            text = {
                                Text("Zmień nazwę")
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                    }

                    if (allowDelete) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Usuń",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
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
