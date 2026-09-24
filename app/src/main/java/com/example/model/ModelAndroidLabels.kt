package com.example.model

import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Adaptateurs TEMPORAIRES côté Android (étape intermédiaire de la migration).
 *
 * Les modèles Category et YearMonth vivent désormais dans shared/commonMain et ne peuvent plus
 * référencer R.string ni java.text. Ces extensions conservent l'ancien contrat pour l'UI native
 * le temps de migrer les ressources textuelles vers le catalogue partagé (composeResources).
 */

/** Identifiant de ressource Android du libellé de la catégorie. */
val Category.labelResId: Int
    get() = when (this) {
        Category.TRANSPORT -> R.string.category_transport
        Category.ALIMENTATION -> R.string.category_alimentation
        Category.LOISIRS -> R.string.category_loisirs
        Category.LOGEMENT -> R.string.category_logement
    }

/** Libellé formaté en français (ex: "Août 2026"). */
val YearMonth.displayLabel: String
    get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val formatted = SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(Date(cal.timeInMillis))
        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
    }
