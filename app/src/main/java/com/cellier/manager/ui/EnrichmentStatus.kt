package com.cellier.manager.ui

import com.cellier.manager.data.EnrichmentRequest
import com.cellier.manager.data.EnrichmentRequestState

data class EnrichmentStatusUi(
    val text: String,
    val inProgress: Boolean = false,
    val completed: Boolean = false,
    val problem: Boolean = false,
)

fun enrichmentStatus(request: EnrichmentRequest, now: Long = System.currentTimeMillis()): EnrichmentStatusUi {
    if (request.lastErrorCode == "COMPANION_UNAVAILABLE") {
        return EnrichmentStatusUi(
            "L’ordinateur ne répond pas — nouvelle tentative automatique",
            problem = true,
        )
    }
    return when (request.state) {
        EnrichmentRequestState.LOCAL_PENDING.name -> EnrichmentStatusUi(
            "En attente de l’ordinateur", inProgress = true,
        )
        EnrichmentRequestState.SENDING.name -> EnrichmentStatusUi(
            "Connexion à l’ordinateur…", inProgress = true,
        )
        EnrichmentRequestState.WAITING_BROWSER.name -> when (request.serverState) {
            "QUEUED" -> if (now - request.updatedAt >= 30_000) {
                EnrichmentStatusUi(
                    "Chrome ou l’extension ne répond pas — toucher pour relancer",
                    problem = true,
                )
            } else {
                EnrichmentStatusUi(
                    "Demande reçue par le PC — attente de Chrome", inProgress = true,
                )
            }
            "CLAIMED", "SEARCHING" -> EnrichmentStatusUi(
                "Recherche en cours dans Chrome", inProgress = true,
            )
            "NAVIGATING" -> EnrichmentStatusUi(
                "Résultat trouvé — ouverture de la fiche", inProgress = true,
            )
            "CAPTURING" -> EnrichmentStatusUi(
                "Lecture des renseignements de la page", inProgress = true,
            )
            "PARSING" -> EnrichmentStatusUi(
                "Analyse des renseignements", inProgress = true,
            )
            "NEEDS_USER" -> EnrichmentStatusUi(
                "Résultat non confirmé — toucher pour relancer", problem = true,
            )
            "FAILED" -> EnrichmentStatusUi(
                "La recherche a échoué dans Chrome — toucher pour réessayer", problem = true,
            )
            else -> EnrichmentStatusUi(
                "Recherche transmise à l’ordinateur", inProgress = true,
            )
        }
        EnrichmentRequestState.READY_FOR_REVIEW.name -> EnrichmentStatusUi(
            "Résultat ambigu — vérifier sur ce téléphone", completed = true,
        )
        EnrichmentRequestState.APPLIED.name -> EnrichmentStatusUi(
            "Recherche terminée — renseignements ajoutés", completed = true,
        )
        EnrichmentRequestState.REJECTED.name -> EnrichmentStatusUi("Résultat refusé")
        EnrichmentRequestState.CANCELLED.name -> EnrichmentStatusUi("Recherche annulée")
        EnrichmentRequestState.STALE.name -> EnrichmentStatusUi(
            "La fiche a changé — résultat périmé", problem = true,
        )
        EnrichmentRequestState.EXPIRED.name -> EnrichmentStatusUi(
            "Recherche expirée — toucher pour recommencer", problem = true,
        )
        else -> EnrichmentStatusUi(request.state)
    }
}
