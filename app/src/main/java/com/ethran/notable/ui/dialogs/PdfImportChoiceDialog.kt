package com.ethran.notable.ui.dialogs

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.shipbook.shipbooksdk.Log
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R

@Composable
fun PdfImportChoiceDialog(
    uri: Uri,
    onCopy: (Uri) -> Unit,
    onObserve: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val pathLength = remember(uri) { uri.path?.length ?: 0 }
    Log.d("PdfImportChoiceDialog", "Path Length: $pathLength")
    val pathTooLong = pathLength > 100

    ShowConfirmationDialog(
        title = stringResource(R.string.pdf_import_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.pdf_import_question),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // --- Observe Option ---
                Text(
                    text = stringResource(R.string.pdf_import_observe_label),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.pdf_import_observe_text),
                    fontSize = 14.sp
                )
                // Add a warning if the file path is too long
                if (pathTooLong) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pdf_import_path_warning, pathLength),
                        color = MaterialTheme.colors.error, // Use theme's error color for emphasis
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Copy Option ---
                Text(
                    text = stringResource(R.string.pdf_import_copy_label),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.pdf_import_copy_text),
                    fontSize = 14.sp
                )
            }
        },
        onConfirm = { onCopy(uri) },
        onCancel = { onObserve(uri) },
        onDismiss = { onDismiss() },
        confirmButtonText = stringResource(R.string.pdf_import_copy),
        cancelButtonText = stringResource(R.string.pdf_import_observe)
    )
}