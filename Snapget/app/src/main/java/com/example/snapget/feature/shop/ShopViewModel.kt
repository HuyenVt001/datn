package com.example.snapget.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.data.SettingsPreferences
import com.example.snapget.core.designsystem.effect.TouchEffectRegistry
import com.example.snapget.core.designsystem.skin.SkinRegistry
import com.example.snapget.core.network.dto.ShopItemDto
import com.example.snapget.core.network.dto.TopupPackageDto
import com.example.snapget.core.network.serverMessage
import com.example.snapget.feature.shop.data.ShopRepository
import com.example.snapget.feature.shop.data.TopupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 4 tab cua Shop — thu tu nay la thu tu hien tren TabRow (user chot 2026-11-15). */
enum class ShopTab(val title: String, val itemType: String?) {
    ASTRITE("Astrite", null),
    SKINS("Skins", ShopItemDto.TYPE_SKIN),
    FRAMES("Frames", ShopItemDto.TYPE_FRAME),
    EFFECTS("Effects", ShopItemDto.TYPE_EFFECT),
}

/** Trang thai man Shop. */
data class ShopUiState(
    val status: LoadStatus = LoadStatus.Init(),
    val astrite: Int = 0,
    /** Mat hang da LOC (bo item APK nay khong ve duoc — xem [supportedItems]). */
    val items: List<ShopItemDto> = emptyList(),
    /** Mo vao tab Skins: mat hang la thu nguoi dung vao Shop de xem. */
    val tab: ShopTab = ShopTab.SKINS,
    /** Mat hang dang hoi "Buy X for N Astrite?" — khac null = dang mo dialog. */
    val confirmItem: ShopItemDto? = null,
    /** itemId dang goi POST /shop/buy — chan bam kep. */
    val buyingItemId: String? = null,
    /** Skin/effect vua mua — khac null = dang hoi "Apply now?". */
    val applyPrompt: ShopItemDto? = null,
    val currentSkinId: Int = SkinRegistry.DEFAULT_ID,
    val currentEffectId: Int = TouchEffectRegistry.NONE_ID,
    /** Thong bao ngan (toast). */
    val message: String? = null,
    /** Luong nap Astrite qua PayOS (tab Astrite). */
    val topup: TopupUiState = TopupUiState(),
) {
    fun itemsOf(tab: ShopTab): List<ShopItemDto> = items.filter { it.itemType == tab.itemType }

    fun canAfford(item: ShopItemDto): Boolean = astrite >= item.price

    /** Skin/effect da so huu va DANG dung (khung khong co khai niem nay). */
    fun isInUse(item: ShopItemDto): Boolean {
        val id = item.refId.toIntOrNull() ?: return false
        return when (item.itemType) {
            ShopItemDto.TYPE_SKIN -> id == currentSkinId
            ShopItemDto.TYPE_EFFECT -> id == currentEffectId
            else -> false
        }
    }
}

/** Trang thai luong nap Astrite qua PayOS. */
data class TopupUiState(
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
)

