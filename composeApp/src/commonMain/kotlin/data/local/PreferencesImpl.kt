package data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import domain.PreferencesRepository
import domain.model.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalSettingsApi::class)
class PreferencesImpl(
    private val settings: Settings
) : PreferencesRepository {

    companion object {
        const val TIMESTAMP_KEY = "lastUpdated"
        const val SOURCE_CODE_KEY = "sourceCode"
        const val TARGET_CODE_KEY = "targetCode"
        val DEFAULT_SOURCE_CODE = CurrencyCode.USD.name
        val DEFAULT_TARGET_CODE = CurrencyCode.EUR.name
    }

    private val flowSettings: FlowSettings = (settings as ObservableSettings).toFlowSettings()

    override suspend fun saveLastUpdated(lastUpdated: String) {
        flowSettings.putLong(
            key = TIMESTAMP_KEY,
            value = Instant.parse(lastUpdated).toEpochMilliseconds()
        )
    }

    override suspend fun isDataFresh(currentTimeStamp: Long): Boolean {
        val savedTimestamp = flowSettings.getLong(
            key = TIMESTAMP_KEY,
            defaultValue = 0L
        )

        return if (savedTimestamp != 0L) {
            val currentInstant = Instant.fromEpochMilliseconds(currentTimeStamp)
            val savedInstant = Instant.fromEpochMilliseconds(savedTimestamp)

            val currentDateTime = currentInstant
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val savedDateTime = savedInstant
                .toLocalDateTime(TimeZone.currentSystemDefault())

            val daysDiff = currentDateTime.date.dayOfYear - savedDateTime.date.dayOfYear

            daysDiff < 1

        } else false
    }

    override suspend fun saveSourceCurrencyCode(code: String) {
        flowSettings.putString(SOURCE_CODE_KEY, code)
    }

    override suspend fun saveTargetCurrencyCode(code: String) {
        flowSettings.putString(TARGET_CODE_KEY, code)
    }

    override fun readSourceCurrencyCode(): Flow<CurrencyCode> {
        return flowSettings.getStringFlow(SOURCE_CODE_KEY, defaultValue = DEFAULT_SOURCE_CODE)
            .map { CurrencyCode.valueOf(it) }
    }

    override fun readTargetCurrencyCode(): Flow<CurrencyCode> {
        return flowSettings.getStringFlow(TARGET_CODE_KEY, defaultValue = DEFAULT_TARGET_CODE)
            .map { CurrencyCode.valueOf(it) }
    }
}