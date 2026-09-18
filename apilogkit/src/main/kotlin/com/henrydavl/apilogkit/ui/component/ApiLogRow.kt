package com.henrydavl.apilogkit.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogState
import com.henrydavl.apilogkit.model.LogEventType
import com.henrydavl.apilogkit.ui.theme.ApiLogColors
import com.henrydavl.apilogkit.util.apiLogFormatted

/** A single log entry in the list. Compose port of the iOS `ApiLogRowView`. */
@Composable
fun ApiLogRow(log: ApiLog, logType: LogEventType, modifier: Modifier = Modifier) {
    val endpoint = log.url.substringAfterLast('/').ifEmpty { log.url }
    val responseTimeText = "%.2f s".format(log.responseTime.toDoubleOrNull() ?: 0.0)
    val isHttp = logType == LogEventType.API
    val isPending = log.state == ApiLogState.PENDING

    // EventTracker rows have no status or timing, but a restored one still needs
    // its marker — so the badge row also appears for those.
    val showsBadgeRow = isHttp || log.fromPreviousSession

    Card(
        modifier = modifier
            .fillMaxWidth()
            // Dim the whole row while in flight, so completed entries are what
            // the eye lands on when scanning.
            .alpha(if (isPending) 0.55f else 1f),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            1.dp,
            if (isPending) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (showsBadgeRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isHttp) {
                        if (isPending) {
                            PendingBadge()
                        } else {
                            StatusBadge(log.responseCode)
                        }
                    }

                    // Only ever true when the host opted into disk persistence;
                    // marks an entry restored from an earlier run of the app.
                    if (log.fromPreviousSession) {
                        RestoredBadge()
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isHttp) {
                        // An in-flight request has no duration yet — showing
                        // "0.00 s" would read as an impossibly fast response.
                        Text(
                            text = if (isPending) "—" else responseTimeText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Text(
                text = endpoint,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (showsBadgeRow) 6.dp else 0.dp),
            )
            Text(
                text = log.url,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = log.date.apiLogFormatted(),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Common shape for every badge in the status row.
 *
 * The height is fixed rather than left to each badge's own content, so the
 * status code, the pending badge and the restored marker are flush by
 * construction. Padding alone does not achieve that: a `Text` badge's height
 * comes from font metrics and an `Icon` badge's from its glyph box, and the two
 * differ by several dp — visibly, once they sit side by side.
 */
@Composable
private fun Badge(
    background: Color,
    horizontalPadding: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(BADGE_HEIGHT)
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun StatusBadge(responseCode: String) {
    Badge(background = ApiLogColors.statusColor(responseCode), horizontalPadding = 8.dp) {
        Text(
            text = responseCode,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Marks an entry that came from disk rather than this session. An icon beside
 * the status badge rather than a text badge: the full-width session-boundary
 * divider iOS tried first was far too heavy, and the per-row marker reads at a
 * glance without stealing a line.
 */
@Composable
private fun RestoredBadge() {
    Badge(background = MaterialTheme.colorScheme.outlineVariant, horizontalPadding = 6.dp) {
        Icon(
            Icons.Outlined.Inventory2,
            contentDescription = "From a previous session",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Stands in for the status badge until a real code arrives. */
@Composable
private fun PendingBadge() {
    Badge(background = ApiLogColors.PendingBadgeBackground, horizontalPadding = 8.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PendingSpinner(color = Color.White, size = 10.dp)
            Text(
                text = "Pending",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Measured against a 13.sp status code, which is the tallest badge content. */
private val BADGE_HEIGHT = 24.dp
