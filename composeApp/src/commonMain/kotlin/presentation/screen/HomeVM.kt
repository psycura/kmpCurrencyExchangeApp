package presentation.screen

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import domain.CurrencyApiService
import domain.MongoRepository
import domain.PreferencesRepository
import domain.model.Currency
import domain.model.RateStatus
import domain.model.RequestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

sealed class HomeUiEvent {
    data object RefreshRates : HomeUiEvent()
    data object SwitchCurrencies : HomeUiEvent()
}

class HomeVM(
    private val preferences: PreferencesRepository,
    private val api: CurrencyApiService,
    private val mongoDb: MongoRepository
) : ScreenModel {
    private var _rateStatus: MutableState<RateStatus> = mutableStateOf(RateStatus.Idle)
    val rateStatus: State<RateStatus> = _rateStatus

    private var _allCurrencies = mutableStateListOf<Currency>()
    val allCurrencies: List<Currency> = _allCurrencies

    private var _sourceCurrency: MutableState<RequestState<Currency>> =
        mutableStateOf(RequestState.Idle)
    val sourceCurrency: State<RequestState<Currency>> = _sourceCurrency

    private var _targetCurrency: MutableState<RequestState<Currency>> =
        mutableStateOf(RequestState.Idle)
    val targetCurrency: State<RequestState<Currency>> = _targetCurrency

    init {
        screenModelScope.launch {
            fetchNewRates()
            readSourceCurrency()
            readTargetCurrency()
        }
    }

    fun sendEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.RefreshRates -> {
                screenModelScope.launch {
                    fetchNewRates()
                }
            }

            HomeUiEvent.SwitchCurrencies -> {
                switchCurrencies()
            }
        }
    }

    private fun switchCurrencies() {
        val source = _sourceCurrency.value
        val target = _targetCurrency.value
        _sourceCurrency.value = target
        _targetCurrency.value = source
    }

    private fun readSourceCurrency() {
        screenModelScope.launch(Dispatchers.Main) {
            preferences.readSourceCurrencyCode().collect {currencyCode ->
                val selectedCurrency = _allCurrencies.find { it.code == currencyCode.name }
                if (selectedCurrency != null) {
                    _sourceCurrency.value = RequestState.Success(selectedCurrency)
                } else {
                    _sourceCurrency.value = RequestState.Error("Currency not found")
                }
            }
        }
    }

    private fun readTargetCurrency() {
        screenModelScope.launch(Dispatchers.Main) {
            preferences.readTargetCurrencyCode().collect { currencyCode ->
                val selectedCurrency = _allCurrencies.find { it.code == currencyCode.name }
                if (selectedCurrency != null) {
                    _targetCurrency.value = RequestState.Success(selectedCurrency)
                } else {
                    _targetCurrency.value = RequestState.Error("Currency not found")
                }

            }
        }
    }

    private suspend fun fetchNewRates() {
        try {
            val localCache = mongoDb.readCurrencyData().first()
            if (localCache.isSuccess()) {
                if (localCache.getSuccessData().isNotEmpty()) {
                    println("HomeVM: DATABASE IS FULL")
                    val data = localCache.getSuccessData()
                    println("HomeVM: DATA SIZE ${data.size}")

                    _allCurrencies.addAll(data)
                    if (!preferences.isDataFresh(Clock.System.now().toEpochMilliseconds())) {
                        println("HomeVM: DATA IS NOT FRESH")
                        cacheData()
                    } else {
                        println("HomeVM: DATA IS FRESH")
                    }
                } else {
                    println("HomeVM: DATABASE NEEDS DATA")
                    cacheData()
                }
            } else if (localCache.isError()) {
                println("HomeVM: ERROR READING LOCAL DATABASE ${localCache.getErrorMessage()}")
            }

            getRateStatus()
        } catch (e: Exception) {
            println(e.message)
        }
    }

    private suspend fun cacheData() {
        val fetchedData = api.getLatestExchangeRates()
        if (fetchedData.isSuccess()) {
            mongoDb.cleanUp()
            fetchedData.getSuccessData().forEach {
                println("HomeVM: ADDING ${it.code}")
                mongoDb.insertCurrencyData(currency = it)
            }

            println("HomeVM: UPDATING _allCurrencies")
            _allCurrencies.addAll(fetchedData.getSuccessData())
        } else if (fetchedData.isError()) {
            println("HomeVM: FETCHING FAILED ${fetchedData.getErrorMessage()}")
        }
    }

    private suspend fun getRateStatus() {
        _rateStatus.value = if (preferences.isDataFresh(
                currentTimeStamp = Clock.System.now().toEpochMilliseconds()
            )
        ) RateStatus.Fresh
        else RateStatus.Stale
    }
}