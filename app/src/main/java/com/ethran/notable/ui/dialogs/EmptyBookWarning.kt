package com.ethran.notable.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R
import com.ethran.notable.data.db.Notebook

@Composable
fun EmptyBookWarningHandler(
    emptyBook: Notebook, onDelete: (String) -> Unit, onDismiss: () -> Unit
) {
    val title = emptyBook.title
    val createdAt = emptyBook.createdAt
    ShowSimpleConfirmationDialog(
        title = stringResource(R.string.empty_book_title),
        message = stringResource(R.string.empty_book_message, title, createdAt.toString()),
        onConfirm = {
            onDelete(emptyBook.id)
        },
        onCancel = { onDismiss() })
}