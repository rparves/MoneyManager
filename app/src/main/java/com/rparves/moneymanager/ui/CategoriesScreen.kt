package com.rparves.moneymanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.Category
import com.rparves.moneymanager.data.Seed
import com.rparves.moneymanager.ui.theme.LocalExtra
import kotlinx.coroutines.launch

private data class CatEdit(val existing: Category?, val parent: Category?, val isIncome: Boolean)

private val colorNames = listOf("Green", "Olive", "Amber", "Orange", "Slate blue", "Purple", "Blue", "Red", "Teal", "Plum", "Brown", "Gray")

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val dao = LocalContext.current.app().dao
    var incomeTab by rememberSaveable { mutableStateOf(false) }
    val cats by remember { dao.categories() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<CatEdit?>(null) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    val tops = cats.filter { it.isIncome == incomeTab && it.parentId == null }

    Column(Modifier.fillMaxSize()) {
        TopRow("Categories", onBack) {
            TextButton(onClick = { editing = CatEdit(null, null, incomeTab) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Category", fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Segmented(listOf("Expense", "Income"), if (incomeTab) 1 else 0, { incomeTab = it == 1 })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tops, key = { it.id }) { c ->
                val subs = cats.filter { it.parentId == c.id }
                val open = c.id in expanded
                Panel {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { expanded = if (open) expanded - c.id else expanded + c.id }
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryBadge(c.name, c.color)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                when (subs.size) { 0 -> "No subcategories"; 1 -> "1 subcategory"; else -> "${subs.size} subcategories" },
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = CatEdit(c, null, c.isIncome) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${c.name}")
                        }
                        Icon(
                            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (open) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (open) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        subs.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = CatEdit(s, c, c.isIncome) }
                                    .padding(start = 66.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(s.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${s.name}", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { editing = CatEdit(null, c, c.isIncome) }, modifier = Modifier.padding(start = 54.dp, bottom = 4.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add subcategory", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    editing?.let { e -> CategoryDialog(e, cats) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(e: CatEdit, all: List<Category>, onDismiss: () -> Unit) {
    val dao = LocalContext.current.app().dao
    val scope = rememberCoroutineScope()
    val ex = LocalExtra.current
    val existing = e.existing
    val parent = e.parent
    val isSub = parent != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: parent?.color ?: Seed.palette.first()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val siblings = all.filter { it.isIncome == e.isIncome && it.parentId == parent?.id && it.id != existing?.id }
    val dup = name.isNotBlank() && siblings.any { it.name.equals(name.trim(), ignoreCase = true) }
    val tops = all.filter { it.isIncome == e.isIncome && it.parentId == null }
    val fallback = if (existing != null && !isSub) {
        tops.filter { it.id != existing.id }.let { others -> others.firstOrNull { it.name.equals("Other", true) } ?: others.firstOrNull() }
    } else null
    val canDelete = existing != null && (isSub || fallback != null)
    val title = when {
        existing == null && parent != null -> "New subcategory in ${parent.name}"
        existing == null -> if (e.isIncome) "New income category" else "New expense category"
        isSub -> "Edit subcategory"
        else -> "Edit category"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (confirmDelete) {
                    Text(
                        if (isSub) "Delete “${existing?.name}”? Its transactions stay under ${parent?.name}."
                        else "Delete “${existing?.name}” and its subcategories? Its transactions move to “${fallback?.name}”.",
                    )
                } else {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name") },
                        singleLine = true, isError = dup, modifier = Modifier.fillMaxWidth(),
                    )
                    if (dup) Text("That name is already used here.", color = ex.expense, fontSize = 12.sp)
                    if (!isSub) {
                        Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Seed.palette.forEachIndexed { i, p ->
                                val on = p == color
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(Color(p))
                                        .border(3.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                        .selectable(selected = on, role = Role.RadioButton, onClick = { color = p })
                                        .semantics { contentDescription = colorNames.getOrElse(i) { "Color" } },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = {
                    if (existing != null) scope.launch { dao.deleteCategory(existing, fallback?.id) }
                    onDismiss()
                }) { Text("Delete", color = ex.expense, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(enabled = name.isNotBlank() && !dup, onClick = {
                    val n = name.trim()
                    scope.launch {
                        if (existing != null) {
                            dao.updateCategory(existing.copy(name = n, color = color))
                            if (!isSub && existing.color != color) dao.recolorChildren(existing.id, color)
                        } else {
                            dao.insertCategory(Category(name = n, isIncome = e.isIncome, color = color, parentId = parent?.id))
                        }
                    }
                    onDismiss()
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            Row {
                if (canDelete && !confirmDelete) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = ex.expense) }
                TextButton(onClick = { if (confirmDelete) confirmDelete = false else onDismiss() }) { Text("Cancel") }
            }
        },
    )
}
