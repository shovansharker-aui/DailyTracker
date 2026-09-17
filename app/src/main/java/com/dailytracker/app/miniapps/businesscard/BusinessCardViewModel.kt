package com.dailytracker.app.miniapps.businesscard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** In-progress card being captured or edited - not yet (or being re-)saved. */
data class CardDraft(
    val businessName: String = "",
    val personName: String = "",
    val address: String = "",
    val phoneNumber: String = "",
    val supportedBrands: List<BrandInfo> = emptyList(),
    val imagePath: String = "",
    val editingCardId: Long? = null
)

class BusinessCardViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = BusinessCardDatabase.getDatabase(application).businessCardDao()

    // Plain SharedPreferences, matching how SuperAppViewModel stores its own
    // settings (theme, order) - fine for a single-user local device, but note
    // this key is stored in cleartext. If this needs hardening later, swap
    // in androidx.security:security-crypto's EncryptedSharedPreferences.
    private val prefs = application.getSharedPreferences("business_card_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<BusinessCardEntity>> = _searchQuery
        .flatMapLatest { query -> if (query.isBlank()) dao.getAllCards() else dao.searchCards(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draft = MutableStateFlow(CardDraft())
    val draft: StateFlow<CardDraft> = _draft.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractionError = MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> = _extractionError.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun startNewCapture() {
        _draft.value = CardDraft()
        _extractionError.value = null
    }

    /** Kicks off Gemini extraction for a freshly captured card photo. */
    fun onImageCaptured(imagePath: String) {
        _draft.value = _draft.value.copy(imagePath = imagePath)
        _extractionError.value = null
        _isExtracting.value = true
        viewModelScope.launch {
            val bytes = try {
                File(imagePath).readBytes()
            } catch (e: Exception) {
                _isExtracting.value = false
                _extractionError.value = "Couldn't read the captured photo."
                return@launch
            }

            when (val result = GeminiCardExtractor.extract(getApiKey(), bytes)) {
                is ExtractionResult.Success -> {
                    val data = result.data
                    _draft.value = _draft.value.copy(
                        businessName = data.businessName,
                        personName = data.personName,
                        address = data.address,
                        phoneNumber = data.phoneNumber,
                        supportedBrands = data.supportedBrands.filter { it.name.isNotBlank() }
                    )
                }
                is ExtractionResult.Failure -> {
                    _extractionError.value = result.message
                }
            }
            _isExtracting.value = false
        }
    }

    fun updateDraft(update: (CardDraft) -> CardDraft) {
        _draft.value = update(_draft.value)
    }

    fun addBrandToDraft(name: String, domain: String) {
        if (name.isBlank()) return
        _draft.value = _draft.value.copy(
            supportedBrands = _draft.value.supportedBrands +
                BrandInfo(name = name.trim(), domain = domain.trim().ifBlank { null })
        )
    }

    fun removeBrandFromDraft(brand: BrandInfo) {
        _draft.value = _draft.value.copy(
            supportedBrands = _draft.value.supportedBrands.filterNot { it == brand }
        )
    }

    fun saveDraft() {
        val d = _draft.value
        viewModelScope.launch {
            dao.insertCard(
                BusinessCardEntity(
                    id = d.editingCardId ?: 0,
                    businessName = d.businessName.trim(),
                    personName = d.personName.trim(),
                    address = d.address.trim(),
                    phoneNumber = d.phoneNumber.trim(),
                    supportedBrands = d.supportedBrands,
                    cardImagePath = d.imagePath
                )
            )
        }
    }

    fun loadCardIntoDraft(id: Long) {
        viewModelScope.launch {
            dao.getCardById(id)?.let { card ->
                _draft.value = CardDraft(
                    businessName = card.businessName,
                    personName = card.personName,
                    address = card.address,
                    phoneNumber = card.phoneNumber,
                    supportedBrands = card.supportedBrands,
                    imagePath = card.cardImagePath,
                    editingCardId = card.id
                )
            }
        }
    }

    suspend fun getCard(id: Long): BusinessCardEntity? = dao.getCardById(id)

    fun deleteCard(card: BusinessCardEntity) {
        viewModelScope.launch { dao.deleteCard(card) }
    }
}
