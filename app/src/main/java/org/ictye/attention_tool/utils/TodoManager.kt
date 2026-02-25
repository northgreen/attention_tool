package org.ictye.attention_tool.utils

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.util.Calendar

data class TodoItem(
    val text: String,
    val priority: Char? = null,
    val isCompleted: Boolean = false,
    val completionDate: String? = null,
    val creationDate: String? = null,
    val dueDate: String? = null,
    val projects: List<String> = emptyList(),
    val contexts: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

@SuppressLint("StaticFieldLeak")
object TodoManager {
    private const val TODO_FILE = "todo.txt"
    private var todoFile: File? = null

    fun init(ctx: Context) {
        todoFile = File(ctx.filesDir, TODO_FILE)
        if (!todoFile!!.exists()) {
            todoFile!!.createNewFile()
        }
    }

    fun loadTodos(): List<TodoItem> {
        val file = todoFile ?: return emptyList()
        if (!file.exists()) return emptyList()

        return try {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { parseLine(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseLine(line: String): TodoItem? {
        var remaining = line.trim()
        var isCompleted = false
        var completionDate: String? = null
        var priority: Char? = null

        if (remaining.startsWith("x ")) {
            isCompleted = true
            remaining = remaining.substring(2)

            val dateMatch = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+").find(remaining)
            if (dateMatch != null) {
                completionDate = dateMatch.groupValues[1]
                remaining = remaining.substring(dateMatch.value.length)
            }
        }

        val priorityMatch = Regex("^\\(([A-Z])\\)\\s+").find(remaining)
        if (priorityMatch != null) {
            priority = priorityMatch.groupValues[1][0]
            remaining = remaining.substring(priorityMatch.value.length)
        }

        val dateMatch = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s+").find(remaining)
        var creationDate: String? = null
        if (dateMatch != null) {
            creationDate = dateMatch.groupValues[1]
            remaining = remaining.substring(dateMatch.value.length)
        }

        val dueDateMatch = Regex("due:(\\d{4}-\\d{2}-\\d{2})").find(remaining)
        val dueDate = dueDateMatch?.groupValues?.get(1)
        remaining = remaining.replace(Regex("due:\\d{4}-\\d{2}-\\d{2}"), "")

        val projects = Regex("\\+(\\S+)").findAll(remaining)
            .map { it.groupValues[1] }
            .toList()

        val contexts = Regex("@(\\S+)").findAll(remaining)
            .map { it.groupValues[1] }
            .toList()
        
        val tags = Regex("tag:(\\S+)").findAll(remaining)
            .map { it.groupValues[1] }
            .toList()
        remaining = remaining.replace(Regex("tag:\\S+"), "")

        if (remaining.isBlank()) return null

        return TodoItem(
            text = remaining.trim(),
            priority = priority,
            isCompleted = isCompleted,
            completionDate = completionDate,
            creationDate = creationDate,
            dueDate = dueDate,
            projects = projects,
            contexts = contexts,
            tags = tags
        )
    }

    fun saveTodos(todos: List<TodoItem>) {
        val file = todoFile ?: return
        val content = todos.joinToString("\n") { item ->
            buildString {
                if (item.isCompleted) {
                    append("x ")
                    item.completionDate?.let { append("$it ") }
                }
                item.priority?.let { append("($it) ") }
                item.creationDate?.let { append("$it ") }
                append(item.text)
                item.projects.forEach { append(" +$it") }
                item.contexts.forEach { append(" @$it") }
                item.dueDate?.let { append(" due:$it") }
                item.tags.forEach { append(" tag:$it") }
            }
        }
        file.writeText(content)
    }

    fun addTodo(text: String, priority: Char? = null, creationDate: String? = null, dueDate: String? = null, tags: List<String> = emptyList()) {
        val todos = loadTodos().toMutableList()
        todos.add(TodoItem(text = text, priority = priority, creationDate = creationDate ?: getCurrentDate(), dueDate = dueDate, tags = tags))
        saveTodos(todos)
    }

    fun toggleComplete(index: Int) {
        val todos = loadTodos().toMutableList()
        if (index in todos.indices) {
            val item = todos[index]
            todos[index] = item.copy(
                isCompleted = !item.isCompleted,
                completionDate = if (!item.isCompleted) getCurrentDate() else null
            )
            saveTodos(todos)
        }
    }

    fun deleteTodo(index: Int) {
        val todos = loadTodos().toMutableList()
        if (index in todos.indices) {
            todos.removeAt(index)
            saveTodos(todos)
        }
    }
    
    fun updateTodo(index: Int, text: String, priority: Char? = null, creationDate: String? = null, dueDate: String? = null, tags: List<String> = emptyList()) {
        val todos = loadTodos().toMutableList()
        if (index in todos.indices) {
            todos[index] = todos[index].copy(
                text = text,
                priority = priority,
                creationDate = creationDate,
                dueDate = dueDate,
                tags = tags
            )
            saveTodos(todos)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getCurrentDate(): String {
        val now = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    fun getTodoFilePath(): String {
        return todoFile?.absolutePath ?: ""
    }
    
    fun getTodoFileContent(): String {
        return todoFile?.readText() ?: ""
    }
}
