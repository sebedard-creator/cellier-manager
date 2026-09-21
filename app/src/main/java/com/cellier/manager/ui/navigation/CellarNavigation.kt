package com.cellier.manager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cellier.manager.ui.screens.AjouterScreen
import com.cellier.manager.ui.screens.ConsulterScreen
import com.cellier.manager.ui.screens.FicheProduitScreen
import com.cellier.manager.ui.screens.SommelierScreen
import com.cellier.manager.ui.screens.EnrichmentScreen
import com.cellier.manager.ui.screens.PairingScreen
import com.cellier.manager.ui.screens.ProposalScreen
import com.cellier.manager.ui.screens.DepletedScreen

/**
 * Routes de navigation de l'app.
 */
sealed class Route(val path: String) {
    data object Consulter : Route("consulter")
    data object Sommelier : Route("sommelier")
    data object Ajouter : Route("ajouter")
    data object Recherches : Route("recherches")
    data object Reglages : Route("reglages")
    data object Epuises : Route("epuises")
    data object Proposition : Route("proposition/{requestId}") {
        fun create(requestId: String) = "proposition/$requestId"
    }
    data object Fiche : Route("fiche/{itemId}") {
        fun create(itemId: Long) = "fiche/$itemId"
    }
}

@Composable
fun CellarNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Route.Consulter.path
    ) {
        composable(Route.Consulter.path) {
            ConsulterScreen(
                onSommelierClick = { navController.navigate(Route.Sommelier.path) },
                onSearchesClick = { navController.navigate(Route.Recherches.path) },
                onSettingsClick = { navController.navigate(Route.Reglages.path) },
                onDepletedClick = { navController.navigate(Route.Epuises.path) },
                onAddClick = { navController.navigate(Route.Ajouter.path) },
                onItemClick = { itemId ->
                    navController.navigate(Route.Fiche.create(itemId))
                }
            )
        }

        composable(Route.Recherches.path) {
            EnrichmentScreen(
                onBack = { navController.popBackStack() },
                onReview = { navController.navigate(Route.Proposition.create(it)) }
            )
        }

        composable(Route.Reglages.path) {
            PairingScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Epuises.path) {
            DepletedScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { navController.navigate(Route.Fiche.create(it)) }
            )
        }

        composable(
            route = Route.Proposition.path,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType })
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString("requestId") ?: return@composable
            ProposalScreen(requestId = requestId, onBack = { navController.popBackStack() })
        }

        composable(Route.Sommelier.path) {
            SommelierScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { navController.navigate(Route.Fiche.create(it)) }
            )
        }

        composable(Route.Ajouter.path) {
            AjouterScreen(
                onBack = { navController.popBackStack() },
                onSaved = { itemId ->
                    // Après sauvegarde, on navigue vers la fiche en remplaçant
                    // l'écran Ajouter dans le back stack
                    navController.popBackStack()
                    navController.navigate(Route.Fiche.create(itemId))
                }
            )
        }

        composable(
            route = Route.Fiche.path,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            FicheProduitScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
