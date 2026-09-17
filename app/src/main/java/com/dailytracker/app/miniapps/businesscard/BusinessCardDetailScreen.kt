package com.dailytracker.app.miniapps.businesscard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BusinessCardDetailScreen(
    viewModel: BusinessCardViewModel,
    cardId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var card by remember { mutableStateOf<BusinessCardEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        card = viewModel.getCard(cardId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Card Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { paddingValues ->
        val current = card
        if (current != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (File(current.cardImagePath).exists()) {
                    AsyncImage(
                        model = File(current.cardImagePath),
                        contentDescription = "Card photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                DetailField("Business name", current.businessName)
                DetailField("Person's name", current.personName)
                DetailField("Phone number", current.phoneNumber)
                DetailField("Address", current.address)

                if (current.supportedBrands.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Supported brands", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.supportedBrands.forEach { brand ->
                            BrandChip(brand)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val toDelete = card
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this card?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete?.let { viewModel.deleteCard(it) }
                    showDeleteConfirm = false
                    onDeleted()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
