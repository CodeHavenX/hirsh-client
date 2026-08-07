package com.cramsan.hirsh.di

import com.cramsan.hirsh.preferences.AppPreferences
import com.cramsan.hirsh.repository.AccountRepository
import com.cramsan.hirsh.repository.AuthRepository
import com.cramsan.hirsh.repository.DefaultSessionRepository
import com.cramsan.hirsh.repository.FakeAuthRepository
import com.cramsan.hirsh.repository.InMemoryAccountRepository
import com.cramsan.hirsh.repository.InMemoryPatientRepository
import com.cramsan.hirsh.repository.PatientRepository
import com.cramsan.hirsh.repository.SessionRepository
import com.cramsan.hirsh.ui.screens.login.LoginViewModel
import com.cramsan.hirsh.ui.screens.patientlist.PatientListViewModel
import com.cramsan.hirsh.ui.screens.patientrecord.PatientRecordViewModel
import com.cramsan.hirsh.ui.screens.patientregister.RegisterPatientViewModel
import com.cramsan.hirsh.ui.screens.profile.ProfileViewModel
import com.cramsan.hirsh.util.Clock
import com.cramsan.hirsh.util.DefaultClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
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
        HttpClient(engineFactory) {
            install(ContentNegotiation) { json(get()) }
            install(Logging) { level = LogLevel.INFO }
        }
    }
    singleOf(::AppPreferences)
    singleOf(::FakeAuthRepository) bind AuthRepository::class
    singleOf(::DefaultSessionRepository) bind SessionRepository::class
    singleOf(::InMemoryPatientRepository) bind PatientRepository::class
    singleOf(::InMemoryAccountRepository) bind AccountRepository::class
    singleOf(::DefaultClock) bind Clock::class

    viewModelOf(::LoginViewModel)
    viewModelOf(::PatientListViewModel)
    viewModelOf(::PatientRecordViewModel)
    viewModelOf(::RegisterPatientViewModel)
    viewModelOf(::ProfileViewModel)
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, sharedModule)
    }
}
