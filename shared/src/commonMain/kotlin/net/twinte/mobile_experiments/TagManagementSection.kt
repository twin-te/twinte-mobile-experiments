package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.Tag

@Composable
internal fun TagManagementSection(
    tags: List<Tag>,
    enabled: Boolean,
    onCreate: (String) -> Unit,
    onUpdate: (Tag, String) -> Unit,
    onDelete: (Tag) -> Unit,
    onMove: (Tag, Int) -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var deletingTag by remember { mutableStateOf<Tag?>(null) }

    HorizontalDivider()
    Text("Tags", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = newTagName,
            onValueChange = { newTagName = it },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            label = { Text("New tag") },
            singleLine = true,
        )
        Button(
            enabled = enabled && newTagName.isNotBlank(),
            onClick = {
                onCreate(newTagName.trim())
                newTagName = ""
            },
        ) {
            Text("Add")
        }
    }

    tags.forEachIndexed { index, tag ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(tag.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled && index > 0,
                    onClick = { onMove(tag, -1) },
                ) {
                    Text("Up")
                }
                TextButton(
                    enabled = enabled && index < tags.lastIndex,
                    onClick = { onMove(tag, 1) },
                ) {
                    Text("Down")
                }
                TextButton(
                    enabled = enabled,
                    onClick = { editingTag = tag },
                ) {
                    Text("Rename")
                }
                TextButton(
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { deletingTag = tag },
                ) {
                    Text("Delete")
                }
            }
        }
    }

    editingTag?.let { tag ->
        RenameTagDialog(
            tag = tag,
            onConfirm = { name ->
                editingTag = null
                onUpdate(tag, name)
            },
            onDismiss = { editingTag = null },
        )
    }
    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("Delete tag?") },
            text = { Text(tag.name) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        deletingTag = null
                        onDelete(tag)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingTag = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun RenameTagDialog(
    tag: Tag,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(tag.id) { mutableStateOf(tag.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename tag") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
