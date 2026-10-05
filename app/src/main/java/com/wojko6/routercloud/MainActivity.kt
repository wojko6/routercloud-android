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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

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

    var tileEditMode by remember {
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
            editMode = tileEditMode,
            onEditModeChange = {
                tileEditMode = it
            },
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

private const val METRO_WORKSPACE_ROWS =
    4


private const val METRO_TILE_ORDER =
    "tile_order"


internal enum class MetroTileSize(
    val displayName: String,
) {
    Small("Mały"),
    Medium("Średni"),
    Wide("Szeroki"),
    Large("Duży"),
}


private fun defaultMetroTileOrder(): List<String> =
    listOf(
        METRO_TILE_UPLOAD,
        METRO_TILE_DIRECTORY,
        METRO_TILE_STORAGE,
    )


private fun loadMetroTileOrder(
    context: Context,
): List<String> {
    val defaults = defaultMetroTileOrder()

    val saved =
        context
            .getSharedPreferences(
                METRO_TILE_PREFS,
                Context.MODE_PRIVATE,
            )
            .getString(
                METRO_TILE_ORDER,
                null,
            )
            ?.split(",")
            ?.filter { it in defaults }
            .orEmpty()

    return (saved + defaults).distinct()
}


private fun saveMetroTileOrder(
    context: Context,
    order: List<String>,
) {
    context
        .getSharedPreferences(
            METRO_TILE_PREFS,
            Context.MODE_PRIVATE,
        )
        .edit()
        .putString(
            METRO_TILE_ORDER,
            order.joinToString(","),
        )
        .apply()
}


private fun moveMetroTile(
    order: List<String>,
    source: String,
    target: String,
    placeAfter: Boolean,
): List<String> {
    if (source == target) {
        return order
    }

    val result =
        order
            .filterNot { it == source }
            .toMutableList()

    val targetIndex =
        result.indexOf(target)

    if (targetIndex < 0) {
        return order
    }

    val insertIndex =
        targetIndex +
            if (placeAfter) {
                1
            } else {
                0
            }

    result.add(
        insertIndex.coerceIn(
            0,
            result.size,
        ),
        source,
    )

    return result
}


internal data class MetroTilePosition(
    val column: Int,
    val row: Int,
)


internal fun metroTileSpan(
    size: MetroTileSize,
): Pair<Int, Int> =
    when (size) {
        MetroTileSize.Small ->
            1 to 1

        MetroTileSize.Medium ->
            2 to 2

        MetroTileSize.Wide ->
            4 to 2

        MetroTileSize.Large ->
            4 to 4
    }


private fun metroTilePositionsPreferenceKey(
    gridUnits: Int,
): String =
    "tile_positions_v2_$gridUnits"


private fun loadMetroTilePositions(
    context: Context,
    gridUnits: Int,
): Map<String, MetroTilePosition>? {
    val raw =
        context
            .getSharedPreferences(
                METRO_TILE_PREFS,
                Context.MODE_PRIVATE,
            )
            .getString(
                metroTilePositionsPreferenceKey(
                    gridUnits,
                ),
                null,
            )
            ?: return null

    val allowed =
        defaultMetroTileOrder().toSet()

    return raw
        .split(";")
        .mapNotNull { entry ->
            val parts =
                entry.split(":")

            if (parts.size != 3) {
                return@mapNotNull null
            }

            val tileId =
                parts[0]

            val column =
                parts[1].toIntOrNull()

            val row =
                parts[2].toIntOrNull()

            if (
                tileId !in allowed ||
                column == null ||
                row == null
            ) {
                null
            } else {
                tileId to
                    MetroTilePosition(
                        column = column,
                        row = row,
                    )
            }
        }
        .toMap()
}


private fun saveMetroTilePositions(
    context: Context,
    gridUnits: Int,
    positions: Map<String, MetroTilePosition>,
) {
    val encoded =
        positions.entries.joinToString(";") {
                (tileId, position),
            ->

            "$tileId:${position.column}:${position.row}"
        }

    context
        .getSharedPreferences(
            METRO_TILE_PREFS,
            Context.MODE_PRIVATE,
        )
        .edit()
        .putString(
            metroTilePositionsPreferenceKey(
                gridUnits,
            ),
            encoded,
        )
        .apply()
}


internal fun validateMetroTileLayout(
    positions: Map<String, MetroTilePosition>,
    sizes: Map<String, MetroTileSize>,
    tileIds: Collection<String>,
    gridUnits: Int,
    workspaceRows: Int = METRO_WORKSPACE_ROWS,
): Boolean {
    if (
        gridUnits <= 0 ||
        workspaceRows <= 0
    ) {
        return false
    }

    /*
     * Every tile participating in the layout must have
     * both a position and a size.
     */
    if (
        tileIds.any { tileId ->
            tileId !in positions ||
                tileId !in sizes
        }
    ) {
        return false
    }

    /*
     * No unknown / duplicated logical layout state.
     *
     * Map keys are already unique, but positions for tiles
     * outside tileIds are intentionally ignored because
     * capability-dependent tiles may currently be hidden.
     */
    tileIds.forEach { tileId ->
        val position =
            positions[tileId]
                ?: return false

        val size =
            sizes[tileId]
                ?: return false

        val (
            widthUnits,
            heightUnits,
        ) =
            metroTileSpan(size)

        if (
            position.column < 0 ||
            position.row < 0 ||
            position.column +
                widthUnits >
                gridUnits ||
            position.row +
                heightUnits >
                workspaceRows
        ) {
            return false
        }
    }

    /*
     * Pairwise collision test.
     */
    val ids =
        tileIds.toList()

    for (
        firstIndex in
        0 until ids.size
    ) {
        val firstId =
            ids[firstIndex]

        val firstPosition =
            positions[firstId]
                ?: return false

        val firstSize =
            sizes[firstId]
                ?: return false

        val (
            firstWidth,
            firstHeight,
        ) =
            metroTileSpan(firstSize)

        val firstLeft =
            firstPosition.column

        val firstTop =
            firstPosition.row

        val firstRight =
            firstLeft + firstWidth

        val firstBottom =
            firstTop + firstHeight

        for (
            secondIndex in
            firstIndex + 1 until ids.size
        ) {
            val secondId =
                ids[secondIndex]

            val secondPosition =
                positions[secondId]
                    ?: return false

            val secondSize =
                sizes[secondId]
                    ?: return false

            val (
                secondWidth,
                secondHeight,
            ) =
                metroTileSpan(secondSize)

            val secondLeft =
                secondPosition.column

            val secondTop =
                secondPosition.row

            val secondRight =
                secondLeft + secondWidth

            val secondBottom =
                secondTop + secondHeight

            val overlaps =
                firstLeft < secondRight &&
                    firstRight > secondLeft &&
                    firstTop < secondBottom &&
                    firstBottom > secondTop

            if (overlaps) {
                return false
            }
        }
    }

    return true
}


private fun canPlaceMetroTile(
    tileId: String,
    position: MetroTilePosition,
    positions: Map<String, MetroTilePosition>,
    sizes: Map<String, MetroTileSize>,
    gridUnits: Int,
): Boolean {
    val size =
        sizes[tileId]
            ?: MetroTileSize.Small

    val (width, height) =
        metroTileSpan(size)

    if (
        position.column < 0 ||
        position.row < 0 ||
        position.column + width > gridUnits
    ) {
        return false
    }

    val left =
        position.column

    val top =
        position.row

    val right =
        left + width

    val bottom =
        top + height

    positions.forEach {
            (otherId, otherPosition),
        ->

        if (otherId == tileId) {
            return@forEach
        }

        val otherSize =
            sizes[otherId]
                ?: MetroTileSize.Small

        val (otherWidth, otherHeight) =
            metroTileSpan(otherSize)

        val otherLeft =
            otherPosition.column

        val otherTop =
            otherPosition.row

        val otherRight =
            otherLeft + otherWidth

        val otherBottom =
            otherTop + otherHeight

        val overlaps =
            left < otherRight &&
                right > otherLeft &&
                top < otherBottom &&
                bottom > otherTop

        if (overlaps) {
            return false
        }
    }

    return true
}


private fun buildBoundedMetroTilePositions(
    order: List<String>,
    sizes: Map<String, MetroTileSize>,
    gridUnits: Int,
    workspaceRows: Int,
): Map<String, MetroTilePosition>? {
    val result =
        linkedMapOf<String, MetroTilePosition>()

    order.forEach { tileId ->
        val size =
            sizes[tileId]
                ?: return null

        val (
            width,
            height,
        ) =
            metroTileSpan(size)

        val maxColumn =
            gridUnits - width

        val maxRow =
            workspaceRows - height

        if (
            maxColumn < 0 ||
            maxRow < 0
        ) {
            return null
        }

        var placed = false

        search@ for (row in 0..maxRow) {
            for (column in 0..maxColumn) {
                val candidate =
                    MetroTilePosition(
                        column = column,
                        row = row,
                    )

                if (
                    canPlaceMetroTile(
                        tileId = tileId,
                        position = candidate,
                        positions = result,
                        sizes = sizes,
                        gridUnits = gridUnits,
                    )
                ) {
                    result[tileId] =
                        candidate

                    placed = true
                    break@search
                }
            }
        }

        if (!placed) {
            return null
        }
    }

    if (
        !validateMetroTileLayout(
            positions = result,
            sizes = sizes,
            tileIds = order,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return null
    }

    return result
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
    tileId: String,
    gridUnits: Int,
    default: MetroTileSize,
): MetroTileSize {
    val preferences =
        context
            .getSharedPreferences(
                METRO_TILE_PREFS,
                Context.MODE_PRIVATE,
            )

    val perGridKey =
        metroTileSizePreferenceKey(
            tileId = tileId,
            gridUnits = gridUnits,
        )

    val resolution =
        resolveMetroTileSizePreference(
            perGridStored =
                preferences.getString(
                    perGridKey,
                    null,
                ),
            legacyStored =
                preferences.getString(
                    tileId,
                    null,
                ),
            default = default,
        )

    if (resolution.shouldMigrateLegacy) {
        preferences
            .edit()
            .putString(
                perGridKey,
                resolution.size.name,
            )
            .apply()
    }

    return resolution.size
}


private fun saveMetroTileSize(
    context: Context,
    tileId: String,
    gridUnits: Int,
    size: MetroTileSize,
) {
    context
        .getSharedPreferences(
            METRO_TILE_PREFS,
            Context.MODE_PRIVATE,
        )
        .edit()
        .putString(
            metroTileSizePreferenceKey(
                tileId = tileId,
                gridUnits = gridUnits,
            ),
            size.name,
        )
        .apply()
}


private fun nextMetroTileSize(
    current: MetroTileSize,
): MetroTileSize =
    when (current) {
        MetroTileSize.Small ->
            MetroTileSize.Medium

        MetroTileSize.Medium ->
            MetroTileSize.Wide

        MetroTileSize.Wide ->
            MetroTileSize.Large

        MetroTileSize.Large ->
            MetroTileSize.Small
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
            MetroTileSize.Wide,
            MetroTileSize.Large -> 4
        }

    val heightUnits =
        when (tileSize) {
            MetroTileSize.Small -> 1

            MetroTileSize.Medium,
            MetroTileSize.Wide -> 2

            MetroTileSize.Large -> 4
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
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onUpload: () -> Unit,
    onCreateDirectory: () -> Unit,
) {
    if (!allowUpload && storage == null) {
        return
    }

    val context = LocalContext.current

    var tileOrder by remember(context) {
        mutableStateOf(
            loadMetroTileOrder(context),
        )
    }

    val tileBounds = remember {
        mutableStateMapOf<String, Rect>()
    }

    var draggingTile by remember {
        mutableStateOf<String?>(null)
    }

    var selectedTile by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(editMode) {
        if (!editMode) {
            selectedTile = null
        }
    }

    /*
     * Absolute pointer position inside the dashboard.
     *
     * The drag preview uses this directly and therefore does
     * not care where FlowRow moves the placeholder.
     */
    var dragPosition by remember {
        mutableStateOf<Offset?>(null)
    }

    var dragStartPositions by remember {
        mutableStateOf<
            Map<String, MetroTilePosition>?
        >(null)
    }

    val gridUnits =
        if (showMoreTiles) {
            8
        } else {
            6
        }

    var uploadSize by remember(
        context,
        gridUnits,
    ) {
        mutableStateOf(
            loadMetroTileSize(
                context,
                METRO_TILE_UPLOAD,
                gridUnits,
                MetroTileSize.Medium,
            ),
        )
    }

    var directorySize by remember(
        context,
        gridUnits,
    ) {
        mutableStateOf(
            loadMetroTileSize(
                context,
                METRO_TILE_DIRECTORY,
                gridUnits,
                MetroTileSize.Medium,
            ),
        )
    }

    var storageSize by remember(
        context,
        gridUnits,
    ) {
        mutableStateOf(
            loadMetroTileSize(
                context,
                METRO_TILE_STORAGE,
                gridUnits,
                MetroTileSize.Wide,
            ),
        )
    }

    val currentTileSizes =
        mapOf(
            METRO_TILE_UPLOAD to uploadSize,
            METRO_TILE_DIRECTORY to directorySize,
            METRO_TILE_STORAGE to storageSize,
        )

    val safeDefaultTileSizes =
        mapOf(
            METRO_TILE_UPLOAD to
                MetroTileSize.Medium,
            METRO_TILE_DIRECTORY to
                MetroTileSize.Medium,
            METRO_TILE_STORAGE to
                MetroTileSize.Wide,
        )

    var tilePositions by remember(
        context,
        gridUnits,
    ) {
        val tileIds =
            defaultMetroTileOrder()

        val saved =
            loadMetroTilePositions(
                context,
                gridUnits,
            )

        val validSaved =
            saved?.takeIf { positions ->
                validateMetroTileLayout(
                    positions = positions,
                    sizes = currentTileSizes,
                    tileIds = tileIds,
                    gridUnits = gridUnits,
                    workspaceRows =
                        METRO_WORKSPACE_ROWS,
                )
            }

        val packedCurrent =
            buildBoundedMetroTilePositions(
                order = tileOrder,
                sizes = currentTileSizes,
                gridUnits = gridUnits,
                workspaceRows =
                    METRO_WORKSPACE_ROWS,
            )

        val safeFallback =
            buildBoundedMetroTilePositions(
                order = tileOrder,
                sizes = safeDefaultTileSizes,
                gridUnits = gridUnits,
                workspaceRows =
                    METRO_WORKSPACE_ROWS,
            )
                ?: error(
                    "Safe Metro layout does not fit workspace",
                )

        mutableStateOf(
            validSaved
                ?: packedCurrent
                ?: safeFallback,
        )
    }

    /*
     * One-time migration for layouts created before the
     * bounded 4-row Tile Engine.
     *
     * If the persisted sizes cannot form a legal layout in
     * the current density, restore only the Metro tile layout
     * to safe sizes. Other application preferences remain
     * untouched.
     */
    LaunchedEffect(
        context,
        gridUnits,
    ) {
        val tileIds =
            defaultMetroTileOrder()

        val currentLayoutIsValid =
            validateMetroTileLayout(
                positions = tilePositions,
                sizes = currentTileSizes,
                tileIds = tileIds,
                gridUnits = gridUnits,
                workspaceRows =
                    METRO_WORKSPACE_ROWS,
            )

        if (!currentLayoutIsValid) {
            val safePositions =
                buildBoundedMetroTilePositions(
                    order = tileOrder,
                    sizes = safeDefaultTileSizes,
                    gridUnits = gridUnits,
                    workspaceRows =
                        METRO_WORKSPACE_ROWS,
                )
                    ?: return@LaunchedEffect

            uploadSize =
                safeDefaultTileSizes.getValue(
                    METRO_TILE_UPLOAD,
                )

            directorySize =
                safeDefaultTileSizes.getValue(
                    METRO_TILE_DIRECTORY,
                )

            storageSize =
                safeDefaultTileSizes.getValue(
                    METRO_TILE_STORAGE,
                )

            tilePositions =
                safePositions

            safeDefaultTileSizes.forEach {
                    (tileId, size),
                ->
                saveMetroTileSize(
                    context,
                    tileId,
                    gridUnits,
                    size,
                )
            }

            saveMetroTilePositions(
                context,
                gridUnits,
                safePositions,
            )
        } else {
            /*
             * Also replace an old invalid/missing persisted
             * position record with the bounded layout selected
             * during startup.
             */
            saveMetroTilePositions(
                context,
                gridUnits,
                tilePositions,
            )
        }
    }

    fun applyMetroTileSizeChange(
        tileId: String,
        newSize: MetroTileSize,
    ) {
        val currentPosition =
            tilePositions[tileId]
                ?: return

        /*
         * Only tiles which are currently available in the
         * dashboard participate in collision resolution.
         *
         * Hidden capability-dependent tiles may still have
         * persisted positions, but they must not block the
         * currently visible layout.
         */
        val activeTileIds =
            buildList {
                if (allowUpload) {
                    add(METRO_TILE_UPLOAD)
                    add(METRO_TILE_DIRECTORY)
                }

                if (storage != null) {
                    add(METRO_TILE_STORAGE)
                }
            }

        if (tileId !in activeTileIds) {
            return
        }

        /*
         * The resize button is a size-cycle control.
         *
         * The requested size is always tried first. If that
         * exact size cannot produce a valid bounded layout,
         * continue through the size cycle until the first
         * legal alternative is found.
         *
         * This prevents the UI from becoming stuck when, for
         * example, Wide -> Large is impossible but Small is
         * perfectly legal.
         *
         * Every candidate is resolved independently from the
         * same currently accepted layout.
         */
        val currentSize =
            currentTileSizes[tileId]
                ?: return

        val candidateSizes =
            buildList {
                var candidate =
                    newSize

                repeat(MetroTileSize.values().size) {
                    if (
                        candidate != currentSize &&
                        candidate !in this
                    ) {
                        add(candidate)
                    }

                    candidate =
                        nextMetroTileSize(candidate)
                }
            }

        val resolved =
            candidateSizes
                .firstNotNullOfOrNull { candidateSize ->
                    val (
                        widthUnits,
                        heightUnits,
                    ) =
                        metroTileSpan(
                            candidateSize,
                        )

                    val maxColumn =
                        gridUnits - widthUnits

                    val maxRow =
                        METRO_WORKSPACE_ROWS -
                            heightUnits

                    if (
                        maxColumn < 0 ||
                        maxRow < 0
                    ) {
                        null
                    } else {
                        /*
                         * Prefer keeping the tile exactly
                         * where it is.
                         *
                         * If the new size no longer fits at
                         * that anchor, search the nearest
                         * legal anchor. This is especially
                         * important for tiles placed at the
                         * right or bottom edge.
                         */
                        val candidatePositions =
                            buildList {
                                for (row in 0..maxRow) {
                                    for (
                                        column in
                                        0..maxColumn
                                    ) {
                                        add(
                                            MetroTilePosition(
                                                column =
                                                    column,
                                                row =
                                                    row,
                                            ),
                                        )
                                    }
                                }
                            }
                                .sortedWith(
                                    compareBy<
                                        MetroTilePosition
                                    > {
                                        kotlin.math.abs(
                                            it.column -
                                                currentPosition
                                                    .column,
                                        ) +
                                            kotlin.math.abs(
                                                it.row -
                                                    currentPosition
                                                        .row,
                                            )
                                    }.thenBy {
                                        it.row
                                    }.thenBy {
                                        it.column
                                    },
                                )

                        candidatePositions
                            .firstNotNullOfOrNull {
                                    candidatePosition,
                                ->
                                resolveMetroTileLayoutChange(
                                    tileId = tileId,
                                    requestedPosition =
                                        candidatePosition,
                                    requestedSize =
                                        candidateSize,
                                    positions =
                                        tilePositions,
                                    sizes =
                                        currentTileSizes,
                                    tileIds =
                                        activeTileIds,
                                    gridUnits =
                                        gridUnits,
                                    workspaceRows =
                                        METRO_WORKSPACE_ROWS,
                                )
                            }
                    }
                }
                ?: return

        /*
         * ACCEPT.
         *
         * State and persistence are changed only after the
         * complete candidate layout has passed the resolver
         * and validator.
         */
        when (tileId) {
            METRO_TILE_UPLOAD ->
                uploadSize =
                    resolved.sizes.getValue(tileId)

            METRO_TILE_DIRECTORY ->
                directorySize =
                    resolved.sizes.getValue(tileId)

            METRO_TILE_STORAGE ->
                storageSize =
                    resolved.sizes.getValue(tileId)
        }

        tilePositions =
            resolved.positions

        saveMetroTileSize(
            context,
            tileId,
            gridUnits,
            resolved.sizes.getValue(tileId),
        )

        saveMetroTilePositions(
            context,
            gridUnits,
            resolved.positions,
        )
    }


    val dashboardInteractionSource =
        remember {
            MutableInteractionSource()
        }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled =
                    editMode &&
                        draggingTile == null,
                interactionSource =
                    dashboardInteractionSource,
                indication = null,
                onClick = {
                    onEditModeChange(false)
                },
            )
            .padding(
                horizontal = 20.dp,
                vertical = 8.dp,
            ),
    ) {
        val gap = 8.dp

        val cellSize =
            (
                maxWidth -
                    gap * (gridUnits - 1)
            ) / gridUnits

        val density =
            LocalDensity.current

        val cellPx =
            with(density) {
                cellSize.toPx()
            }

        val gapPx =
            with(density) {
                gap.toPx()
            }

        val gridPitchPx =
            cellPx + gapPx

        val availableTiles =
            buildSet {
                if (allowUpload) {
                    add(METRO_TILE_UPLOAD)
                    add(METRO_TILE_DIRECTORY)
                }

                if (storage != null) {
                    add(METRO_TILE_STORAGE)
                }
            }

        val visibleOrder =
            tileOrder.filter {
                it in availableTiles
            }

        /*
         * NORMAL FLOW
         *
         * The dragged item still owns its slot, so FlowRow can
         * calculate and live-update the layout.
         *
         * ReorderableMetroTile hides the real content while it
         * is being dragged.
         */
        MetroPositionedLayout(
            tileSizes =
                visibleOrder.map { tileId ->
                    when (tileId) {
                        METRO_TILE_UPLOAD ->
                            uploadSize

                        METRO_TILE_DIRECTORY ->
                            directorySize

                        METRO_TILE_STORAGE ->
                            storageSize

                        else ->
                            MetroTileSize.Small
                    }
                },
            tilePositions =
                visibleOrder.map { tileId ->
                    tilePositions.getValue(
                        tileId,
                    )
                },
            gridUnits = gridUnits,
            workspaceRows =
                METRO_WORKSPACE_ROWS,
            cellSize = cellSize,
            gap = gap,
            modifier = Modifier.fillMaxWidth(),
        ) {
            visibleOrder.forEach { tileId ->
                key(tileId) {
                    val tileModifier =
                        when (tileId) {
                            METRO_TILE_UPLOAD ->
                                Modifier.metroTileDimensions(
                                    uploadSize,
                                    cellSize,
                                    gap,
                                )

                            METRO_TILE_DIRECTORY ->
                                Modifier.metroTileDimensions(
                                    directorySize,
                                    cellSize,
                                    gap,
                                )

                            METRO_TILE_STORAGE ->
                                Modifier.metroTileDimensions(
                                    storageSize,
                                    cellSize,
                                    gap,
                                )

                            else ->
                                Modifier
                        }

                    ReorderableMetroTile(
                        tileId = tileId,
                        editMode = editMode,
                        isDragging =
                            draggingTile == tileId,
                        onBoundsChanged = { bounds ->
                            tileBounds[tileId] = bounds
                        },
                        onDragStart = {
                            val bounds =
                                tileBounds[tileId]

                            if (bounds != null) {
                                selectedTile = tileId
                                onEditModeChange(true)

                                draggingTile = tileId

                                dragPosition =
                                    bounds.center

                                dragStartPositions =
                                    tilePositions
                            }
                        },
                        onDrag = { amount ->
                            val current =
                                dragPosition

                            if (current != null) {
                                val next =
                                    current + amount

                                /*
                                 * The detached overlay follows
                                 * the pointer without changing
                                 * dashboard geometry.
                                 */
                                dragPosition =
                                    next

                                val tileSize =
                                    currentTileSizes[tileId]
                                        ?: MetroTileSize.Small

                                val (
                                    widthUnits,
                                    heightUnits,
                                ) =
                                    metroTileSpan(
                                        tileSize,
                                    )

                                val tileWidthPx =
                                    cellPx * widthUnits +
                                        gapPx *
                                            (widthUnits - 1)

                                val tileHeightPx =
                                    cellPx * heightUnits +
                                        gapPx *
                                            (heightUnits - 1)

                                val rawColumn =
                                    kotlin.math.round(
                                        (
                                            next.x -
                                                tileWidthPx /
                                                    2f
                                        ) /
                                            gridPitchPx,
                                    ).toInt()

                                val rawRow =
                                    kotlin.math.round(
                                        (
                                            next.y -
                                                tileHeightPx /
                                                    2f
                                        ) /
                                            gridPitchPx,
                                    ).toInt()

                                /*
                                 * Do not clamp an illegal
                                 * target into the workspace.
                                 *
                                 * T09-T12 require an out of
                                 * bounds drag to be REJECT,
                                 * preserving the last valid
                                 * logical layout.
                                 */
                                val candidate =
                                    MetroTilePosition(
                                        column = rawColumn,
                                        row = rawRow,
                                    )

                                /*
                                 * Resolve every live preview
                                 * from the layout captured at
                                 * drag start.
                                 *
                                 * This prevents cumulative
                                 * reflow drift when the pointer
                                 * moves through several cells
                                 * and then comes back.
                                 */
                                val basePositions =
                                    dragStartPositions
                                        ?: tilePositions

                                val resolved =
                                    resolveMetroTileLayoutChange(
                                        tileId = tileId,
                                        requestedPosition =
                                            candidate,
                                        requestedSize =
                                            tileSize,
                                        positions =
                                            basePositions,
                                        sizes =
                                            currentTileSizes,
                                        tileIds =
                                            visibleOrder,
                                        gridUnits =
                                            gridUnits,
                                        workspaceRows =
                                            METRO_WORKSPACE_ROWS,
                                    )

                                if (resolved != null) {
                                    tilePositions =
                                        resolved.positions
                                }
                            }
                        },
                        onDragEnd = {
                            val finalLayoutIsValid =
                                validateMetroTileLayout(
                                    positions =
                                        tilePositions,
                                    sizes =
                                        currentTileSizes,
                                    tileIds =
                                        visibleOrder,
                                    gridUnits =
                                        gridUnits,
                                    workspaceRows =
                                        METRO_WORKSPACE_ROWS,
                                )

                            if (finalLayoutIsValid) {
                                saveMetroTilePositions(
                                    context,
                                    gridUnits,
                                    tilePositions,
                                )
                            } else {
                                /*
                                 * Defensive fallback.
                                 *
                                 * The new resolver should never
                                 * produce an invalid candidate,
                                 * but persistence must never
                                 * accept one even if another UI
                                 * regression appears later.
                                 */
                                dragStartPositions?.let {
                                    tilePositions = it
                                }
                            }

                            draggingTile = null
                            dragPosition = null
                            dragStartPositions = null
                        },
                        onDragCancel = {
                            dragStartPositions?.let {
                                tilePositions = it
                            }

                            draggingTile = null
                            dragPosition = null
                            dragStartPositions = null
                        },
                        modifier = tileModifier,
                    ) {
                        when (tileId) {
                            METRO_TILE_UPLOAD -> {
                                MetroActionTile(
                                    icon =
                                        MetroActionGlyphType.Upload,
                                    label = "Wyślij plik",
                                    enabled = !busy && !editMode,
                                    tileSize = uploadSize,
                                    onClick = onUpload,
                                    onLongClick = {
                                        selectedTile = tileId
                                        onEditModeChange(true)
                                    },
                                    showResizeControl =
                                        editMode &&
                                            selectedTile == tileId &&
                                            draggingTile == null,
                                    onSizeChange = { newSize ->
                                        applyMetroTileSizeChange(
                                            tileId,
                                            newSize,
                                        )
                                    },
                                    modifier =
                                        Modifier.fillMaxSize(),
                                )
                            }

                            METRO_TILE_DIRECTORY -> {
                                MetroActionTile(
                                    icon =
                                        MetroActionGlyphType.NewFolder,
                                    label = "Katalog",
                                    enabled = !busy && !editMode,
                                    tileSize = directorySize,
                                    onClick =
                                        onCreateDirectory,
                                    onLongClick = {
                                        selectedTile = tileId
                                        onEditModeChange(true)
                                    },
                                    showResizeControl =
                                        editMode &&
                                            selectedTile == tileId &&
                                            draggingTile == null,
                                    onSizeChange = { newSize ->
                                        applyMetroTileSizeChange(
                                            tileId,
                                            newSize,
                                        )
                                    },
                                    modifier =
                                        Modifier.fillMaxSize(),
                                )
                            }

                            METRO_TILE_STORAGE -> {
                                storage?.let {
                                    StorageTile(
                                        storage = it,
                                        tileSize = storageSize,
                                        onLongClick = {
                                            selectedTile = tileId
                                            onEditModeChange(true)
                                        },
                                        showResizeControl =
                                            editMode &&
                                                selectedTile == tileId &&
                                                draggingTile == null,
                                        onSizeChange = { newSize ->
                                            applyMetroTileSizeChange(
                                                tileId,
                                                newSize,
                                            )
                                        },
                                        modifier =
                                            Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        /*
         * DRAG OVERLAY
         *
         * This is the ONLY visible copy of the dragged tile.
         *
         * It is positioned from the absolute pointer position,
         * independently of the tile's current FlowRow slot.
         */
        val activeTile =
            draggingTile

        val pointer =
            dragPosition

        if (
            activeTile != null &&
            pointer != null
        ) {
            val bounds =
                tileBounds[activeTile]

            if (bounds != null) {
                val previewModifier =
                    when (activeTile) {
                        METRO_TILE_UPLOAD ->
                            Modifier.metroTileDimensions(
                                uploadSize,
                                cellSize,
                                gap,
                            )

                        METRO_TILE_DIRECTORY ->
                            Modifier.metroTileDimensions(
                                directorySize,
                                cellSize,
                                gap,
                            )

                        METRO_TILE_STORAGE ->
                            Modifier.metroTileDimensions(
                                storageSize,
                                cellSize,
                                gap,
                            )

                        else ->
                            Modifier
                    }

                Box(
                    modifier = previewModifier
                        .graphicsLayer {
                            translationX =
                                pointer.x -
                                    bounds.width / 2f

                            translationY =
                                pointer.y -
                                    bounds.height / 2f

                            scaleX = 1.035f
                            scaleY = 1.035f
                            alpha = 0.92f
                        }
                        .zIndex(100f),
                ) {
                    when (activeTile) {
                        METRO_TILE_UPLOAD -> {
                            MetroActionTile(
                                icon =
                                    MetroActionGlyphType.Upload,
                                label = "Wyślij plik",
                                enabled = true,
                                tileSize = uploadSize,
                                onClick = {},
                                onSizeChange = {},
                                modifier =
                                    Modifier.fillMaxSize(),
                            )
                        }

                        METRO_TILE_DIRECTORY -> {
                            MetroActionTile(
                                icon =
                                    MetroActionGlyphType.NewFolder,
                                label = "Katalog",
                                enabled = true,
                                tileSize = directorySize,
                                onClick = {},
                                onSizeChange = {},
                                modifier =
                                    Modifier.fillMaxSize(),
                            )
                        }

                        METRO_TILE_STORAGE -> {
                            storage?.let {
                                StorageTile(
                                    storage = it,
                                    tileSize = storageSize,
                                    onSizeChange = {},
                                    modifier =
                                        Modifier.fillMaxSize(),
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
private fun ReorderableMetroTile(
    tileId: String,
    editMode: Boolean,
    isDragging: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val currentOnDragStart by
        rememberUpdatedState(
            onDragStart,
        )

    val currentOnDrag by
        rememberUpdatedState(
            onDrag,
        )

    val currentOnDragEnd by
        rememberUpdatedState(
            onDragEnd,
        )

    val currentOnDragCancel by
        rememberUpdatedState(
            onDragCancel,
        )

    /*
     * Translation used only for automatic reflow animation.
     *
     * The dragged tile itself never uses these offsets.
     */
    val reflowX = remember(tileId) {
        Animatable(0f)
    }

    val reflowY = remember(tileId) {
        Animatable(0f)
    }

    var previousBounds by remember(tileId) {
        mutableStateOf<Rect?>(null)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val newBounds =
                    coordinates.boundsInParent()

                /*
                 * Drag/drop hit testing always receives the real
                 * FlowRow slot, not the animated visual position.
                 */
                onBoundsChanged(newBounds)

                val oldBounds =
                    previousBounds

                if (
                    !isDragging &&
                    oldBounds != null
                ) {
                    val dx =
                        oldBounds.left -
                            newBounds.left

                    val dy =
                        oldBounds.top -
                            newBounds.top

                    /*
                     * Ignore sub-pixel layout noise.
                     */
                    if (
                        kotlin.math.abs(dx) > 0.5f ||
                        kotlin.math.abs(dy) > 0.5f
                    ) {
                        /*
                         * Preserve the tile's previous visual
                         * position even if another animation was
                         * already running.
                         */
                        val startX =
                            reflowX.value + dx

                        val startY =
                            reflowY.value + dy

                        scope.launch {
                            reflowX.snapTo(startX)

                            reflowX.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 150,
                                ),
                            )
                        }

                        scope.launch {
                            reflowY.snapTo(startY)

                            reflowY.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 150,
                                ),
                            )
                        }
                    }
                }

                previousBounds =
                    newBounds
            }
            .pointerInput(
                editMode,
                tileId,
            ) {
                if (editMode) {
                    detectDragGestures(
                        onDragStart = {
                            currentOnDragStart()
                        },
                        onDragEnd = {
                            currentOnDragEnd()
                        },
                        onDragCancel = {
                            currentOnDragCancel()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            currentOnDrag(amount)
                        },
                    )
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    /*
                     * The real dragged tile remains hidden because
                     * the detached dashboard overlay is the visible
                     * copy under the user's finger.
                     *
                     * Other tiles receive only the short reflow
                     * translation animation.
                     */
                    if (isDragging) {
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                    } else {
                        alpha = 1f
                        translationX = reflowX.value
                        translationY = reflowY.value
                    }
                },
        ) {
            content()
        }
    }
}


@Composable
private fun MetroPositionedLayout(
    tileSizes: List<MetroTileSize>,
    tilePositions: List<MetroTilePosition>,
    gridUnits: Int,
    workspaceRows: Int,
    cellSize: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->

        val cellPx =
            cellSize.roundToPx()

        val gapPx =
            gap.roundToPx()

        val placeables =
            measurables.mapIndexed {
                    index,
                    measurable,
                ->

                val tileSize =
                    tileSizes.getOrElse(index) {
                        MetroTileSize.Small
                    }

                val (
                    widthUnits,
                    heightUnits,
                ) =
                    metroTileSpan(
                        tileSize,
                    )

                val widthPx =
                    cellPx * widthUnits +
                        gapPx *
                            (widthUnits - 1)

                val heightPx =
                    cellPx * heightUnits +
                        gapPx *
                            (heightUnits - 1)

                measurable.measure(
                    androidx.compose.ui.unit
                        .Constraints.fixed(
                            widthPx,
                            heightPx,
                        ),
                )
            }

        /*
         * Workspace geometry is fixed.
         *
         * Tile positions must never control the height of the
         * dashboard. This guarantees that dragging a tile can
         * never push the file list further down the screen.
         */
        val requestedHeight =
            if (workspaceRows > 0) {
                cellPx * workspaceRows +
                    gapPx *
                        (workspaceRows - 1)
            } else {
                0
            }

        val layoutHeight =
            requestedHeight
                .coerceAtLeast(
                    constraints.minHeight,
                )
                .coerceAtMost(
                    constraints.maxHeight,
                )

        layout(
            width = constraints.maxWidth,
            height = layoutHeight,
        ) {
            placeables.forEachIndexed {
                    index,
                    placeable,
                ->

                val position =
                    tilePositions.getOrElse(index) {
                        MetroTilePosition(
                            column = 0,
                            row = 0,
                        )
                    }

                val x =
                    position.column *
                        (cellPx + gapPx)

                val y =
                    position.row *
                        (cellPx + gapPx)

                placeable.placeRelative(
                    x = x,
                    y = y,
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
    onLongClick: () -> Unit = {},
    showResizeControl: Boolean = false,
    modifier: Modifier = Modifier,
) {
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
                    onLongClick = onLongClick,
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
            when (tileSize) {
                MetroTileSize.Small -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetroActionGlyph(
                            type = icon,
                            glyphSize = 18.dp,
                        )
                    }
                }

                MetroTileSize.Medium -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            MetroActionGlyph(
                                type = icon,
                                glyphSize = 26.dp,
                            )
                        }

                        Text(
                            text = label,
                            style =
                                MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                MetroTileSize.Wide -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            MetroActionGlyph(
                                type = icon,
                                glyphSize = 30.dp,
                            )
                        }

                        Text(
                            text = label,
                            style =
                                MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                MetroTileSize.Large -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    ) {
                        MetroActionGlyph(
                            type = icon,
                            glyphSize = 38.dp,
                        )

                        Spacer(
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = label,
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        if (showResizeControl) {
            MetroResizeHandle(
                onClick = {
                    onSizeChange(
                        nextMetroTileSize(tileSize),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
            )
        }



}
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageTile(
    storage: RouterCloudStorage,
    tileSize: MetroTileSize,
    onSizeChange: (MetroTileSize) -> Unit,
    onLongClick: () -> Unit = {},
    showResizeControl: Boolean = false,
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

    Box(
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                ),
            tonalElevation = 0.dp,
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
                            .padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = String.format(
                                    Locale.getDefault(),
                                    "%.1f%%",
                                    percentage,
                                ),
                                style =
                                    MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                            )
                        }

                        Text(
                            text = "Pamięć",
                            style =
                                MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                MetroTileSize.Wide -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment =
                                Alignment.Top,
                        ) {
                            Text(
                                text =
                                    "${formatBytes(storage.used)} zajęte",
                                style =
                                    MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                            )

                            Text(
                                text = String.format(
                                    Locale.getDefault(),
                                    "%.1f%%",
                                    percentage,
                                ),
                                style =
                                    MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                            )
                        }

                        Spacer(
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = "Pamięć",
                            style =
                                MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                MetroTileSize.Large -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment =
                                Alignment.Top,
                        ) {
                            Text(
                                text = "Pamięć",
                                style =
                                    MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                            )

                            Text(
                                text = String.format(
                                    Locale.getDefault(),
                                    "%.1f%%",
                                    percentage,
                                ),
                                style =
                                    MaterialTheme.typography.bodyLarge,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                maxLines = 1,
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(14.dp),
                        )

                        Text(
                            text =
                                "${formatBytes(storage.used)}\nzajęte",
                            style =
                                MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Normal,
                            maxLines = 2,
                        )

                        Spacer(
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text =
                                "Wolne: ${formatBytes(storage.available)}",
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (showResizeControl) {
            MetroResizeHandle(
                onClick = {
                    onSizeChange(
                        nextMetroTileSize(tileSize),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
            )
        }



}
}


@Composable
private fun MetroResizeHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphColor =
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = 0.96f,
        )

    Box(
        modifier = modifier
            .requiredSize(28.dp)
            .zIndex(50f)
            .clickable(
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val lineWidth =
                1.6.dp.toPx()

            // Circular Windows Phone style control.
            drawCircle(
                color = glyphColor,
                radius = size.width * 0.42f,
                style = Stroke(
                    width = lineWidth,
                ),
            )

            // Diagonal stem.
            val tip =
                Offset(
                    x = size.width * 0.34f,
                    y = size.height * 0.34f,
                )

            val tail =
                Offset(
                    x = size.width * 0.67f,
                    y = size.height * 0.67f,
                )

            drawLine(
                color = glyphColor,
                start = tail,
                end = tip,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
            )

            // Upper-left arrow head.
            drawLine(
                color = glyphColor,
                start = tip,
                end = Offset(
                    x = size.width * 0.34f,
                    y = size.height * 0.51f,
                ),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
            )

            drawLine(
                color = glyphColor,
                start = tip,
                end = Offset(
                    x = size.width * 0.51f,
                    y = size.height * 0.34f,
                ),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
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
