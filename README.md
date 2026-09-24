# EcoBudget 🌿 — Migration vers Kotlin Multiplatform (KMP)

> UE Développement mobile avancé — Année académique 2025-2026
> **Document Technique de Synthèse**

Ce dépôt fait évoluer le prototype Android natif **EcoBudget** (Kotlin + Jetpack Compose) vers une architecture
**Kotlin Multiplatform / Compose Multiplatform**. La logique métier, les données, l'état d'interface, le ViewModel et
les ressources textuelles sont désormais dans un module commun `shared` qui cible **Android et iOS**. L'application
Android `app` consomme ce socle et conserve son interface Jetpack Compose et toutes ses fonctionnalités.

---

## Sommaire

1. [Architecture cible](#1-architecture-cible)
2. [Configuration Gradle](#2-configuration-gradle)
3. [Analyse fichier par fichier des migrations vers `commonMain`](#3-analyse-fichier-par-fichier-des-migrations-vers-commonmain)
4. [Gestion d'état et cycle de vie](#4-gestion-détat-et-cycle-de-vie)
5. [Centralisation des ressources textuelles](#5-centralisation-des-ressources-textuelles)
6. [Validation fonctionnelle](#6-validation-fonctionnelle)
7. [Historique des commits](#7-historique-des-commits)
8. [Limites et pistes d'évolution](#8-limites-et-pistes-dévolution)

---

## 1. Architecture cible

```
EcoBudget/
├── app/                                   ← Application Android native (UI Jetpack Compose)
│   └── src/main/java/com/example/
│       ├── MainActivity.kt                (inchangé : by viewModels())
│       └── ui/  screens/ components/ theme/   (consomment shared : état + Res.string)
│
└── shared/                                ← Module Kotlin Multiplatform
    ├── build.gradle.kts
    └── src/
        ├── commonMain/
        │   ├── kotlin/com/example/
        │   │   ├── model/            Category, Transaction, YearMonth
        │   │   ├── data/repository/  TransactionRepository, FakeTransactionRepository
        │   │   ├── viewmodel/        EcoBudgetUiState, EcoBudgetViewModel
        │   │   └── ui/resources/     ResourceMappings (pont domaine ↔ ressources)
        │   └── composeResources/values/strings.xml   ← catalogue de libellés partagé
        ├── commonTest/               tests de non-régression (kotlin.test)
        ├── androidMain/              (vide : aucune implémentation spécifique nécessaire)
        └── iosMain/                  (vide : aucune implémentation spécifique nécessaire)
```

Flux de dépendances (unidirectionnel) :

```
app (UI Android) ──► shared.viewmodel ──► shared.data ──► shared.model
        │                                                    ▲
        └──────────► shared.ui.resources (Res.string) ───────┘
```

Le domaine (`model`) ne dépend de rien d'autre que de la bibliothèque standard Kotlin et de `kotlinx-datetime`.

---

## 2. Configuration Gradle

### 2.1 Versions retenues (`gradle/libs.versions.toml`)

| Élément | Avant | Après | Raison |
|---|---|---|---|
| Kotlin | 2.0.21 | **2.1.21** | Compose Multiplatform 1.8.x demande Kotlin 2.1.20 ou plus |
| Compose Multiplatform (`org.jetbrains.compose`) | — | **1.8.2** | Catalogue de ressources multiplateforme (`Res`) |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | — | **2.9.1** | `ViewModel` + `viewModelScope` en `commonMain` |
| `kotlinx-datetime` | — | **0.6.2** | Remplace `java.util.Calendar` / `SimpleDateFormat` |
| `kotlinx-coroutines-core` | 1.10.2 | 1.10.2 | Déjà multiplateforme (Flow, StateFlow) |
| Compose BOM | 2024.10.01 | **2025.06.00** | Alignement sur Jetpack Compose 1.8, version sur laquelle repose CMP 1.8.2 |
| AndroidX lifecycle | 2.8.7 | **2.9.1** | Même version que le lifecycle KMP de JetBrains, pour éviter deux versions du même artefact |
| Room + KSP | présents | **supprimés** | Aucune entité ni DAO dans le projet (`app/build.gradle.kts` indiquait « à retirer si Room n'est plus utilisé »). KSP ralentissait aussi la montée de version de Kotlin |

`kotlinx-datetime` reste en **0.6.x** volontairement : la 0.7 remplace `kotlinx.datetime.Instant/Clock` par
`kotlin.time.Instant/Clock`, ce qui demande une version de Kotlin plus récente.

### 2.2 Fichiers modifiés

**`settings.gradle.kts`**
```kotlin
include(":app")
include(":shared")
```

**`build.gradle.kts` (racine)** : on déclare les plugins sans les appliquer (`apply false`), pour qu'une seule
version soit résolue pour tous les modules :
```kotlin
alias(libs.plugins.android.library) apply false
alias(libs.plugins.kotlin.multiplatform) apply false
alias(libs.plugins.compose.multiplatform) apply false
```

**`shared/build.gradle.kts`**, points essentiels :

```kotlin
plugins {
  alias(libs.plugins.kotlin.multiplatform)   // KMP
  alias(libs.plugins.android.library)        // produit une AAR pour :app
  alias(libs.plugins.compose.multiplatform)  // ressources Compose Multiplatform
  alias(libs.plugins.kotlin.compose)         // compilateur Compose (requis par CMP)
}

kotlin {
  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
  listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
    it.binaries.framework { baseName = "Shared"; isStatic = true }
  }
  sourceSets {
    all { languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi") }
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      api(libs.jetbrains.lifecycle.viewmodel)
      implementation(libs.compose.mp.runtime)
      api(libs.compose.mp.resources)
    }
    androidMain.dependencies { implementation(libs.kotlinx.coroutines.android) }
  }
}

compose.resources {
  publicResClass = true                         // Res visible depuis :app
  packageOfResClass = "com.example.shared.resources"
  generateResClass = always
}
```

- **`api` et non `implementation`** : les types de ces bibliothèques apparaissent dans l'API publique du module
  (`StateFlow` dans le ViewModel, `ViewModel` comme classe parente, `Clock` dans les constructeurs, `StringResource`
  dans les mappings). Avec `api`, `:app` y a accès sans les redéclarer.
- **`kotlinx-coroutines-android` dans `androidMain`** : il fournit `Dispatchers.Main`, adossé au `Looper` Android, que
  `viewModelScope` utilise. Sur iOS, `kotlinx-coroutines-core` fournit déjà un `Dispatchers.Main` basé sur la file
  principale Darwin.
- **`publicResClass = true`** : sans ce réglage, la classe `Res` générée est `internal` et l'UI de `:app` ne peut pas y
  accéder.

**`app/build.gradle.kts`**
```kotlin
implementation(project(":shared"))
```
Le plugin KSP et les dépendances Room ont été retirés.

**`gradle.properties`**
```properties
kotlin.native.ignoreDisabledTargets=true
```
Les cibles iOS ne se compilent que sur macOS. Sous Windows ou Linux, cette propriété fait simplement ignorer ces cibles
au lieu d'afficher un avertissement à chaque build. Sur un Mac, elles sont compilées normalement.

---

## 3. Analyse fichier par fichier des migrations vers `commonMain`

> Méthode : chaque fichier a été déplacé avec `git mv` vers `shared/src/commonMain/kotlin/...`. Tout symbole qui n'existe
> que sur la JVM ou sur Android provoque alors une erreur de compilation dans le code commun. Chaque erreur a été traitée
> selon la même priorité : **(1)** une API de la bibliothèque standard Kotlin, **(2)** une bibliothèque officielle
> multiplateforme, **(3)** en dernier recours seulement, le mécanisme `expect`/`actual`. Ce troisième niveau n'a pas été
> nécessaire ici.

### 3.1 `model/Category.kt`

| | |
|---|---|
| **Problème rencontré** | `import com.example.R` et la propriété `labelResId: Int` initialisée avec `R.string.category_transport`, etc. La classe `R` est générée par AAPT pour Android uniquement : elle n'existe ni dans `commonMain` ni sur iOS. Erreur : `Unresolved reference 'R'`. |
| **Choix technique** | La propriété `labelResId` est retirée : l'enum ne garde que son identité et son `emoji` (une chaîne Unicode). Le libellé traduit est fourni par l'extension `val Category.label: StringResource` dans `ui/resources/ResourceMappings.kt`, qui renvoie vers le catalogue partagé `Res.string.category_*`. |
| **Justification** | Le domaine reste du Kotlin pur, compilable pour toutes les cibles et testable sans framework graphique. Seule la couche de présentation connaît les ressources, ce qui respecte la séparation domaine / présentation. `StringResource` est un type Compose Multiplatform qui fonctionne sur Android et iOS : l'UI Android l'affiche avec `stringResource(category.label)`, et une future UI iOS pourra faire de même. |

### 3.2 `model/Transaction.kt`

| | |
|---|---|
| **Problème rencontré** | Aucun. La classe n'utilise que `String`, `Double`, `Long` et `Category`. |
| **Choix technique** | Déplacement tel quel. La date reste un `Long` (millisecondes depuis l'epoch UTC). |
| **Justification** | Un timestamp `Long` se représente de la même façon partout, y compris dans le framework iOS exporté, où il devient un `Int64`. Le passer en `kotlinx.datetime.Instant` aurait modifié le contrat pour l'UI Android, sans rien apporter aux fonctionnalités. |

### 3.3 `model/YearMonth.kt`

| | |
|---|---|
| **Problème rencontré** | `import java.text.SimpleDateFormat`, `java.util.Calendar`, `java.util.Date`, `java.util.Locale`, utilisés pour : **(a)** `displayLabel` (`SimpleDateFormat("MMMM yyyy", Locale.FRENCH)`), **(b)** `containsTimestamp`, `current()` et `fromTimestamp()` (`Calendar.getInstance()`), **(c)** `titlecase(Locale.FRENCH)`. Ces classes appartiennent au JDK et n'existent pas sur Kotlin/Native (iOS). Erreurs : `Unresolved reference 'java'`, `Unresolved reference 'Calendar'`. |
| **Choix technique** | **kotlinx-datetime**, bibliothèque officielle JetBrains : `Clock.System.now()` remplace `Calendar.getInstance()`, `Instant.fromEpochMilliseconds(ts).toLocalDateTime(TimeZone.currentSystemDefault())` remplace `cal.timeInMillis = ts; cal.get(...)`, et `LocalDateTime(...).toInstant(tz)` remplace les `cal.set(...)`. Deux méthodes ont été ajoutées : `plusMonths(offset)`, un calcul arithmétique pur avec `floorDiv`/`mod` qui gère le passage d'une année à l'autre, et `toEpochMillis(day, hour)`. **`displayLabel` est retiré du domaine** et devient la fonction `@Composable YearMonth.displayLabel()`, construite depuis le tableau de chaînes partagé `month_names` (voir 3.9). Le mois reste indexé de 0 à 11, comme avec `Calendar`. |
| **Justification** | kotlinx-datetime s'appuie sur `java.time` pour la cible Android et sur l'API Foundation (`NSTimeZone`) pour iOS : les calculs de fuseau horaire sont donc identiques sur les deux plateformes. Il n'existe pas de formateur de dates localisé en code commun, et le nom des mois est un libellé d'interface. Le placer dans le catalogue de ressources évite un `expect`/`actual` autour de `SimpleDateFormat` / `NSDateFormatter`, et le rend traduisible. La `Clock` et le `TimeZone` sont passés en paramètres (avec des valeurs par défaut) pour rendre les tests déterministes. |

### 3.4 `data/repository/TransactionRepository.kt`

| | |
|---|---|
| **Problème rencontré** | Aucun. L'interface expose `Flow<List<Transaction>>` et des fonctions `suspend`. |
| **Choix technique** | Déplacement tel quel. |
| **Justification** | `kotlinx.coroutines.flow.Flow` et les fonctions `suspend` font partie de kotlinx-coroutines et du langage, disponibles sur toutes les cibles KMP. Le contrat asynchrone et réactif est conservé sans modification. |

### 3.5 `data/repository/FakeTransactionRepository.kt`

| | |
|---|---|
| **Problème rencontré** | **(a)** `java.util.UUID.randomUUID()` pour générer les identifiants ; **(b)** `java.util.Calendar` avec `cal.set(Calendar.MONTH, currentMonth + monthOffset)`, qui s'appuie sur le mode « lenient » de `Calendar` pour passer au mois 12 ou -1. Erreurs : `Unresolved reference 'UUID'`, `Unresolved reference 'Calendar'`. |
| **Choix technique** | **(a)** `kotlin.uuid.Uuid.random().toString()`, dans la bibliothèque standard Kotlin depuis la version 2.0.20. Son opt-in `ExperimentalUuidApi` est activé une fois pour tout le module dans le `build.gradle.kts`. **(b)** `referenceMonth.plusMonths(offset).toEpochMillis(day, hour)`, qui réutilise le domaine migré. Le mois de référence est maintenant un paramètre du constructeur, avec le mois courant comme valeur par défaut. |
| **Justification** | Aucune dépendance supplémentaire n'est nécessaire pour les UUID, et le format produit (UUID v4 de 36 caractères) est le même qu'avec `java.util.UUID`. Le passage d'une année à l'autre ne dépend plus d'un comportement propre à `Calendar` : il est calculé explicitement, donc identique sur Android et iOS. Le jeu de données devient reproductible dans les tests. |

### 3.6 `viewmodel/EcoBudgetUiState.kt` (extrait de `EcoBudgetViewModel.kt`)

| | |
|---|---|
| **Problème rencontré** | Aucune dépendance plateforme directe, mais la classe d'état était définie dans le même fichier que le ViewModel. |
| **Choix technique** | Extraction dans son propre fichier, dans `commonMain`. Les propriétés calculées (`budgetUsageRatio`, `budgetUsagePercentage`, `isAllCategoriesSelected`) sont conservées. |
| **Justification** | Une `data class` immuable qui ne contient que des types purs est le contrat d'état le plus facile à partager. Elle est consommée par Jetpack Compose côté Android et pourrait l'être par SwiftUI côté iOS. La séparer du ViewModel rend le rôle de chaque fichier explicite. |

### 3.7 `viewmodel/EcoBudgetViewModel.kt`

| | |
|---|---|
| **Problème rencontré** | **(a)** `androidx.lifecycle.ViewModel` et `viewModelScope` venaient de l'artefact AndroidX Android-only (`lifecycle-viewmodel-compose` / `lifecycle-runtime-ktx`), absent du classpath de `commonMain` : `Unresolved reference 'ViewModel'`. **(b)** `System.currentTimeMillis()`, une API JVM. **(c)** `java.util.Calendar`, pour dater une dépense au 15 du mois affiché. **(d)** `java.util.UUID`. |
| **Choix technique** | **(a)** Dépendance `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` en `commonMain`. Elle expose les **mêmes packages** (`androidx.lifecycle.ViewModel`, `androidx.lifecycle.viewModelScope`), donc aucun import n'a changé. **(b)** `clock.now().toEpochMilliseconds()`, avec une `Clock` injectable. **(c)** `currentYearMonth.toEpochMillis(day = 15, hour = 12)`. **(d)** `Uuid.random()`. |
| **Justification** | Côté Android, l'artefact JetBrains correspond directement à `androidx.lifecycle:lifecycle-viewmodel`, sans couche intermédiaire. Le ViewModel partagé **est donc un vrai `ViewModel` AndroidX** : il survit aux changements de configuration, et `by viewModels()`, `onCleared()` et `viewModelScope` s'annulent comme avant. Côté iOS, le même code fournit une implémentation Kotlin/Native de `ViewModel` et de `viewModelScope`, qui tourne sur `Dispatchers.Main` (la file principale). Tous les paramètres du constructeur ont une valeur par défaut : Kotlin/JVM génère donc un constructeur sans argument, et la fabrique par défaut de `by viewModels()` continue de fonctionner sans `ViewModelProvider.Factory`. |

### 3.8 `ui/resources/ResourceMappings.kt` (nouveau, `commonMain`)

| | |
|---|---|
| **Problème rencontré** | Après les migrations 3.1 et 3.3, l'UI n'avait plus de moyen d'obtenir le libellé d'une catégorie ou d'un mois. |
| **Choix technique** | Deux extensions : `val Category.label: StringResource` et `@Composable fun YearMonth.displayLabel(): String`, qui s'appuie sur `stringArrayResource(Res.array.month_names)`. |
| **Justification** | Ce fichier est le seul point de liaison entre le domaine et le catalogue de ressources. Il est commun, donc réutilisable par une UI iOS en Compose Multiplatform. |

### 3.9 Ressources : `res/values/strings.xml` → `composeResources/values/strings.xml`

| | |
|---|---|
| **Problème rencontré** | Les libellés étaient des ressources Android (`R.string.*`, lues avec `androidx.compose.ui.res.stringResource`), inaccessibles depuis le code commun et depuis iOS. Deux incompatibilités de format sont apparues à la migration : **(a)** le moteur de formatage de Compose Multiplatform ne remplace que les arguments **positionnels** (`%1$s`, `%1$d`), pas `%s` / `%d` ; **(b)** les échappements propres à AAPT (`\'`, `%%`) ne sont pas les règles du catalogue CMP. |
| **Choix technique** | Catalogue `shared/src/commonMain/composeResources/values/strings.xml`, qui génère la classe `com.example.shared.resources.Res`. Les formats ont été numérotés (`Total dépensé : %1$s FCFA`, `%1$d% consommé`, etc.), les apostrophes ne sont plus échappées, et un `string-array` `month_names` a été ajouté. Dans l'UI de `:app`, `R.string.x` devient `Res.string.x`, et l'import `androidx.compose.ui.res.stringResource` devient `org.jetbrains.compose.resources.stringResource`, avec la même signature `(ressource, vararg args)`. |
| **Justification** | Une seule source de vérité pour les libellés sur les deux plateformes. Sur Android, les ressources de la bibliothèque sont emballées dans les *assets* de l'AAR et lues à l'exécution. Sur iOS, elles sont intégrées au framework. **Exception assumée** : `app_name` reste aussi dans `app/src/main/res/values/strings.xml`, car l'`AndroidManifest.xml` (`android:label="@string/app_name"`) est lu par le système Android avant l'exécution de tout code Kotlin, et ne peut référencer qu'une ressource AAPT. |

### 3.10 Fichiers restés dans `:app` (et pourquoi)

| Fichier | Dépendances plateforme conservées | Justification |
|---|---|---|
| `MainActivity.kt` | `ComponentActivity`, `by viewModels()` | Point d'entrée Android par nature. Aucun changement de code : il reçoit le ViewModel partagé. |
| `ui/screens/*`, `ui/components/*` | `java.text.NumberFormat`, `SimpleDateFormat`, `collectAsStateWithLifecycle` | Le cahier des charges demande que l'**application native Android** consomme le socle commun : l'UI reste en Jetpack Compose et peut utiliser des API JVM. Seule la source des libellés a changé. |
| `ui/theme/*` | `dynamicLight/DarkColorScheme(LocalContext)` | Le thème Material You dépend d'Android 12 et plus. |

---

## 4. Gestion d'état et cycle de vie

La chaîne réactive est **identique** à la version d'origine. Elle se trouve maintenant entièrement dans `commonMain` :

```
repository.getTransactions() ─────────────────────────┐
_currentMonth ────────┐                               │
_selectedCategories ──┼─ combine ─────────────────────┼─ combine ─► stateIn(viewModelScope, WhileSubscribed(5_000)) ─► uiState: StateFlow
_monthlyBudget ───────┘                               │
_isAddDialogOpen ─────┬─ combine ─────────────────────┘
_editingTransaction ──┘
```

- **Flux asynchrones** : `MutableStateFlow`, `combine` et `stateIn` viennent de `kotlinx-coroutines-core`, qui est
  multiplateforme. Aucune adaptation n'a été nécessaire.
- **Portée des coroutines** : `viewModelScope` (lifecycle KMP) est annulé dans `onCleared()` sur les deux plateformes.
  `saveTransaction()` et `deleteTransaction()` y lancent leurs écritures.
- **`SharingStarted.WhileSubscribed(5_000)`** : la collecte en amont s'arrête 5 s après la disparition du dernier
  observateur, ce qui couvre une rotation d'écran sans relancer la chaîne.
- **Côté Android** : `collectAsStateWithLifecycle()` suspend la collecte quand l'Activity passe sous `STARTED`. Le
  cycle de vie Android est donc respecté sans aucun code Android dans le ViewModel.
- **Testabilité** : `Clock` et `TransactionRepository` sont injectables. Les tests `commonTest` remplacent
  `Dispatchers.Main` par `UnconfinedTestDispatcher`.

---

## 5. Centralisation des ressources textuelles

Exemple d'usage dans `EcoBudgetScreen.kt` :

```kotlin
import com.example.shared.resources.*                       // Res + accesseurs générés
import org.jetbrains.compose.resources.stringResource
import com.example.ui.resources.label
import com.example.ui.resources.displayLabel

Text(stringResource(Res.string.budget_usage_percent_format, uiState.budgetUsagePercentage))
Text("${cat.emoji} ${stringResource(cat.label)}")
Text(stringResource(Res.string.transactions_detail_title_format, uiState.currentMonth.displayLabel()))
```

Pour ajouter une langue, il suffit de créer `composeResources/values-en/strings.xml` : Android et iOS en profitent
tous les deux.

---

## 6. Validation fonctionnelle

### 6.1 Tests automatisés (`shared/src/commonTest`)

```bash
./gradlew :shared:testDebugUnitTest     # tests communs exécutés sur la JVM Android
./gradlew :shared:iosSimulatorArm64Test # sur macOS uniquement
```

| Test | Ce qu'il vérifie |
|---|---|
| `YearMonthTest` | `previous()` / `next()` au passage d'année, `plusMonths(-14)`, aller-retour timestamp ↔ mois |
| `EcoBudgetViewModelTest.dashboardComputesMonthlyTotals` | 7 dépenses, total 365 800 FCFA, reste 134 200 FCFA, 73 % consommé |
| `…categoryFiltersSupportMultiSelection` | Filtre Transport (6 000), multi-sélection Transport + Logement (304 000), bouton « Tous » |
| `…monthNavigationFiltersTransactions` | Mois précédent (372 000), mois suivant (270 000), retour au mois courant |
| `…addEditAndDeleteTransaction` | Ajout, modification et suppression, avec recalcul des totaux |

### 6.2 Exécution sur émulateur Android

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug     # émulateur démarré
```

Liste de contrôle de non-régression :

- [ ] **Tableau de bord** : la carte « BUDGET RESTANT DISPONIBLE » affiche le montant et le pourcentage consommé.
- [ ] **Calcul des dépenses** : un ajout avec « + » met à jour le total, le reste et la barre de progression.
- [ ] **Édition / suppression** : un tap sur une carte ouvre « Modifier la dépense » ; la corbeille supprime la dépense.
- [ ] **Navigation mensuelle** : les flèches changent le mois (« Août 2026 » → « Juillet 2026 ») ; le badge « En cours » ramène au mois courant.
- [ ] **Filtres** : sélection d'une ou plusieurs catégories, bandeau récapitulatif, bouton « Tous ».
- [ ] **Libellés** : tous les textes viennent du catalogue partagé (vérifiable en modifiant une chaîne dans `composeResources`).

> _Captures d'écran de l'exécution sur émulateur : à insérer ici._

---

## 7. Historique des commits

| Commit | Étape |
|---|---|
| `build: préparer la chaîne d'outils pour Kotlin Multiplatform` | Catalogue de versions, Kotlin 2.1.21, suppression de Room/KSP |
| `feat(shared): créer le module KMP partagé (Android + iOS) et le lier à :app` | `shared/build.gradle.kts`, `settings`, dépendance `:app → :shared` |
| `refactor(model): migrer Category, Transaction et YearMonth vers commonMain` | Domaine neutre (kotlinx-datetime), adaptateurs Android temporaires |
| `refactor(data): migrer TransactionRepository et FakeTransactionRepository vers commonMain` | `kotlin.uuid.Uuid`, dates calculées sans `Calendar` |
| `refactor(viewmodel): migrer EcoBudgetUiState et EcoBudgetViewModel vers commonMain` | Lifecycle KMP, `Clock` injectable |
| `test(shared): tests commonTest de non-régression…` | Tests multiplateformes |
| `feat(resources): centraliser les libellés dans le catalogue Compose Multiplatform partagé` | `Res.string`, suppression des adaptateurs temporaires |
| `docs: document technique de synthèse` | Ce README |

Chaque étape a été conçue pour laisser le projet compilable. Pendant l'étape « model », un fichier temporaire
`app/.../model/ModelAndroidLabels.kt` fournissait encore `labelResId` et `displayLabel` à l'UI Android. Il a été
supprimé une fois le catalogue partagé en place.

---

## 8. Limites et pistes d'évolution

- **Application iOS** : le module produit le framework `Shared` (statique), mais aucun projet Xcode n'est fourni. Pour
  aller plus loin : ajouter `iosApp/` avec un `MainViewController` Compose Multiplatform, ou une UI SwiftUI qui observe
  `uiState`.
- **Formatage des montants** : `NumberFormat` (JVM) reste dans l'UI Android. Si l'UI est partagée plus tard, il faudra
  un formateur commun (implémentation Kotlin pure ou `expect`/`actual` avec `NSNumberFormatter`).
- **Persistance** : `FakeTransactionRepository` est en mémoire. Room KMP (2.7+) ou SQLDelight pourraient fournir une
  implémentation persistante de `TransactionRepository`, sans modifier le ViewModel.
