package tr.turtle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

// class s
// val appDirectory: String = File(s::class.java.getProtectionDomain().codeSource.location.toURI()).path

fun main(vararg args: String) {
	if (args.size < 2) {
		println("Usage : rname <directory_path> undo | <regex_to_replace> [replacement_string = '']")
		return
	}
	
	val workingDirectory = File(args[0])
	if (!workingDirectory.isDirectory) {
		println("Invalid directory path: ${args[0]}")
		return
	}
	
	if (args[1] == "undo") {
		undo(workingDirectory)
		return
	}
	
	val regex = args[1].toRegex()
	val replacement = if (args.size > 2) args[2] else ""
	val files = workingDirectory.listFiles() ?: emptyArray()
	var count = 0
	val backups = mutableListOf<Pair<String, String>>()
	for (file in files) {
		val baseName = file.nameWithoutExtension
		val extension = file.extension
		if (baseName.contains(regex)) {
			val newName = baseName.replace(regex, replacement)
			if (file.name != newName) {
				val success = rename(workingDirectory, file.name, "$newName.$extension")
				if (success) {
					++count
					println("File renamed from '$baseName' to '$newName'")
					backups.add(file.name to "${newName}.$extension")
				}
			}
		}
	}
	val backupName = workingDirectory.absolutePath.replace("[^a-zA-Z0-9_]".toRegex(), "")
	Json.encodeToString(Backup(backupName, backups)).also {json ->
		File(getTempDirectory(), "${backupName}.json")
				.also {it.createNewFile()}.writeText(json)
	}
	println("$count files are processed by `${args[1]}`")
	println("to undo : rname <directory_path> undo")
}

private fun undo(directory: File) {
	val backupFile = File(getTempDirectory(), "${directory.absolutePath.replace("[^a-zA-Z0-9_]".toRegex(), "")}.json")
	if (backupFile.exists()) {
		val backup = Json.decodeFromString<Backup>(backupFile.readText())
		var count = 0
		for (pair in backup.pairs) {
			val success = rename(directory, pair.second, pair.first)
			if (success) {
				++count
				println("File renamed from '${pair.second}' to '${pair.first}'")
			}
		}
		
		println("Processed $count file")
		backupFile.delete()
		println("Backup deleted for `${backupFile.nameWithoutExtension}`")
	}
	else {
		println("Backup not found for `${backupFile.nameWithoutExtension}`")
	}
}

private fun rename(directory: File, oldName: String, newName: String): Boolean {
	val file = File(directory, oldName)
	if (file.exists()) {
		val newFile = File(directory, newName)
		if (newFile.exists()) {
			println("File already exists: $newName")
			return false
		}
		else {
			val source = file.toPath()
			runCatching {
				Files.move(source, source.resolveSibling(newName))
			}.onSuccess {return true}.onFailure {
				println("Error renaming file: ${it.message}")
				return false
			}
		}
	}
	println("File not exist : $oldName")
	return false
}

fun getTempDirectory() = File(System.getProperty("java.io.tmpdir"))

@Serializable
data class Backup(val directory: String, val pairs: List<Pair<String, String>>)