package tr.turtle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

// class s
// val appDirectory: String = File(s::class.java.getProtectionDomain().codeSource.location.toURI()).path

/**
 * Renames files in a directory.
 *
 * Usage:
 * ```
 * rname <directory_path> undo | <regex_to_replace> [replacement_string = '']
 * ```
 *
 * - `rname`              : name of the program
 * - `directory_path`     : path of the directory that files will be renamed that exist into it
 * - `undo`               : undo the last operation in the entered `directory_path`
 * - `regex_to_replace`   : regex to match the file names
 * - `replacement_string` : string to replace with matched parts. Default is empty string.
 *
 * Example:
 * ```
 * rname test [0-9] _
 * ```
 *
 * - `test` : is the relative directory. It can be absolute or relative.
 * - `[0-9]` : regex to match the file names. It matches any digit.
 * - `_` : string to replace with. It can be omited to empty string. Empty string means removing the matched part.
 *
 * There is a file named `1t2e3s4t5.txt` in the `test` directory.
 * It will be renamed to `_t_e_s_t_.txt` according to above example.
 * All operations are saved in a json file in the system temp directory.
 * If you type
 *
 * ```
 * rname <directory_path> undo
 * ```
 * it will undo the last operation and deletes the backup file.
 * `Last operation` means the last usage of the `rname` program in the `directory_path`.
 * On the last usage maybe 100 files are renamed, `undo` will undo 100 files then.
 *
 * @param args command line arguments
 */
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
	
	if (args[1] == "undo") undo(workingDirectory)
	else process(workingDirectory, *args)
}

private fun process(workingDirectory: File, vararg args: String) {
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

private fun getTempDirectory() = File(System.getProperty("java.io.tmpdir"))

@Serializable
data class Backup(val directory: String, val pairs: List<Pair<String, String>>)