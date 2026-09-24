package com.example.ui.resources

import androidx.compose.runtime.Composable
import com.example.model.Category
import com.example.model.YearMonth
import com.example.shared.resources.Res
import com.example.shared.resources.category_alimentation
import com.example.shared.resources.category_logement
import com.example.shared.resources.category_loisirs
import com.example.shared.resources.category_transport
import com.example.shared.resources.month_names
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource

/*
 * Pont entre le domaine partagé (Category, YearMonth — Kotlin pur) et le catalogue de ressources
 * Compose Multiplatform. Le domaine ne connaît pas les ressources ; seule cette couche le fait.
 */

/**
 * Ressource du libellé traduit de la catégorie (remplace l'ancien `labelResId: Int` = `R.string.*`).
 * Utilisation dans l'UI : `stringResource(category.label)`.
 */
val Category.label: StringResource
    get() = when (this) {
        Category.TRANSPORT -> Res.string.category_transport
        Category.ALIMENTATION -> Res.string.category_alimentation
        Category.LOISIRS -> Res.string.category_loisirs
        Category.LOGEMENT -> Res.string.category_logement
    }

/**
 * Libellé du mois affiché (ex: "Août 2026"), construit à partir du tableau de chaînes partagé
 * `month_names` (remplace `SimpleDateFormat("MMMM yyyy", Locale.FRENCH)`, API JVM).
 */
@Composable
fun YearMonth.displayLabel(): String {
    val monthNames = stringArrayResource(Res.array.month_names)
    return "${monthNames[month]} $year"
}
