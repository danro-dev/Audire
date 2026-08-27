package com.example.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.URLDecoder

data class FolderNode(
    val path: String,
    val name: String,
    val parentPath: String? = null,
    val subFolders: MutableList<FolderNode> = mutableListOf(),
    val books: MutableList<Audiobook> = mutableListOf()
) {
    val totalBooksCount: Int
        get() = books.size + subFolders.sumOf { it.totalBooksCount }

    val totalSubfoldersCount: Int
        get() = subFolders.size + subFolders.sumOf { it.totalSubfoldersCount }
}

object FolderHierarchyBuilder {

    fun buildHierarchy(books: List<Audiobook>, context: Context, lang: String): FolderNode {
        val rootName = if (lang == "es") "Almacén Principal" else "Root Storage"
        val root = FolderNode(path = "root", name = rootName)

        for (book in books) {
            val segments = extractDirectorySegments(book.filePath, lang)
            var currentNode = root
            var currentPathAccumulator = "root"

            for (i in 0 until segments.size - 1) {
                val segName = segments[i]
                currentPathAccumulator += "/$segName"

                var child = currentNode.subFolders.find { it.name == segName }
                if (child == null) {
                    child = FolderNode(
                        path = currentPathAccumulator,
                        name = segName,
                        parentPath = currentNode.path
                    )
                    currentNode.subFolders.add(child)
                }
                currentNode = child
            }

            // The book belongs to the innermost directory
            currentNode.books.add(book)
        }

        // Sort subfolders and books alphabetically
        sortNode(root)
        return root
    }

    private fun sortNode(node: FolderNode) {
        node.subFolders.sortBy { it.name.lowercase() }
        node.books.sortBy { it.title.lowercase() }
        for (sub in node.subFolders) {
            sortNode(sub)
        }
    }

    fun findNode(root: FolderNode, targetPath: String): FolderNode {
        if (root.path == targetPath) return root
        for (sub in root.subFolders) {
            val found = findNode(sub, targetPath)
            if (found.path == targetPath) return found
        }
        return root
    }

    fun getBreadcrumbs(root: FolderNode, currentPath: String): List<FolderNode> {
        val list = mutableListOf<FolderNode>()
        var curr: FolderNode? = findNode(root, currentPath)
        while (curr != null) {
            list.add(0, curr)
            if (curr.parentPath == null || curr.path == "root") break
            curr = findNode(root, curr.parentPath!!)
        }
        return if (list.isEmpty()) listOf(root) else list
    }

    fun extractDirectorySegments(filePath: String, lang: String): List<String> {
        if (filePath.startsWith("demo://")) {
            val folderName = if (lang == "es") "Libros de Ejemplo" else "Demo Books"
            val fileName = filePath.removePrefix("demo://")
            return listOf(folderName, fileName)
        }

        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            val folderName = if (lang == "es") "Transmisión / Nube" else "Streaming / Cloud"
            val fileName = filePath.substringAfterLast("/")
            return listOf(folderName, fileName)
        }

        if (filePath.startsWith("content://")) {
            try {
                val uri = Uri.parse(filePath)
                val rawPath = uri.path ?: ""
                val decoded = URLDecoder.decode(rawPath, "UTF-8")

                // Handle tree / document uris e.g., /tree/primary:Books/document/primary:Books/Manga/Ch1.cbz
                if (decoded.contains(":")) {
                    val splitColon = decoded.split(":")
                    val meaningfulSub = splitColon.last()
                    val parts = meaningfulSub.split("/").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        return parts
                    } else if (parts.size == 1) {
                        val baseFolder = if (lang == "es") "Almacenamiento del Dispositivo" else "Device Storage"
                        return listOf(baseFolder, parts[0])
                    }
                }

                val slashParts = decoded.split("/").filter { it.isNotBlank() }
                if (slashParts.size >= 2) {
                    return slashParts.takeLast(4)
                }
            } catch (e: Exception) {
                // fallback
            }
            val defaultFolder = if (lang == "es") "Archivos del Dispositivo" else "Device Files"
            return listOf(defaultFolder, filePath.substringAfterLast("/"))
        }

        // Direct file paths / file://
        try {
            val cleanPath = if (filePath.startsWith("file://")) Uri.parse(filePath).path ?: filePath else filePath
            val file = File(cleanPath)
            val segments = mutableListOf<String>()
            var curr: File? = file
            while (curr != null && curr.name.isNotEmpty()) {
                segments.add(0, curr.name)
                curr = curr.parentFile
                // Stop if we hit root or storage/emulated/0
                if (curr?.path == "/storage/emulated/0" || curr?.path == "/storage" || curr?.path == "/") {
                    break
                }
            }
            if (segments.size >= 2) {
                return segments
            } else if (segments.size == 1) {
                val parentName = file.parentFile?.name ?: (if (lang == "es") "Mis Documentos" else "My Documents")
                return listOf(parentName, segments[0])
            }
        } catch (e: Exception) {
            // fallback
        }

        val fallbackFolder = if (lang == "es") "Biblioteca General" else "General Library"
        return listOf(fallbackFolder, filePath.substringAfterLast("/"))
    }
}
