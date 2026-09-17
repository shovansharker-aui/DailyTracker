package com.dailytracker.app.miniapps.businesscard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessCardReviewScreen(
    viewModel: BusinessCardViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var showAddBrandDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (draft.editingCardId != null) "Edit Card" else "Review Card") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (draft.imagePath.isNotBlank() && File(draft.imagePath).exists()) {
                AsyncImage(
                    model = File(draft.imagePath),
                    contentDescription = "Captured card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = draft.businessName,
                onValueChange = { v -> viewModel.updateDraft { it.copy(businessName = v) } },
                label = { Text("Business name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.personName,
                onValueChange = { v -> viewModel.updateDraft { it.copy(personName = v) } },
                label = { Text("Person's name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.phoneNumber,
                onValueChange = { v -> viewModel.updateDraft { it.copy(phoneNumber = v) } },
                label = { Text("Phone number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.address,
                onValueChange = { v -> viewModel.updateDraft { it.copy(address = v) } },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Supported brands", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showAddBrandDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (draft.supportedBrands.isEmpty()) {
                Text(
                    text = "None detected. Add brands manually if needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(draft.supportedBrands, key = { it.name + it.domain }) { brand ->
                        AssistChip(
                            onClick = {},
                            label = { Text(brand.name) },
                            leadingIcon = {
                                brand.logoUrl?.let { url ->
                                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.removeBrandFromDraft(brand) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove ${brand.name}")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveDraft()
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.businessName.isNotBlank() || draft.personName.isNotBlank()
            ) {
                Text("Save card")
            }
        }
    }

    if (showAddBrandDialog) {
        AddBrandDialog(
            onDismiss = { showAddBrandDialog = false },
            onAdd = { name, domain ->
                viewModel.addBrandToDraft(name, domain)
                showAddBrandDialog = false
            }
        )
    }
}

@Composable
private fun AddBrandDialog(onDismiss: () -> Unit, onAdd: (name: String, domain: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a brand") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Brand name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Website (optional, for the logo)") },
                    placeholder = { Text("e.g. samsung.com") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, domain) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
