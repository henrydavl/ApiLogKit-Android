package com.henrydavl.apilogkit.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Expand-all / collapse-all controls for a JSON tree. Kept in their own row
 * (separate from the tree) so the buttons don't shift when expanding resizes the
 * tree — matching the iOS `JSONTreeControls`.
 */
@Composable
fun JsonTreeControls(model: JsonTreeModel, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { model.expandAll() }) {
            Icon(Icons.Filled.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = " Expand all", fontSize = 12.sp)
        }
        OutlinedButton(onClick = { model.collapseAll() }) {
            Icon(Icons.Filled.IndeterminateCheckBox, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = " Collapse all", fontSize = 12.sp)
        }
    }
}
