package presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import domain.model.CurrencyType
import presentation.component.CurrencyPickerDialog
import presentation.component.HomeHeader
import ui.theme.surfaceColor

class HomeScreen : Screen {

    @Composable
    override fun Content() {

        val vm = koinScreenModel<HomeVM>()
        val rateStatus by vm.rateStatus
        val source by vm.sourceCurrency
        val target by vm.targetCurrency
        val allCurrencies = vm.allCurrencies

        var amount by rememberSaveable { mutableStateOf(0.0) }

        var selectedCurrencyType: CurrencyType by remember {
            mutableStateOf(CurrencyType.None)
        }

        var dialogIsOpened by remember { mutableStateOf(false) }

        println("HomeVM:allCurrencies at Home Screen ${allCurrencies.size} ")


        if (dialogIsOpened) {
            CurrencyPickerDialog(
                currencies = allCurrencies,
                currencyType = selectedCurrencyType,
                onPositiveClick = {
                    dialogIsOpened = false
                },
                onDismiss = { dialogIsOpened = false }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor)
        ) {
            HomeHeader(
                status = rateStatus,
                source = source,
                target = target,
                amount = amount,
                onAmountChange = { amount = it },
                onRatesRefresh = {
                    vm.sendEvent(HomeUiEvent.RefreshRates)
                },
                onSwitchClick = {
                    vm.sendEvent(HomeUiEvent.SwitchCurrencies)
                }
            )
        }
    }
}