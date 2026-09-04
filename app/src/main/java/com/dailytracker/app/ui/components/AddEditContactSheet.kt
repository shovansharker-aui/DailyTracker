package com.dailytracker.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.dailytracker.app.data.FrequencyPeriod
import com.dailytracker.app.data.TrackedContact

val RelationshipOptions = listOf(
    "Mother", "Father", "Grandparent", "Sibling", "Child", "Relative", "Best Friend", "Partner", "Other"
)

val AvatarColors = listOf(
    "#D9534F", "#E57373", "#FF8A65", "#FFB74D", "#81C784", "#4DB6AC", "#64B5F6", "#BA68C8"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditContactSheet(
    contactToEdit: TrackedContact?,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        name: String,
        phone: String,
        relationship: String,
        targetCount: Int,
        frequencyPeriod: String,
        notes: String,
        colorHex: String,
        pinned: Boolean,
        photoUri: String,
        priorityWeight: Int
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var name by remember { mutableStateOf(contactToEdit?.name ?: "") }
    var phoneNumbers by remember {
        mutableStateOf(
            contactToEdit?.getPhoneNumbersList()?.takeIf { it.isNotEmpty() } ?: listOf("")
        )
    }
    var targetCount by remember { mutableIntStateOf(contactToEdit?.targetCount ?: 2) }
    var frequencyPeriod by remember { mutableStateOf(contactToEdit?.frequencyPeriod ?: "WEEK") }
    var avatarColorHex by remember { mutableStateOf(contactToEdit?.avatarColorHex ?: AvatarColors.first()) }
    var pinned by remember { mutableStateOf(contactToEdit?.pinned ?: false) }
    var photoUri by remember { mutableStateOf(contactToEdit?.photoUri ?: "") }
    var priorityWeight by remember { androidx.compose.runtime.mutableFloatStateOf(contactToEdit?.priorityWeight?.toFloat() ?: 3f) }

    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    // System Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                try {
                    var pickedName: String? = null
                    var contactId: String? = null
                    var lookupKey: String? = null

                    val cursor = context.contentResolver.query(contactUri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            if (nameIdx >= 0) pickedName = c.getString(nameIdx)

                            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                            if (idIdx >= 0) contactId = c.getString(idIdx)

                            val lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                            if (lookupIdx >= 0) lookupKey = c.getString(lookupIdx)

                            val photoIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                            if (photoIdx >= 0) {
                                val pUri = c.getString(photoIdx)
                                if (!pUri.isNullOrBlank()) {
                                    photoUri = pUri
                                }
                            }
                        }
                    }

                    if (pickedName.isNullOrBlank()) {
                        val pCursor = context.contentResolver.query(
                            contactUri,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                            null, null, null
                        )
                        pCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val nameIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                                if (nameIdx >= 0) pickedName = pc.getString(nameIdx)
                            }
                        }
                    }

                    if (!pickedName.isNullOrBlank()) {
                        name = pickedName!!
                        nameError = false
                    }

                    val fetchedNumbers = mutableListOf<String>()
                    val selectionParts = mutableListOf<String>()
                    val selectionArgs = mutableListOf<String>()

                    if (!contactId.isNullOrBlank()) {
                        selectionParts.add("${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?")
                        selectionArgs.add(contactId!!)
                    }
                    if (!lookupKey.isNullOrBlank()) {
                        selectionParts.add("${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?")
                        selectionArgs.add(lookupKey!!)
                    }

                    if (selectionParts.isNotEmpty()) {
                        val phoneCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            selectionParts.joinToString(" OR "),
                            selectionArgs.toTypedArray(),
                            null
                        )
                        phoneCursor?.use { pc ->
                            val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (pc.moveToNext()) {
                                if (numIdx >= 0) {
                                    val num = pc.getString(numIdx)
                                    if (!num.isNullOrBlank()) {
                                        val cleanNum = num.trim()
                                        if (!fetchedNumbers.contains(cleanNum)) {
                                            fetchedNumbers.add(cleanNum)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (fetchedNumbers.isEmpty()) {
                        val directCursor = context.contentResolver.query(
                            contactUri,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            null, null, null
                        )
                        directCursor?.use { dc ->
                            val numIdx = dc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (dc.moveToNext()) {
                                if (numIdx >= 0) {
                                    val num = dc.getString(numIdx)
                                    if (!num.isNullOrBlank()) {
                                        val cleanNum = num.trim()
                                        if (!fetchedNumbers.contains(cleanNum)) {
                                            fetchedNumbers.add(cleanNum)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (fetchedNumbers.isNotEmpty()) {
                        phoneNumbers = fetchedNumbers
                        phoneError = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AddEditContactSheet", "Failed to fetch contact phone numbers", e)
                }
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        contactPickerLauncher.launch(pickIntent)
    }

    val triggerSave = {
        val validPhones = phoneNumbers.map { it.trim() }.filter { it.isNotBlank() }
        if (name.isBlank()) {
            nameError = true
        } else if (validPhones.isEmpty()) {
            phoneError = true
        } else {
            val joinedPhones = validPhones.joinToString(", ")
            onSave(
                contactToEdit?.id ?: 0L,
                name.trim(),
                joinedPhones,
                "",
                targetCount,
                frequencyPeriod,
                "",
                avatarColorHex,
                pinned,
                photoUri,
                priorityWeight.toInt().coerceIn(1, 5)
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val isImeVisible = WindowInsets.isImeVisible

        BackHandler(enabled = isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title & Confirmation Button on Top Right Corner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (contactToEdit == null) "Add New Contact" else "Edit Contact Goal",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                // Confirmation Button on top right corner
                Button(
                    onClick = { triggerSave() },
                    modifier = Modifier.testTag("save_contact_button_top_right"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Confirm",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (contactToEdit == null) "Save" else "Update", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Pick from system contacts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Import details:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                            contactPickerLauncher.launch(pickIntent)
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    modifier = Modifier.testTag("pick_system_contact_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContactPhone,
                        contentDescription = "Pick Contact",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Contact")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Selection Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    if (photoUri.isNotBlank()) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Contact Photo",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Default Avatar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    // Add / Edit Photo Badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = "Add Photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (photoUri.isBlank()) "Tap to add contact photo" else "Tap to change photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name Field (removed "e.g. mom, sarah")
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) nameError = false
                },
                label = { Text("Contact Name") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                isError = nameError,
                supportingText = if (nameError) { { Text("Name is required") } } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_contact_name")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Multiple Phone Numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phone Number(s)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                OutlinedButton(
                    onClick = { phoneNumbers = phoneNumbers + "" },
                    modifier = Modifier.testTag("add_phone_number_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add number", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Number", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            phoneNumbers.forEachIndexed { index, number ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { updated ->
                            val list = phoneNumbers.toMutableList()
                            list[index] = updated
                            phoneNumbers = list
                            if (updated.isNotBlank()) phoneError = false
                        },
                        label = { Text("Phone Number ${if (phoneNumbers.size > 1) "#${index + 1}" else ""}") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError && phoneNumbers.all { it.isBlank() },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_contact_phone_$index")
                    )

                    if (phoneNumbers.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val list = phoneNumbers.toMutableList()
                                list.removeAt(index)
                                phoneNumbers = list
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Remove number",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (phoneError && phoneNumbers.all { it.isBlank() }) {
                Text(
                    text = "At least one phone number is required",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Relationship Closeness / Priority Weight
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How close are you?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Closeness rating: ${priorityWeight.toInt()} / 5 (${
                            when (priorityWeight.toInt()) {
                                1 -> "Acquaintance"
                                2 -> "Casual Friend"
                                3 -> "Close Kin"
                                4 -> "Very Close"
                                else -> "Inner Circle"
                            }
                        })",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.Slider(
                        value = priorityWeight,
                        onValueChange = { priorityWeight = it },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("priority_weight_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Call Goal Picker
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Call Goal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Target Count:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (targetCount > 1) targetCount-- },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_target_decrement")
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "$targetCount times",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            IconButton(
                                onClick = { if (targetCount < 20) targetCount++ },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_target_increment")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Frequency Period:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val periods = listOf(
                            FrequencyPeriod.DAY to "Per Day",
                            FrequencyPeriod.WEEK to "Per Week",
                            FrequencyPeriod.MONTH to "Per Month"
                        )
                        periods.forEachIndexed { index, (pEnum, label) ->
                            SegmentedButton(
                                selected = (frequencyPeriod == pEnum.name),
                                onClick = { frequencyPeriod = pEnum.name },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = periods.size
                                )
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Save Button
            Button(
                onClick = { triggerSave() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_contact_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (contactToEdit == null) "Add to KinKeep" else "Update Goal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
