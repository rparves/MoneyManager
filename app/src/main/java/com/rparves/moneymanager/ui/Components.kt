package com.rparves.moneymanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rparves.moneymanager.data.TxnRow
import com.rparves.moneymanager.ui.theme.LocalExtra

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(content = content)
    }
}

@Composable
fun EmptyNote(text: String) {
    Panel {
        Text(text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun TopRow(
    title: String,
    onBack: (() -> Unit)?,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(backIcon, contentDescription = "Back") }
        else Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun categoryInk(color: Long?): Color {
    val c = Color(color ?: 0xFF6B7570)
    return if (isSystemInDarkTheme()) lerp(c, Color.White, 0.45f) else c
}

@Composable
fun CategoryBadge(name: String?, color: Long?, size: Dp = 40.dp) {
    val c = Color(color ?: 0xFF6B7570)
    val dark = isSystemInDarkTheme()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = if (dark) 0.30f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text((name ?: "?").take(1).uppercase(), color = categoryInk(color), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
fun TxnItem(row: TxnRow, cur: String, onClick: () -> Unit) {
    val ex = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(row.catName, row.catColor)
        Column(Modifier.weight(1f)) {
            Text(
                row.t.note.ifBlank { row.catName ?: "Uncategorized" },
                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(row.catName.takeIf { row.t.note.isNotBlank() }, row.accName).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
            signed(row.t.amount, row.t.isIncome, cur),
            color = if (row.t.isIncome) ex.income else ex.expense,
            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, activeColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    color = if (on) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, label: String, cur: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 12 && v.all { it.isDigit() || it == '.' || it == ',' }) onChange(v) },
        label = { Text(label) },
        prefix = { Text(cur) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}
