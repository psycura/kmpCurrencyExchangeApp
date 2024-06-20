package di

import com.russhwolf.settings.Settings
import data.local.MongoImpl
import data.local.PreferencesImpl
import data.remote.api.CurrencyApiServiceImpl
import domain.CurrencyApiService
import domain.MongoRepository
import domain.PreferencesRepository
import org.koin.core.context.startKoin
import org.koin.dsl.module
import presentation.screen.HomeVM

val appModule = module {
    single { Settings() }
    single<PreferencesRepository> { PreferencesImpl(settings = get()) }
    single<CurrencyApiService> { CurrencyApiServiceImpl(preferencesRepository = get()) }
    single<MongoRepository> { MongoImpl() }
    factory {
        HomeVM(
            preferences = get(),
            api = get(),
            mongoDb = get(),
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }

}