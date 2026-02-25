package org.ictye.attention_tool

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.ictye.attention_tool.utils.TodoManager
import org.ictye.attention_tool.utils.TodoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun isOverdue(dueDate: String?): Boolean {
    if (dueDate.isNullOrBlank()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dueDate)
        date != null && date.before(Date())
    } catch (e: Exception) {
        false
    }
}

@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreenPreview() {
    val previewTodos = remember {
        listOf(
            TodoItem(
                "Review project proposal",
                priority = 'A',
                creationDate = "2026-02-20",
                dueDate = "2026-02-28",
                tags = listOf("work", "urgent")
            ),
            TodoItem("Buy groceries", priority = 'C', tags = listOf("personal")),
            TodoItem(
                "Finish quarterly report",
                priority = 'B',
                isCompleted = false,
                creationDate = "2026-02-15",
                dueDate = "2026-02-25",
                tags = listOf("work")
            ),
            TodoItem("Call dentist", priority = 'D', isCompleted = true, tags = listOf("health")),
            TodoItem(
                "Prepare presentation slides",
                priority = 'A',
                creationDate = "2026-02-22",
                dueDate = "2026-03-01",
                tags = listOf("work", "important")
            )
        )
    }
    TodoScreen(previewTodos = previewTodos)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(modifier: Modifier = Modifier, previewTodos: List<TodoItem>? = null) {
    var todos by remember { mutableStateOf(previewTodos ?: TodoManager.loadTodos()) }
    var sortBy by remember { mutableStateOf("priority") }
    var ascending by remember { mutableStateOf(true) }

    var filterPriority by remember { mutableStateOf<Char?>(null) }
    var filterKeyword by remember { mutableStateOf("") }
    var filterFirstLetter by remember { mutableStateOf("") }
    var filterCompleted by remember { mutableStateOf<Boolean?>(null) }
    var filterTags by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var filterPriorityExpanded by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newTodoText by remember { mutableStateOf("") }
    var newTodoPriority by remember { mutableStateOf<Char?>(null) }
    var newTodoCreationDate by remember { mutableStateOf("") }
    var newTodoDueDate by remember { mutableStateOf("") }
    var newTodoTags by remember { mutableStateOf("") }

    var editingTodoIndex by remember { mutableStateOf<Int?>(null) }
    var editingTodoText by remember { mutableStateOf("") }
    var editingTodoPriority by remember { mutableStateOf<Char?>(null) }
    var editingTodoCreationDate by remember { mutableStateOf("") }
    var editingTodoDueDate by remember { mutableStateOf("") }
    var editingTodoTags by remember { mutableStateOf("") }

    val sortedTodos = remember(
        todos,
        sortBy,
        ascending,
        filterPriority,
        filterKeyword,
        filterFirstLetter,
        filterCompleted,
        filterTags
    ) {
        getSortedTodos(
            todos,
            sortBy,
            ascending,
            filterPriority,
            filterKeyword,
            filterFirstLetter,
            filterCompleted,
            filterTags
        )
    }

    val isFilterActive =
        filterPriority != null || filterKeyword.isNotBlank() || filterFirstLetter.isNotBlank() || filterCompleted != null || filterTags.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        TodoHeader()

        Spacer(modifier = Modifier.height(4.dp))

        TodoFilterBar(
            isFilterActive = isFilterActive,
            sortBy = sortBy,
            ascending = ascending,
            onFilterClick = { showFilterMenu = true },
            onSortChange = { sortBy = it },
            onAscendingChange = { ascending = !ascending }
        )

        if (isFilterActive) {
            ActiveFiltersRow(
                filterPriority = filterPriority,
                filterKeyword = filterKeyword,
                filterFirstLetter = filterFirstLetter,
                filterCompleted = filterCompleted,
                filterTags = filterTags,
                onClearPriority = { filterPriority = null },
                onClearKeyword = { filterKeyword = "" },
                onClearFirstLetter = { filterFirstLetter = "" },
                onClearCompleted = { filterCompleted = null },
                onClearTags = { filterTags = "" }
            )
        }

        if (showFilterMenu) {
            FilterDialog(
                filterPriority = filterPriority,
                filterKeyword = filterKeyword,
                filterFirstLetter = filterFirstLetter,
                filterCompleted = filterCompleted,
                filterTags = filterTags,
                filterPriorityExpanded = filterPriorityExpanded,
                onFilterPriorityChange = { filterPriority = it },
                onFilterKeywordChange = { filterKeyword = it },
                onFilterFirstLetterChange = { filterFirstLetter = it },
                onFilterCompletedChange = { filterCompleted = it },
                onFilterTagsChange = { filterTags = it },
                onFilterPriorityExpandedChange = { filterPriorityExpanded = it },
                onDismiss = { showFilterMenu = false },
                onClear = {
                    filterPriority = null
                    filterKeyword = ""
                    filterFirstLetter = ""
                    filterCompleted = null
                    filterTags = ""
                    showFilterMenu = false
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        TodoList(
            sortedTodos = sortedTodos,
            todos = todos,
            onAddClick = { showAddDialog = true },
            onToggleComplete = { index ->
                TodoManager.toggleComplete(index)
                todos = TodoManager.loadTodos()
            },
            onDelete = { index ->
                TodoManager.deleteTodo(index)
                todos = TodoManager.loadTodos()
            },
            onEdit = { index ->
                val item = todos.getOrNull(index)
                if (item != null) {
                    editingTodoIndex = index
                    editingTodoText = item.text
                    editingTodoPriority = item.priority
                    editingTodoCreationDate = item.creationDate ?: ""
                    editingTodoDueDate = item.dueDate ?: ""
                    editingTodoTags = item.tags.joinToString(", ")
                }
            }
        )
    }

    if (showAddDialog) {
        AddTodoDialog(
            text = newTodoText,
            priority = newTodoPriority,
            creationDate = newTodoCreationDate,
            dueDate = newTodoDueDate,
            tags = newTodoTags,
            onTextChange = { newTodoText = it },
            onPriorityChange = { newTodoPriority = it },
            onCreationDateChange = { newTodoCreationDate = it },
            onDueDateChange = { newTodoDueDate = it },
            onTagsChange = { newTodoTags = it },
            onConfirm = {
                if (newTodoText.isNotBlank()) {
                    val creationDate = newTodoCreationDate.takeIf { it.isNotBlank() }
                    val dueDate = newTodoDueDate.takeIf { it.isNotBlank() }
                    val tags = newTodoTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    TodoManager.addTodo(newTodoText, newTodoPriority, creationDate, dueDate, tags)
                    todos = TodoManager.loadTodos()
                    newTodoText = ""
                    newTodoPriority = null
                    newTodoCreationDate = ""
                    newTodoDueDate = ""
                    newTodoTags = ""
                    showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingTodoIndex != null) {
        EditTodoDialog(
            text = editingTodoText,
            priority = editingTodoPriority,
            creationDate = editingTodoCreationDate,
            dueDate = editingTodoDueDate,
            tags = editingTodoTags,
            onTextChange = { editingTodoText = it },
            onPriorityChange = { editingTodoPriority = it },
            onCreationDateChange = { editingTodoCreationDate = it },
            onDueDateChange = { editingTodoDueDate = it },
            onTagsChange = { editingTodoTags = it },
            onConfirm = {
                if (editingTodoText.isNotBlank() && editingTodoIndex != null) {
                    val creationDate = editingTodoCreationDate.takeIf { it.isNotBlank() }
                    val dueDate = editingTodoDueDate.takeIf { it.isNotBlank() }
                    val tags =
                        editingTodoTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    TodoManager.updateTodo(
                        editingTodoIndex!!,
                        editingTodoText,
                        editingTodoPriority,
                        creationDate,
                        dueDate,
                        tags
                    )
                    todos = TodoManager.loadTodos()
                    editingTodoIndex = null
                }
            },
            onDismiss = { editingTodoIndex = null }
        )
    }
}

private fun getSortedTodos(
    todos: List<TodoItem>,
    sortBy: String,
    ascending: Boolean,
    filterPriority: Char?,
    filterKeyword: String,
    filterFirstLetter: String,
    filterCompleted: Boolean?,
    filterTags: String
): List<Pair<Int, TodoItem>> {
    val withIndex = todos.mapIndexed { index, todo -> index to todo }
    val filtered = withIndex.filter { (_, todo) ->
        val matchesPriority = filterPriority == null || todo.priority == filterPriority
        val matchesKeyword = filterKeyword.isBlank() ||
                todo.text.contains(filterKeyword, ignoreCase = true) ||
                todo.tags.any { it.contains(filterKeyword, ignoreCase = true) } ||
                todo.projects.any { it.contains(filterKeyword, ignoreCase = true) }
        val matchesFirstLetter = filterFirstLetter.isBlank() ||
                todo.text.startsWith(filterFirstLetter, ignoreCase = true)
        val matchesCompleted = filterCompleted == null || todo.isCompleted == filterCompleted
        val matchesTags = filterTags.isBlank() ||
                todo.tags.any { it.contains(filterTags, ignoreCase = true) }
        matchesPriority && matchesKeyword && matchesFirstLetter && matchesCompleted && matchesTags
    }

    return when (sortBy) {
        "priority" -> {
            val uncompleted = filtered.filter { !it.second.isCompleted }.sortedWith(
                compareBy { (it.second.priority ?: '\u0000').let { p -> 'Z' - p + 1 } }
            )
            val completed = filtered.filter { it.second.isCompleted }
            if (ascending) uncompleted + completed else uncompleted.reversed() + completed
        }

        "alpha" -> {
            val uncompleted =
                filtered.filter { !it.second.isCompleted }.sortedBy { it.second.text.lowercase() }
            val completed = filtered.filter { it.second.isCompleted }
            if (ascending) uncompleted + completed else uncompleted.reversed() + completed
        }

        else -> {
            val uncompleted = filtered.filter { !it.second.isCompleted }
            val completed = filtered.filter { it.second.isCompleted }
            uncompleted + completed
        }
    }
}

@Composable
private fun TodoHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Todo List",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun TodoFilterBar(
    isFilterActive: Boolean,
    sortBy: String,
    ascending: Boolean,
    onFilterClick: () -> Unit,
    onSortChange: (String) -> Unit,
    onAscendingChange: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = isFilterActive,
            onClick = onFilterClick,
            label = {
                Icon(
                    painterResource(if (isFilterActive) R.drawable.ic_filter_off else R.drawable.ic_filter_on),
                    contentDescription = "Filter"
                )
                Text("Filter")
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAscendingChange) {
                Icon(
                    painterResource(if (ascending) R.drawable.ic_list_short_up else R.drawable.ic_list_short_down),
                    contentDescription = if (ascending) "Ascending" else "Descending"
                )
            }
            FilterChip(
                selected = sortBy == "priority",
                onClick = { onSortChange("priority") },
                label = { Text("Priority") }
            )
            Spacer(modifier = Modifier.size(10.dp))
            FilterChip(
                selected = sortBy == "alpha",
                onClick = { onSortChange("alpha") },
                label = { Text("A-Z") }
            )
        }
    }
}

@Composable
private fun ActiveFiltersRow(
    filterPriority: Char?,
    filterKeyword: String,
    filterFirstLetter: String,
    filterCompleted: Boolean?,
    filterTags: String,
    onClearPriority: () -> Unit,
    onClearKeyword: () -> Unit,
    onClearFirstLetter: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearTags: () -> Unit
) {
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filterPriority?.let {
            FilterChip(
                selected = true,
                onClick = onClearPriority,
                label = { Text("P:$it") }
            )
        }
        if (filterKeyword.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onClearKeyword,
                label = { Text("\"$filterKeyword\"") }
            )
        }
        if (filterFirstLetter.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onClearFirstLetter,
                label = { Text("^$filterFirstLetter") }
            )
        }
        filterCompleted?.let {
            FilterChip(
                selected = true,
                onClick = onClearCompleted,
                label = { Text(if (it) "Done" else "Pending") }
            )
        }
        if (filterTags.isNotBlank()) {
            FilterChip(
                selected = true,
                onClick = onClearTags,
                label = { Text("#$filterTags") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    filterPriority: Char?,
    filterKeyword: String,
    filterFirstLetter: String,
    filterCompleted: Boolean?,
    filterTags: String,
    filterPriorityExpanded: Boolean,
    onFilterPriorityChange: (Char?) -> Unit,
    onFilterKeywordChange: (String) -> Unit,
    onFilterFirstLetterChange: (String) -> Unit,
    onFilterCompletedChange: (Boolean?) -> Unit,
    onFilterTagsChange: (String) -> Unit,
    onFilterPriorityExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Todos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = filterPriorityExpanded,
                    onExpandedChange = onFilterPriorityExpandedChange
                ) {
                    OutlinedTextField(
                        value = filterPriority?.toString() ?: "All",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterPriorityExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = filterPriorityExpanded,
                        onDismissRequest = { onFilterPriorityExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All") },
                            onClick = {
                                onFilterPriorityChange(null)
                                onFilterPriorityExpandedChange(false)
                            }
                        )
                        listOf("A", "B", "C", "D", "E").forEach { priority ->
                            DropdownMenuItem(
                                text = { Text(priority) },
                                onClick = {
                                    onFilterPriorityChange(priority.first())
                                    onFilterPriorityExpandedChange(false)
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = filterKeyword,
                    onValueChange = onFilterKeywordChange,
                    label = { Text("Keyword") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = filterFirstLetter,
                    onValueChange = { onFilterFirstLetterChange(it.take(1)) },
                    label = { Text("First Letter") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = filterTags,
                    onValueChange = onFilterTagsChange,
                    label = { Text("Tag") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = filterCompleted ?: false,
                        onCheckedChange = { onFilterCompletedChange(if (it) true else null) }
                    )
                    Text("Show completed only")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = filterCompleted == false,
                        onCheckedChange = { onFilterCompletedChange(if (it) false else null) }
                    )
                    Text("Show pending only")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoList(
    sortedTodos: List<Pair<Int, TodoItem>>,
    todos: List<TodoItem>,
    onAddClick: () -> Unit,
    onToggleComplete: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onEdit: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(sortedTodos) { _, pair ->
                val (originalIndex, todo) = pair
                TodoListItem(
                    todo = todo,
                    onToggleComplete = { onToggleComplete(originalIndex) },
                    onDelete = { onDelete(originalIndex) },
                    onEdit = { onEdit(originalIndex) }
                )
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add todo"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListItem(
    todo: TodoItem,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    val isOverdue = !todo.isCompleted && isOverdue(todo.dueDate)

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            ListItem(
                modifier = Modifier
                    .clickable { onEdit() }
                    .padding(0.dp)
                    .height(50.dp),
                headlineContent = {
                    Text(
                        text = todo.text,
                        color = when {
                            todo.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                            isOverdue -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        style = if (todo.isCompleted)
                            MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = TextDecoration.LineThrough
                            )
                        else MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    if (todo.priority != null || todo.projects.isNotEmpty() || todo.creationDate != null || todo.dueDate != null || todo.tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            todo.priority?.let {
                                Text(
                                    text = "($it)",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            todo.projects.forEach { project ->
                                Text(
                                    text = "+$project",
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            todo.creationDate?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            todo.dueDate?.let {
                                Text(
                                    text = "Due: $it",
                                    color = if (isOverdue)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            todo.tags.forEach { tag ->
                                Text(
                                    text = "#$tag",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                },
                leadingContent = {
                    Checkbox(
                        checked = todo.isCompleted,
                        onCheckedChange = { onToggleComplete() }
                    )
                },
                trailingContent = {
                    IconButton(
                        onClick = onDelete,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTodoDialog(
    text: String,
    priority: Char?,
    creationDate: String,
    dueDate: String,
    tags: String,
    onTextChange: (String) -> Unit,
    onPriorityChange: (Char?) -> Unit,
    onCreationDateChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreationDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    if (showCreationDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showCreationDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        onCreationDateChange(sdf.format(Date(millis)))
                    }
                    showCreationDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreationDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDueDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        onDueDateChange(sdf.format(Date(millis)))
                    }
                    showDueDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Task") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = priority?.toString() ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                onPriorityChange(null)
                                expanded = false
                            }
                        )
                        listOf("A", "B", "C", "D", "E").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    onPriorityChange(p.first())
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = creationDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Creation Date") },
                    placeholder = { Text("Leave empty for today") },
                    trailingIcon = {
                        IconButton(onClick = { showCreationDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreationDatePicker = true }
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Due Date") },
                    placeholder = { Text("Optional") },
                    trailingIcon = {
                        IconButton(onClick = { showDueDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDueDatePicker = true }
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = onTagsChange,
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. work, urgent") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTodoDialog(
    text: String,
    priority: Char?,
    creationDate: String,
    dueDate: String,
    tags: String,
    onTextChange: (String) -> Unit,
    onPriorityChange: (Char?) -> Unit,
    onCreationDateChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreationDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    if (showCreationDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showCreationDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        onCreationDateChange(sdf.format(Date(millis)))
                    }
                    showCreationDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreationDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDueDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        onDueDateChange(sdf.format(Date(millis)))
                    }
                    showDueDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Task") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = priority?.toString() ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                onPriorityChange(null)
                                expanded = false
                            }
                        )
                        listOf("A", "B", "C", "D", "E").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    onPriorityChange(p.first())
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = creationDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Creation Date") },
                    trailingIcon = {
                        IconButton(onClick = { showCreationDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreationDatePicker = true }
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Due Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDueDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDueDatePicker = true }
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = onTagsChange,
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
