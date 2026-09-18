package com.henrydavl.apilogkit.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrydavl.apilogkit.model.StatusClass

/**
 * Status / method / host chips above the list. Compose port of the iOS
 * `ApiLogListView.filterBar`.
 *
 * Each chip opens a multi-select menu; the facets combine with AND between them
 * and OR within them (see `ApiLogFilter`). Method and host only offer values
 * present in the current bucket, so a selection can never be unsatisfiable.
 */
@Composable
internal fun ApiLogFilterBar(viewModel: ApiLogListViewModel, modifier: Modifier = Modifier) {
    val methods = viewModel.availableMethods
    val hosts = viewModel.availableHosts

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.showsStatusFilter) {
                FilterMenuChip(
                    label = "Status",
                    selectedCount = viewModel.filter.statuses.size,
                    options = StatusClass.entries.map { it to it.title },
                    isSelected = { it in viewModel.filter.statuses },
                    onToggle = viewModel::toggleStatus,
                )
            }
            if (methods.isNotEmpty()) {
                FilterMenuChip(
                    label = "Method",
                    selectedCount = viewModel.filter.methods.size,
                    options = methods.map { it to it },
                    isSelected = { it.uppercase() in viewModel.filter.methods },
                    onToggle = viewModel::toggleMethod,
                )
            }
            if (hosts.isNotEmpty()) {
                FilterMenuChip(
                    label = "Host",
                    selectedCount = viewModel.filter.hosts.size,
                    options = hosts.map { it to it },
                    isSelected = { it.lowercase() in viewModel.filter.hosts },
                    onToggle = viewModel::toggleHost,
                )
            }
            if (viewModel.filter.isActive) {
                TextButton(onClick = viewModel::clearFilters) {
                    Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        HorizontalDivider()
    }
}

/**
 * One chip and the multi-select menu it anchors.
 *
 * The menu is anchored to a [Box] wrapping the chip rather than to the chip
 * itself: a `DropdownMenu` positions against its parent, and inside the
 * horizontally scrolling [Row] that parent has to be a zero-cost wrapper or the
 * menu lands at the row's origin instead of under the chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterMenuChip(
    label: String,
    selectedCount: Int,
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedCount > 0,
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (selectedCount > 0) "$label ($selectedCount)" else label,
                    fontSize = 13.sp,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                val selected = isSelected(value)
                DropdownMenuItem(
                    // The menu stays open: these are multi-select facets, and
                    // reopening it for every choice makes "4xx + 5xx" tedious.
                    onClick = { onToggle(value) },
                    text = { Text(title) },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * Shown when the facets exclude everything, so a blank list doesn't read as
 * "no traffic captured".
 */
@Composable
internal fun FilteredEmptyState(onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.FilterAltOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = "No logs match the current filter",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onClearFilters) {
            Text("Clear filters", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
