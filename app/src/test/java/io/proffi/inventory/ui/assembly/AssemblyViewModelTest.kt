package io.proffi.inventory.ui.assembly

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.proffi.inventory.MainDispatcherRule
import io.proffi.inventory.data.AssemblyRepository
import io.proffi.inventory.network.LocationItem
import io.proffi.inventory.network.Recommendation
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationDetailItem
import io.proffi.inventory.network.RecommendationLocation
import io.proffi.inventory.network.RecommendationProduct
import io.proffi.inventory.network.RecommendationsListResponse
import io.proffi.inventory.network.UserInfo
import io.proffi.inventory.network.WarehouseInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssemblyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = mockk<AssemblyRepository>()

    private fun wh() = WarehouseInfo("w", "W")
    private fun user() = UserInfo("u", "F", "L", "e")
    private fun rec(id: String) = Recommendation(id, wh(), wh(), "collecting", null, user(), "", 1, 1)
    private fun listResponse(pageCount: Int, items: List<Recommendation>) =
        RecommendationsListResponse(pageCount, 100, 20, items)

    private fun detail() = RecommendationDetail(
        id = "r1",
        fromWarehouse = wh(),
        toWarehouse = wh(),
        status = "collecting",
        notes = null,
        createdBy = user(),
        createdAt = "",
        details = listOf(
            RecommendationDetailItem(
                id = "i",
                product = RecommendationProduct("p", "Name", "X", "ART"),
                requestedQuantity = 5,
                collectedQuantity = 0,
                locations = listOf(
                    RecommendationLocation(
                        "l", 5,
                        box = LocationItem("b", "Box", "BOX"),
                        shelf = LocationItem("s", "Shelf", "SH"),
                        zone = LocationItem("z", "Zone", "ZN")
                    )
                )
            )
        ),
        packItems = null
    )

    @Test
    fun pagination_hasMore_derivedFromPageCount() = runTest {
        coEvery { repo.getRecommendations(1, "collecting") } returns
            Result.success(listResponse(pageCount = 2, items = listOf(rec("a"))))
        coEvery { repo.getRecommendations(2, "collecting") } returns
            Result.success(listResponse(pageCount = 2, items = listOf(rec("b"))))

        val vm = AssemblyViewModel(repo)
        vm.loadRecommendations()
        advanceUntilIdle()

        val first = vm.recommendationsState.value as RecommendationsState.Success
        assertTrue(first.hasMore)
        assertEquals(1, first.items.size)

        vm.loadNextPage()
        advanceUntilIdle()

        val second = vm.recommendationsState.value as RecommendationsState.Success
        assertFalse(second.hasMore)
        assertEquals(2, second.items.size)
    }

    @Test
    fun scan_duplicateBarcode_collectsOnce() = runTest {
        val d = detail()
        coEvery { repo.getRecommendationDetail("r1") } returns Result.success(d)
        coEvery { repo.collectProduct(any(), any(), any(), any()) } returns Result.success(d)

        val vm = AssemblyViewModel(repo)
        vm.loadRecommendationDetail("r1")
        advanceUntilIdle()

        vm.onBoxScanned("BOX")
        vm.onProductScanned("X")
        vm.onProductScanned("X") // duplicate within debounce window
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.collectProduct(any(), any(), any(), any()) }
    }
}
