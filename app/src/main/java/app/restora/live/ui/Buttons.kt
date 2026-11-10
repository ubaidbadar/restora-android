package app.restora.live.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** One control shape and minimum touch height throughout native forms and page actions. */
@Composable fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = shape, content = content)
}

@Composable fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small, content = content)
}

@Composable fun TonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small, content = content)
}
