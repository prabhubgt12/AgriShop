package com.fertipos.agroshop.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import com.fertipos.agroshop.ui.customer.CustomerScreen
import com.fertipos.agroshop.ui.product.ProductScreen
import com.fertipos.agroshop.ui.billing.BillingScreen
import com.fertipos.agroshop.ui.reports.ReportsScreen
import com.fertipos.agroshop.ui.purchase.PurchaseScreen
import com.fertipos.agroshop.ui.settings.SettingsScreen
import com.fertipos.agroshop.ui.history.InvoiceHistoryScreen
import com.fertipos.agroshop.ui.history.PurchaseHistoryScreen
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val navVm: AppNavViewModel = hiltViewModel()
    val selected = navVm.selected.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Intercept system back: when not on Home, go back to Home instead of exiting
        BackHandler(enabled = selected.value != 0) {
            navVm.navigateTo(0)
        }
        // On Home tab, back should logout (navigate to Login)
        BackHandler(enabled = selected.value == 0) {
            onLogout()
        }
        when (selected.value) {
            0 -> HomeScreen(onNavigateToTab = {
                if (it == 3) navVm.requestNewBill()
                navVm.navigateTo(it)
            })
            1 -> CustomerScreen()
            2 -> ProductScreen()
            3 -> BillingScreen(navVm)
            4 -> ReportsScreen()
            7 -> PurchaseScreen(navVm)
            5 -> SettingsScreen()
            6 -> InvoiceHistoryScreen(navVm)
            8 -> PurchaseHistoryScreen(navVm)
        }
    }
}

@Composable
private fun HomeScreen(onNavigateToTab: (Int) -> Unit) {
    val profVm: com.fertipos.agroshop.ui.settings.CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    )
    {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header with logo and shop name
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = CardDefaults.elevatedShape
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = profile.logoUri.takeIf { it.isNotBlank() },
                    contentDescription = "Logo",
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name.ifBlank { "Your Shop" },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tiles grid
        val tiles = listOf(
            TileData("Customers", Icons.Filled.People) { onNavigateToTab(1) },
            TileData("Products", Icons.Filled.Inventory2) { onNavigateToTab(2) },
            TileData("Billing", Icons.Filled.ReceiptLong) { onNavigateToTab(3) },
            TileData("Purchases", Icons.Filled.ReceiptLong) { onNavigateToTab(7) },
            TileData("View Bills", Icons.Filled.History) { onNavigateToTab(6) },
            TileData("View Purchases", Icons.Filled.History) { onNavigateToTab(8) },
            TileData("Reports", Icons.Filled.BarChart) { onNavigateToTab(4) },
            TileData("Settings", Icons.Filled.Settings) { onNavigateToTab(5) },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(tiles) { t ->
                TileCard(title = t.title, icon = t.icon, onClick = t.onClick)
            }
        }
    }
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title)
            Spacer(Modifier.padding(2.dp))
            Text(subtitle)
        }
    }
}

private data class TileData(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@Composable
private fun TileCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
