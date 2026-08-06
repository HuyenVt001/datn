package com.example.snapget.feature.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.network.dto.GachaStateDto
import com.example.snapget.core.network.dto.RollOutcomeDto
import com.example.snapget.core.network.dto.TopupPackageDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.gacha.data.GachaRepository
import com.example.snapget.feature.gacha.data.TopupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Trang thai man Gacha. */
data class GachaUiState(
    val status: LoadStatus = LoadStatus.Init(),
    val state: GachaStateDto = GachaStateDto(),
    /**
     * So lan cua luot quay DANG chay (1/10), null = khong quay. Giu so lan chu
     * khong phai Boolean de man hinh chi hien spinner tren dung nut vua bam —
     * nut kia chi mo di.
     */
    val rollingTimes: Int? = null,
    /** Ket qua vua quay; khac null = dang hien man ket qua. */
    val outcome: RollOutcomeDto? = null,
    /** Loi cua rieng thao tac quay (khong thay the man bang man loi). */
    val rollError: String? = null,
    /** Trang thai luong nap Astrite (G6). */
    val topup: TopupUiState = TopupUiState(),
) {
    val isRolling: Boolean get() = rollingTimes != null

    fun canAfford(cost: Int): Boolean = state.astrite >= cost
}

/** Trang thai popup nap Astrite qua PayOS. */
data class TopupUiState(
    val isSheetOpen: Boolean = false,
    val isLoadingPackages: Boolean = false,
    val packages: List<TopupPackageDto> = emptyList(),
    /** packageId dang tao don — chi khoa dung nut cua goi do. */
    val creatingPackageId: String? = null,
    /**
     * Link thanh toan vua tao. Man hinh mo xong thi goi `consumeCheckoutUrl()`
     * — de trong state se mo lai moi lan recompose.
     */
    val checkoutUrl: String? = null,
    /** Don dang cho tra tien — khac null thi man hinh poll trang thai. */
    val pendingOrderCode: Long? = null,
    /** So Astrite vua duoc cong — hien popup chuc mung roi xoa. */
    val creditedAstrite: Int? = null,
    /** Thong bao ngan (toast) cua rieng luong nap. */
    val message: String? = null,
)

