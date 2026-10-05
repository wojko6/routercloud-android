package com.wojko6.routercloud

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

internal data class RouterCloudLocalEntry(
    val relativePath: String,
    val uri: Uri,
    val mimeType: String?,
    val size: Long?,
    val modifiedAt: Long?,
    val isDirectory: Boolean,
)

internal class RouterCloudLocalTreeScanner(
    context: Context,
) {
    private val resolver =
        context.contentResolver

    fun scan(
        treeUri: Uri,
    ): List<RouterCloudLocalEntry> {
        val rootDocumentId =
            DocumentsContract
                .getTreeDocumentId(treeUri)

        val result =
            mutableListOf<RouterCloudLocalEntry>()

        scanDirectory(
            treeUri = treeUri,
            parentDocumentId = rootDocumentId,
            parentRelativePath = "",
            result = result,
        )

        return result.sortedBy {
            it.relativePath.lowercase()
        }
    }

    private fun scanDirectory(
        treeUri: Uri,
        parentDocumentId: String,
        parentRelativePath: String,
        result: MutableList<RouterCloudLocalEntry>,
    ) {
        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    treeUri,
                    parentDocumentId,
                )

        val cursor =
            resolver.query(
                childrenUri,
                PROJECTION,
                null,
                null,
                null,
            ) ?: throw IOException(
                "Nie udało się odczytać wybranego folderu."
            )

        cursor.use {
            val idColumn =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )

            val nameColumn =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )

            val mimeColumn =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )

            val sizeColumn =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE,
                )

            val modifiedColumn =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )

            while (it.moveToNext()) {
                val documentId =
                    it.getString(idColumn)

                val name =
                    it.getString(nameColumn)

                if (
                    name.isBlank() ||
                    '/' in name
                ) {
                    throw IOException(
                        "Nieobsługiwana nazwa elementu: $name"
                    )
                }

                val mimeType =
                    if (it.isNull(mimeColumn)) {
                        null
                    } else {
                        it.getString(mimeColumn)
                    }

                val isDirectory =
                    mimeType ==
                        DocumentsContract.Document.MIME_TYPE_DIR

                val relativePath =
                    if (parentRelativePath.isEmpty()) {
                        name
                    } else {
                        "$parentRelativePath/$name"
                    }

                val documentUri =
                    DocumentsContract
                        .buildDocumentUriUsingTree(
                            treeUri,
                            documentId,
                        )

                val size =
                    if (
                        isDirectory ||
                        it.isNull(sizeColumn)
                    ) {
                        null
                    } else {
                        it.getLong(sizeColumn)
                    }

                val modifiedAt =
                    if (it.isNull(modifiedColumn)) {
                        null
                    } else {
                        it.getLong(modifiedColumn)
                    }

                result +=
                    RouterCloudLocalEntry(
                        relativePath = relativePath,
                        uri = documentUri,
                        mimeType = mimeType,
                        size = size,
                        modifiedAt = modifiedAt,
                        isDirectory = isDirectory,
                    )

                if (isDirectory) {
                    scanDirectory(
                        treeUri = treeUri,
                        parentDocumentId = documentId,
                        parentRelativePath = relativePath,
                        result = result,
                    )
                }
            }
        }
    }

    private companion object {
        val PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
    }
}
