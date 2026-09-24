package com.example.viewmodel

import com.example.data.repository.FakeTransactionRepository
import com.example.model.Category
import com.example.model.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests de non-régression de la logique partagée (tableau de bord, calcul des dépenses,
 * navigation mensuelle, filtres, CRUD). Exécutables sur toutes les cibles (commonTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EcoBudgetViewModelTest {

    private val referenceMonth = YearMonth(2026, 7) // août 2026
    private val fixedClock = object : Clock {
        override fun now(): Instant =
            Instant.fromEpochMilliseconds(referenceMonth.toEpochMillis(day = 22, hour = 14))
    }

    @BeforeTest
    fun setUp() {
        // viewModelScope utilise Dispatchers.Main : on le remplace par un dispatcher de test
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = EcoBudgetViewModel(FakeTransactionRepository(referenceMonth), fixedClock)

    @Test
    fun dashboardComputesMonthlyTotals() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(7, state.monthTransactions.size)
        assertEquals(365_800.0, state.totalSpent)
        assertEquals(134_200.0, state.remainingBudget)
        assertEquals(73, state.budgetUsagePercentage)
    }

    @Test
    fun categoryFiltersSupportMultiSelection() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.toggleCategory(Category.TRANSPORT)
        assertEquals(6_000.0, vm.uiState.value.categorySpent)

        vm.toggleCategory(Category.LOGEMENT)
        assertEquals(4, vm.uiState.value.filteredTransactions.size)
        assertEquals(304_000.0, vm.uiState.value.categorySpent)

        vm.clearCategoryFilter()
        assertEquals(7, vm.uiState.value.filteredTransactions.size)
    }

    @Test
    fun monthNavigationFiltersTransactions() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.previousMonth()
        assertEquals(referenceMonth.previous(), vm.uiState.value.currentMonth)
        assertEquals(372_000.0, vm.uiState.value.totalSpent)

        vm.nextMonth(); vm.nextMonth()
        assertEquals(270_000.0, vm.uiState.value.totalSpent)

        vm.goToCurrentMonth()
        assertEquals(referenceMonth, vm.uiState.value.currentMonth)
    }

    @Test
    fun addEditAndDeleteTransaction() = runTest {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.openAddDialog()
        vm.saveTransaction("Pain", 500.0, Category.ALIMENTATION)
        assertFalse(vm.uiState.value.isAddDialogOpen)
        assertEquals(366_300.0, vm.uiState.value.totalSpent)

        val added = vm.uiState.value.monthTransactions.first { it.title == "Pain" }
        vm.openEditDialog(added)
        vm.saveTransaction("Pain complet", 700.0, Category.ALIMENTATION)
        assertEquals(366_500.0, vm.uiState.value.totalSpent)

        vm.deleteTransaction(added.id)
        assertEquals(365_800.0, vm.uiState.value.totalSpent)
    }
}
