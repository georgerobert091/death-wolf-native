package com.deathwolf.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable Death Wolf chat bubble with selectable text and a copy button.
 *
 * WolfMessage is declared in MainActivity.kt in this project.
 */
@Composable
fun WolfMessageBubble(
    message: WolfMessage,
    maxWidth: Dp,
    userBubbleColor: Color,
    assistantBubbleColor: Color,
    messageTextColor: Color,
    actionTint: Color
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser) userBubbleColor else assistantBubbleColor,
            modifier = Modifier.widthIn(max = maxWidth),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    color = messageTextColor,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.text)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy message",
                    tint = actionTint,
                    modifier = Modifier.size(17.dp)
                )
            }

            Text(
                text = "Long-press to select text",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}
