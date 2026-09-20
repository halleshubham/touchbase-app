package com.yourname.touchbase.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.touchbase.data.local.MessageTemplate
import com.yourname.touchbase.data.local.MessageTemplateDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val dao: MessageTemplateDao
) : ViewModel() {

    val templates: StateFlow<List<MessageTemplate>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(name: String, body: String, makeDefault: Boolean, existingId: Long = 0) {
        viewModelScope.launch {
            val id = dao.upsert(MessageTemplate(id = existingId, name = name, bodyText = body))
            if (makeDefault) dao.setAsDefault(if (existingId != 0L) existingId else id)
        }
    }

    fun delete(template: MessageTemplate) {
        viewModelScope.launch { dao.delete(template) }
    }

    fun setDefault(template: MessageTemplate) {
        viewModelScope.launch { dao.setAsDefault(template.id) }
    }

    /** Seeds one starter template on first run so the WhatsApp button always has something to send. */
    fun seedDefaultIfEmpty() {
        viewModelScope.launch {
            if (dao.getDefault() == null && templates.value.isEmpty()) {
                val id = dao.upsert(
                    MessageTemplate(
                        name = "Quick hello",
                        bodyText = "Hi {name}, hope you're doing well! Just checking in.",
                        isDefault = true
                    )
                )
                dao.setAsDefault(id)
            }
        }
    }
}
