package com.zlight106.nvvocab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.data.QuizOption

@Composable
fun QuestionOptionDetails(
    options: List<QuizOption>,
    correctAnswers: Set<String>,
    modifier: Modifier = Modifier,
    selectedAnswers: Set<String>? = null,
    title: String = "选项详情",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            val isCorrect = option.id in correctAnswers
            val isSelected = selectedAnswers?.let { option.id in it } ?: false
            val status = when {
                isCorrect && isSelected -> "正确答案 / 你的答案"
                isCorrect -> "正确答案"
                isSelected -> "你的答案"
                else -> null
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = when {
                    isCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    isSelected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isCorrect) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (isCorrect) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            option.id,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(option.text, style = MaterialTheme.typography.bodyLarge)
                        status?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCorrect) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
