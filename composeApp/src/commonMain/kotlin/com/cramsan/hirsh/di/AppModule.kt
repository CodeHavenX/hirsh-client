package com.cramsan.hirsh.di

import com.cramsan.hirsh.network.ApiConfig
import com.cramsan.hirsh.network.XsrfHeaderPlugin
import com.cramsan.hirsh.network.installApiErrorValidator
import com.cramsan.hirsh.preferences.AppPreferences
import com.cramsan.hirsh.repository.AccountRepository
import com.cramsan.hirsh.repository.AuthRepository
import com.cramsan.hirsh.repository.DefaultSessionRepository
import com.cramsan.hirsh.repository.FakeAuthRepository
import com.cramsan.hirsh.repository.HospitalizationRepository
import com.cramsan.hirsh.repository.InMemoryAccountRepository
import com.cramsan.hirsh.repository.InMemoryHospitalizationRepository
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.screens.accounts.AccountsViewModel
import com.cramsan.hirsh.ui.screens.admision.AdmisionViewModel
import com.cramsan.hirsh.ui.screens.evolucionnew.NuevaEvolucionViewModel
import com.cramsan.hirsh.ui.screens.evolucionview.EvolucionViewViewModel
import com.cramsan.hirsh.ui.screens.historiaclinica.HistoriaClinicaViewModel
import com.cramsan.hirsh.ui.screens.hospitalization.HospitalizationViewModel
import com.cramsan.hirsh.ui.screens.login.LoginViewModel
import com.cramsan.hirsh.ui.screens.patientedit.EditPatientViewModel
import com.cramsan.hirsh.ui.screens.patienthistory.PatientHistoryViewModel
import com.cramsan.hirsh.ui.screens.patientlist.PatientListViewModel
import com.cramsan.hirsh.ui.screens.patientrecord.PatientRecordViewModel
import com.cramsan.hirsh.ui.screens.patientregister.RegisterPatientViewModel
import com.cramsan.hirsh.ui.screens.profile.ProfileViewModel
import com.cramsan.hirsh.util.Clock
import com.cramsan.hirsh.util.DefaultClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

private val sharedModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    single {
        val engineFactory = get<HttpClientEngineFactory<*>>()
        val engineTuning = get<HttpClientEngineTuning>()
        // Deferred, not `val sessionRepository = get<SessionRepository>()`, so this HttpClient
        // single's own construction never forces SessionRepository (and so AuthRepository) to
        // exist first -- once HISS-611 gives AuthRepository a real HttpClient dependency of its
        // own, an eager get() here would be a genuine constructor-injection cycle. This lambda
        // only actually resolves SessionRepository when a 401 happens, well after both exist.
        val sessionRepositoryProvider = { get<SessionRepository>() }
        HttpClient(engineFactory) {
            install(ContentNegotiation) { json(get()) }
            install(Logging) { level = LogLevel.INFO }
            install(HttpCookies)
            install(XsrfHeaderPlugin)
            installApiErrorValidator(onUnauthorized = { sessionRepositoryProvider().forceLogout() })
            defaultRequest { url(ApiConfig.BASE_URL) }
            engine(engineTuning)
        }
    }
    singleOf(::AppPreferences)
    singleOf(::FakeAuthRepository) bind AuthRepository::class
    singleOf(::DefaultSessionRepository) bind SessionRepository::class
    singleOf(::InMemoryPatientRepository) bind PatientRepository::class
    singleOf(::InMemoryAccountRepository) bind AccountRepository::class
    singleOf(::InMemoryHospitalizationRepository) bind HospitalizationRepository::class
    singleOf(::DefaultClock) bind Clock::class

    viewModelOf(::LoginViewModel)
    viewModelOf(::PatientListViewModel)
    viewModelOf(::PatientRecordViewModel)
    viewModelOf(::RegisterPatientViewModel)
    viewModelOf(::EditPatientViewModel)
    viewModelOf(::PatientHistoryViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::AdmisionViewModel)
    viewModelOf(::HospitalizationViewModel)
    viewModelOf(::NuevaEvolucionViewModel)
    viewModelOf(::EvolucionViewViewModel)
    viewModelOf(::HistoriaClinicaViewModel)
    viewModelOf(::AccountsViewModel)
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, sharedModule)
    }
}
