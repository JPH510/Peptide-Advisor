package com.peptideadvisor

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.peptideadvisor.core.AskAnswer
import com.peptideadvisor.core.AskEngine
import com.peptideadvisor.core.ClaudeClient
import com.peptideadvisor.core.CorpusStore
import com.peptideadvisor.core.SecretStore
import com.peptideadvisor.data.AppDatabase
import com.peptideadvisor.data.DoseEntity
import com.peptideadvisor.data.LabReportEntity
import com.peptideadvisor.data.VendorEntity
import com.peptideadvisor.data.VialEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeptideAdvisorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val database = AppDatabase.get(context)
    val secrets = SecretStore(context)
    val corpus = CorpusStore(context)
    val client = ClaudeClient(secrets)
    val askEngine = AskEngine(corpus, client)
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PeptideAdvisorApp).container

// ---------------------------------------------------------------- Ask

class AskViewModel(private val container: AppContainer) : ViewModel() {

    val corpusState = container.corpus.state

    private val _answers = MutableStateFlow<List<AskAnswer>>(emptyList())
    val answers = _answers.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking = _isThinking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch { container.corpus.load() }
    }

    fun ask(question: String) {
        if (question.isBlank() || _isThinking.value) return
        viewModelScope.launch {
            _isThinking.value = true
            _error.value = null
            container.askEngine.ask(question)
                .onSuccess { _answers.value = listOf(it) + _answers.value }
                .onFailure { _error.value = it.message ?: "Something went wrong." }
            _isThinking.value = false
        }
    }

    fun clearError() { _error.value = null }

    // Deliberately does not close the corpus embedder. CorpusStore is owned by
    // AppContainer and outlives this ViewModel — closing it on a rotation would
    // silently drop semantic rerank for the rest of the process.
}

// ---------------------------------------------------------------- Log

class LogViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.database

    val vials = db.vials().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentDoses = db.doses().observeRecent(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val compounds = db.compounds().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vendors = db.vendors().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun vial(id: Long) = db.vials().observeById(id)

    fun addVial(compoundName: String, peptideMg: Double, lotNumber: String, vendorId: Long?) {
        viewModelScope.launch {
            val compoundId = db.compounds().upsertByName(compoundName.trim())
            db.vials().insert(
                VialEntity(
                    compoundId = compoundId,
                    vendorId = vendorId,
                    peptideMg = peptideMg,
                    lotNumber = lotNumber,
                )
            )
        }
    }

    fun setMix(vialId: Long, diluentMl: Double) {
        viewModelScope.launch {
            db.vials().setMix(vialId, diluentMl, System.currentTimeMillis())
        }
    }

    fun logDose(vialId: Long, doseMcg: Double, units: Double, site: String, notes: String) {
        viewModelScope.launch {
            db.doses().insert(
                DoseEntity(
                    vialId = vialId,
                    doseMcg = doseMcg,
                    unitsDrawn = units,
                    site = site,
                    notes = notes,
                )
            )
        }
    }

    fun discardVial(vialId: Long) {
        viewModelScope.launch { db.vials().discard(vialId) }
    }
}

// ---------------------------------------------------------------- Vendors

class VendorViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.database

    val vendors = db.vendors().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun vendor(id: Long) = db.vendors().observeById(id)

    fun addVendor(name: String, website: String, notes: String) {
        viewModelScope.launch {
            db.vendors().insert(VendorEntity(name = name.trim(), website = website, notes = notes))
        }
    }

    fun addReport(report: LabReportEntity) {
        viewModelScope.launch { db.vendors().insertReport(report) }
    }
}

// ---------------------------------------------------------------- Settings

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val corpusState = container.corpus.state

    private val _hasKey = MutableStateFlow(container.secrets.has(SecretStore.API_KEY))
    val hasKey = _hasKey.asStateFlow()

    fun saveKey(key: String) {
        container.secrets.put(SecretStore.API_KEY, key.trim())
        _hasKey.value = container.secrets.has(SecretStore.API_KEY)
    }

    fun removeKey() {
        container.secrets.remove(SecretStore.API_KEY)
        _hasKey.value = false
    }
}

// ---------------------------------------------------------------- Factory

val AppViewModelFactory = viewModelFactory {
    initializer { AskViewModel(this.container()) }
    initializer { LogViewModel(this.container()) }
    initializer { VendorViewModel(this.container()) }
    initializer { SettingsViewModel(this.container()) }
}
