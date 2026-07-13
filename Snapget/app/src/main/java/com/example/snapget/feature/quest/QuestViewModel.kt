package com.example.snapget.feature.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.core.network.dto.TodayQuestDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.quest.data.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Trang thai man Daily Quest. */
data class QuestUiState(
    val status: LoadStatus = LoadStatus.Init(),
    val quests: List<TodayQuestDto> = emptyList(),
    /** Khung vua duoc thuong hom nay (de highlight); null = chua co. */
    val rewardFrameId: String? = null,
    val frames: List<FrameDto> = emptyList(),
    val personalStreak: Int = 0,
) {
    val completedCount: Int get() = quests.count { it.completed }
}

@HiltViewModel
class QuestViewModel @Inject constructor(
    private val repository: QuestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestUiState())
    val uiState: StateFlow<QuestUiState> = _uiState.asStateFlow()

    /** Tai quest hom nay + catalog khung + streak (3 call song song). */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = LoadStatus.Loading())
            try {
                coroutineScope {
                    val today = async { repository.getTodayQuests() }
                    val frames = async { repository.getFrames() }
                    val streak = async { repository.getMyStreak() }
                    _uiState.value = QuestUiState(
                        status = LoadStatus.Success(),
                        quests = today.await().quests,
                        rewardFrameId = today.await().rewardFrameId,
                        frames = frames.await(),
                        personalStreak = streak.await(),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = LoadStatus.Error(e.serverMessage("Khong tai duoc nhiem vu hom nay.")),
                )
            }
        }
    }
}