@HiltViewModel
class GachaViewModel @Inject constructor(
    private val repository: GachaRepository,
    private val topupRepository: TopupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GachaUiState())
    val uiState: StateFlow<GachaUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = LoadStatus.Loading())
            try {
                _uiState.value = _uiState.value.copy(
                    status = LoadStatus.Success(),
                    state = repository.getState(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = LoadStatus.Error(e.serverMessage("Couldn't load the gacha.")),
                )
            }
        }
    }

    /**
     * Quay [times] lan (1 hoac 10).
     *
     * Chan bam kep bang `isRolling`: server co transaction nen bam 2 lan khong
     * tieu qua so du, nhung se thanh **2 luot quay that** — nguoi dung mat tien
     * ma khong hieu vi sao.
     */
    fun roll(times: Int) {
        if (_uiState.value.isRolling) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(rollingTimes = times, rollError = null)
            try {
                val outcome = repository.roll(times)
                // So du + pity doi sau moi lan quay -> doc lai tu server thay vi
                // tu tru o app (pity chi server biet).
                //
                // ⚠️ `runCatching`: lan quay DA tinh tien va DA phat vat pham roi.
                // Neu de loi cua rieng buoc doc lai state nem ra ngoai thi ca cum
                // roi vao `catch` -> nguoi dung mat tien, mat luon man ket qua va
                // chi thay "Roll failed". Doc state that bai thi giu so du cu
                // (lan `load()` sau se dung lai) — con ket qua quay VAN phai hien.
                val state = runCatching { repository.getState() }.getOrNull()
                _uiState.value = _uiState.value.copy(
                    rollingTimes = null,
                    outcome = outcome,
                    state = state ?: _uiState.value.state,
                )
            } catch (e: Exception) {
                // Quay hong: co the server da tru tien roi moi dut mang. Doc lai
                // state de so du/pity tren man hinh khong bi lech voi thuc te.
                val state = runCatching { repository.getState() }.getOrNull()
                _uiState.value = _uiState.value.copy(
                    rollingTimes = null,
                    state = state ?: _uiState.value.state,
                    rollError = e.serverMessage("Roll failed. Please try again."),
                )
            }
        }
    }

    /** Dong man ket qua. */
    fun dismissOutcome() {
        _uiState.value = _uiState.value.copy(outcome = null)
    }

    fun dismissRollError() {
        _uiState.value = _uiState.value.copy(rollError = null)
    }

    // ==================== Nap Astrite (G6 — PayOS) ====================

    /** Mo popup goi nap va tai danh sach goi. */
    fun openTopup() {
        updateTopup { it.copy(isSheetOpen = true, isLoadingPackages = true) }
        viewModelScope.launch {
            try {
                val packages = topupRepository.listPackages()
                updateTopup { it.copy(isLoadingPackages = false, packages = packages) }
            } catch (e: Exception) {
                updateTopup {
                    it.copy(
                        isLoadingPackages = false,
                        message = e.serverMessage("Couldn't load top-up packages."),
                    )
                }
            }
        }
    }

    fun closeTopup() {
        updateTopup { it.copy(isSheetOpen = false) }
    }

    /**
     * Tao don nap va lay link thanh toan.
     *
     * App chi gui `packageId` — so tien do server tra tu goi. Chan bam kep bang
     * `creatingPackageId`: moi lan bam la mot don PayOS that duoc tao.
     */
    fun buyPackage(packageId: String) {
        if (_uiState.value.topup.creatingPackageId != null) return
        updateTopup { it.copy(creatingPackageId = packageId) }
        viewModelScope.launch {
            try {
                val order = topupRepository.createOrder(packageId)
                updateTopup {
                    it.copy(
                        creatingPackageId = null,
                        isSheetOpen = false,
                        checkoutUrl = order.checkoutUrl,
                        pendingOrderCode = order.orderCode,
                    )
                }
            } catch (e: Exception) {
                updateTopup {
                    it.copy(
                        creatingPackageId = null,
                        message = e.serverMessage("Couldn't start the payment."),
                    )
                }
            }
        }
    }

    /** Man hinh da mo link thanh toan — xoa di de khong mo lai. */
    fun consumeCheckoutUrl() {
        updateTopup { it.copy(checkoutUrl = null) }
    }

    /**
     * Hoi lai trang thai don dang cho.
     *
     * ⚠️ Nguon su that la **webhook PayOS -> server**, khong phai URL trinh
     * duyet chuyen ve: nguoi dung sua duoc thanh dia chi thanh `?status=PAID`.
     * Vi vay app chi hoi server, va chi tin khi server bao `PAID`.
     */
    fun refreshPendingOrder() {
        val orderCode = _uiState.value.topup.pendingOrderCode ?: return
        viewModelScope.launch {
            val order = try {
                topupRepository.getOrder(orderCode)
            } catch (_: Exception) {
                return@launch // mat mang tam thoi -> lan poll sau thu lai
            }
            // Trong luc doi mang, nguoi dung co the da bo cuoc hoac tao don MOI.
            // Khong co chot nay thi ket qua cu se xoa don moi dang cho, hoac hien
            // popup "da cong X Astrite" cua don khong con lien quan.
            if (_uiState.value.topup.pendingOrderCode != orderCode) return@launch
            when {
                order.isPaid -> {
                    // Doc lai state tu server thay vi tu cong o app
                    val state = runCatching { repository.getState() }.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        state = state ?: _uiState.value.state,
                        topup = _uiState.value.topup.copy(
                            pendingOrderCode = null,
                            creditedAstrite = order.astrite,
                        ),
                    )
                }

                !order.isWaiting -> updateTopup {
                    it.copy(pendingOrderCode = null, message = "Payment wasn't completed.")
                }

                else -> Unit // van PENDING -> tiep tuc poll
            }
        }
    }

    /** Nguoi dung bo cuoc giua chung -> ngung poll. */
    fun cancelPendingOrder() {
        updateTopup { it.copy(pendingOrderCode = null) }
    }

    fun dismissCreditedAstrite() {
        updateTopup { it.copy(creditedAstrite = null) }
    }

    fun dismissTopupMessage() {
        updateTopup { it.copy(message = null) }
    }

    private fun updateTopup(transform: (TopupUiState) -> TopupUiState) {
        _uiState.value = _uiState.value.copy(topup = transform(_uiState.value.topup))
    }
}
