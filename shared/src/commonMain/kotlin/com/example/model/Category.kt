package com.example.model

/**
 * Représente les catégories obligatoires pour la classification des dépenses dans EcoBudget.
 *
 * Entité du domaine 100 % Kotlin commun : elle ne référence plus aucune ressource Android
 * (`R.string.*`). Le libellé traduit est associé dans la couche ressources partagée
 * (voir `com.example.ui.resources.label`), ce qui garde le domaine indépendant de l'UI.
 *
 * @property emoji Pictogramme associé à la catégorie (chaîne Unicode, neutre vis-à-vis de la plateforme).
 */
enum class Category(
    val emoji: String
) {
    TRANSPORT("🚌"),
    ALIMENTATION("🍱"),
    LOISIRS("🎾"),
    LOGEMENT("🏠")
}