/**
 * ViewModel man Shop (SHOP_PLAN.md muc P3).
 *
 * Mua bang Astrite qua `POST /shop/buy`; nap Astrite qua PayOS (logic chuyen
 * nguyen tu `GachaViewModel` cu). App KHONG tu tru tien hay tu mo khoa — so du
 * va `isOwned` luon lay tu server tra ve.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val repository: ShopRepository,
    private val topupRepository: TopupRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    /** Tai catalog + goi nap. Goi moi lan vao man (so du co the doi o noi khac). */
    fun load() {
        _uiState.value = _uiState.value.copy(
            status = LoadStatus.Loading(),
            // Skin/effect co the vua doi o Appearance
            currentSkinId = settingsPreferences.skinId.value,
            currentEffectId = settingsPreferences.touchEffectId.value,
        )
        viewModelScope.launch {
            try {
                val catalog = repository.getCatalog()
                _uiState.value = _uiState.value.copy(
                    status = LoadStatus.Success(),
                    astrite = catalog.astrite,
                    items = supportedItems(catalog.items),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = LoadStatus.Error(e.serverMessage("Couldn't load the Shop.")),
                )
            }
        }
        loadPackages()
    }

    fun selectTab(tab: ShopTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    // ==================== Mua vat pham ====================

    /** Bam nut gia -> mo dialog xac nhan (dialog tu doi noi dung khi thieu Astrite). */
    fun requestBuy(item: ShopItemDto) {
        if (item.isOwned || _uiState.value.buyingItemId != null) return
        _uiState.value = _uiState.value.copy(confirmItem = item)
    }

    fun dismissConfirm() {
        _uiState.value = _uiState.value.copy(confirmItem = null)
    }

    /** Thieu Astrite -> nut "Top up" trong dialog nhay sang tab Astrite. */
    fun goToTopup() {
        _uiState.value = _uiState.value.copy(confirmItem = null, tab = ShopTab.ASTRITE)
    }

    /**
     * Mua mat hang dang xac nhan.
     *
     * Chan bam kep bang `buyingItemId`: server co transaction + 409 nen bam 2 lan
     * khong mat tien 2 lan, nhung lan 2 se hien loi kho hieu.
     */
    fun confirmBuy() {
        val item = _uiState.value.confirmItem ?: return
        if (_uiState.value.buyingItemId != null) return
        _uiState.value = _uiState.value.copy(confirmItem = null, buyingItemId = item.itemId)
        viewModelScope.launch {
            try {
                val result = repository.buy(item.itemId)
                val bought = item.copy(isOwned = true)
                val promptApply = item.itemType != ShopItemDto.TYPE_FRAME
                _uiState.value = _uiState.value.copy(
                    buyingItemId = null,
                    astrite = result.astriteAfter,
                    items = _uiState.value.items.map { if (it.itemId == item.itemId) bought else it },
                    applyPrompt = if (promptApply) bought else null,
                    message = if (promptApply) null else "Frame unlocked! Pick it when you post.",
                )
            } catch (e: Exception) {
                // 409 (da so huu) / 400 (thieu tien, het ban) -> doc lai catalog de
                // so du va trang thai so huu tren man hinh khop server.
                val catalog = runCatching { repository.getCatalog() }.getOrNull()
                _uiState.value = _uiState.value.copy(
                    buyingItemId = null,
                    astrite = catalog?.astrite ?: _uiState.value.astrite,
                    items = catalog?.let { supportedItems(it.items) } ?: _uiState.value.items,
                    message = e.serverMessage("Purchase failed. Please try again."),
                )
            }
        }
    }

    /** "Apply now?" -> Apply: ap dung ngay skin/effect vua mua. */
    fun applyPrompted() {
        val item = _uiState.value.applyPrompt ?: return
        _uiState.value = _uiState.value.copy(applyPrompt = null)
        apply(item)
    }

    fun dismissApplyPrompt() {
        _uiState.value = _uiState.value.copy(applyPrompt = null)
    }

    /**
     * Ap dung skin/effect DA so huu (ghi thang [SettingsPreferences] — StateFlow
     * tren singleton do la cau noi toi MainActivity nen app doi NGAY).
     */
    fun apply(item: ShopItemDto) {
        if (!item.isOwned) return
        val id = item.refId.toIntOrNull() ?: return
        when (item.itemType) {
            ShopItemDto.TYPE_SKIN -> {
                settingsPreferences.setSkinId(id)
                _uiState.value = _uiState.value.copy(currentSkinId = id)
            }

            ShopItemDto.TYPE_EFFECT -> {
                settingsPreferences.setTouchEffectId(id)
                _uiState.value = _uiState.value.copy(currentEffectId = id)
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // ==================== Nap Astrite (PayOS) ====================

    private fun loadPackages() {
        updateTopup { it.copy(isLoadingPackages = true) }
        viewModelScope.launch {
            try {
                val packages = topupRepository.listPackages()
                updateTopup { it.copy(isLoadingPackages = false, packages = packages) }
            } catch (_: Exception) {
                // Khong toast: catalog loi da hien man loi; tab Astrite tu hien
                // "No top-up packages" + vao lai man la tai lai.
                updateTopup { it.copy(isLoadingPackages = false) }
            }
        }
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
                        checkoutUrl = order.checkoutUrl,
                        pendingOrderCode = order.orderCode,
                    )
                }
            } catch (e: Exception) {
                updateTopup { it.copy(creatingPackageId = null) }
                _uiState.value = _uiState.value.copy(
                    message = e.serverMessage("Couldn't start the payment."),
                )
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
                    // Doc lai so du tu server thay vi tu cong o app
                    val catalog = runCatching { repository.getCatalog() }.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        astrite = catalog?.astrite ?: _uiState.value.astrite,
                        topup = _uiState.value.topup.copy(
                            pendingOrderCode = null,
                            creditedAstrite = order.astrite,
                        ),
                    )
                }

                !order.isWaiting -> {
                    updateTopup { it.copy(pendingOrderCode = null) }
                    _uiState.value = _uiState.value.copy(message = "Payment wasn't completed.")
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

    private fun updateTopup(transform: (TopupUiState) -> TopupUiState) {
        _uiState.value = _uiState.value.copy(topup = transform(_uiState.value.topup))
    }

    companion object {
        /**
         * Bo mat hang ma APK nay khong ve duoc: skin/effect co `refId` khong nam
         * trong registry (server niem yet skin moi hon ban app dang cai), va 2 id
         * mac dinh (skin 0 / effect 0 — luon so huu, khong ban). Khong loc thi
         * nguoi dung mua xong ma khong ap dung duoc.
         *
         * Khung la anh tu server nen luon hien.
         */
        fun supportedItems(items: List<ShopItemDto>): List<ShopItemDto> = items.filter { item ->
            val id = item.refId.toIntOrNull()
            when (item.itemType) {
                ShopItemDto.TYPE_FRAME -> true
                ShopItemDto.TYPE_SKIN ->
                    id != null && id != SkinRegistry.DEFAULT_ID && SkinRegistry.all.any { it.id == id }
                ShopItemDto.TYPE_EFFECT ->
                    id != null && id != TouchEffectRegistry.NONE_ID && TouchEffectRegistry.all.any { it.id == id }
                else -> false
            }
        }
    }
}
