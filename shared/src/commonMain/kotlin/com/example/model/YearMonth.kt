package com.example.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Modèle immuable représentant un mois spécifique pour la navigation budgétaire.
 *
 * Version multiplateforme : `java.util.Calendar`, `java.util.Date` et `java.text.SimpleDateFormat`
 * (API JVM, indisponibles sur iOS/Kotlin Native) sont remplacés par `kotlinx-datetime`.
 * Le libellé affiché ("Août 2026") n'est plus calculé ici : il est construit dans la couche
 * ressources partagée à partir du catalogue de chaînes (voir `com.example.ui.resources.displayLabel`).
 *
 * @property year Année (ex: 2026).
 * @property month Index du mois de 0 (Janvier) à 11 (Décembre) — convention conservée pour la compatibilité.
 */
data class YearMonth(
    val year: Int,
    val month: Int
) {

    /**
     * Retourne le YearMonth précédent.
     */
    fun previous(): YearMonth = plusMonths(-1)

    /**
     * Retourne le YearMonth suivant.
     */
    fun next(): YearMonth = plusMonths(1)

    /**
     * Décale le mois de [offset] mois (positif ou négatif) en gérant le changement d'année.
     * Remplace le comportement « lenient » de `Calendar.set(Calendar.MONTH, m + offset)`.
     */
    fun plusMonths(offset: Int): YearMonth {
        val totalMonths = year * 12 + month + offset
        return YearMonth(
            year = totalMonths.floorDiv(12),
            month = totalMonths.mod(12)
        )
    }

    /**
     * Vérifie si un timestamp millisecondes appartient à ce mois précis (fuseau [timeZone]).
     */
    fun containsTimestamp(
        timestamp: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = fromTimestamp(timestamp, timeZone) == this

    /**
     * Convertit un jour/heure de ce mois en timestamp millisecondes (fuseau [timeZone]).
     * Remplace la séquence `Calendar.set(...)` + `cal.timeInMillis`.
     */
    fun toEpochMillis(
        day: Int,
        hour: Int,
        minute: Int = 0,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long = LocalDateTime(
        year = year,
        monthNumber = month + 1, // kotlinx-datetime : mois de 1 à 12
        dayOfMonth = day,
        hour = hour,
        minute = minute
    ).toInstant(timeZone).toEpochMilliseconds()

    companion object {
        /**
         * Crée le YearMonth courant.
         *
         * @param clock Horloge injectable (facilite les tests), par défaut l'horloge système.
         */
        fun current(
            clock: Clock = Clock.System,
            timeZone: TimeZone = TimeZone.currentSystemDefault()
        ): YearMonth = fromInstant(clock.now(), timeZone)

        /**
         * Crée le YearMonth correspondant à un timestamp.
         */
        fun fromTimestamp(
            timestamp: Long,
            timeZone: TimeZone = TimeZone.currentSystemDefault()
        ): YearMonth = fromInstant(Instant.fromEpochMilliseconds(timestamp), timeZone)

        private fun fromInstant(instant: Instant, timeZone: TimeZone): YearMonth {
            val dateTime = instant.toLocalDateTime(timeZone)
            return YearMonth(
                year = dateTime.year,
                month = dateTime.monthNumber - 1
            )
        }
    }
}
