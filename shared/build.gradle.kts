import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Module partagé EcoBudget (Kotlin Multiplatform + Compose Multiplatform).
 *
 * Contient le socle commun consommé par l'application Android native (:app)
 * et exportable vers iOS sous forme de framework "Shared" :
 *   - domaine (model), données (repository), état d'interface + ViewModel (viewmodel)
 *   - catalogue de ressources textuelles (src/commonMain/composeResources)
 */
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  // Cible Android : produit une AAR consommée par :app
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  // Cibles iOS (appareil réel + simulateurs Intel et Apple Silicon)
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "Shared"
      isStatic = true
    }
  }

  sourceSets {
    all {
      // kotlin.uuid.Uuid (stdlib multiplateforme) est encore marqué expérimental
      languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
    }

    commonMain.dependencies {
      // Flux asynchrones (Flow / StateFlow) — multiplateforme
      api(libs.kotlinx.coroutines.core)
      // Dates et fuseaux horaires — remplace java.util.Calendar / java.text.SimpleDateFormat
      api(libs.kotlinx.datetime)
      // ViewModel + viewModelScope multiplateformes (JetBrains) — remplace androidx.lifecycle Android-only
      api(libs.jetbrains.lifecycle.viewmodel)
      // Compose runtime + catalogue de ressources partagé (Res.string.*)
      implementation(libs.compose.mp.runtime)
      api(libs.compose.mp.resources)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    androidMain.dependencies {
      // Fournit Dispatchers.Main (Looper Android) utilisé par viewModelScope
      implementation(libs.kotlinx.coroutines.android)
    }
  }
}

android {
  namespace = "com.example.shared"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

// Catalogue de ressources Compose Multiplatform :
// la classe Res est publique pour être utilisable depuis l'UI du module :app.
compose.resources {
  publicResClass = true
  packageOfResClass = "com.example.shared.resources"
  generateResClass = always
}
