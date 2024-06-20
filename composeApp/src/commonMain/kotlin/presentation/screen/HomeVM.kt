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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

sealed class HomeUiEvent {
    data object RefreshRates : HomeUiEvent()
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
            getRateStatus()
        }
    }

    fun sendEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.RefreshRates -> {
                screenModelScope.launch {
                    fetchNewRates()
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
                    _allCurrencies.addAll(localCache.getSuccessData())
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
            } else if(localCache.isError()){
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