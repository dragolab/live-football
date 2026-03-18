package dragolabs.livefootball.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dragolabs.livefootball.model.Category
import dragolabs.livefootball.model.Channel
import dragolabs.livefootball.repository.SportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SportViewModel : ViewModel() {
    private val repository = SportRepository()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            _categories.value = repository.getCategories()
            _isLoading.value = false
        }
    }

    fun loadChannels(categoryId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _channels.value = repository.getChannels(categoryId)
            _isLoading.value = false
        }
    }

    suspend fun refreshChannelUrl(channel: Channel): String? {
        val cUrl = channel.cUrl ?: return null
        val newUrl = repository.getUpdatedUrl(cUrl)
        if (newUrl != null) {
            channel.channelUrl = newUrl
        }
        return newUrl
    }
}
