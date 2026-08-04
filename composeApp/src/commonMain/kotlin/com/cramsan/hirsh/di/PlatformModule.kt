package com.cramsan.hirsh.di

import org.koin.core.module.Module

/**
 * Supplies the pieces that only exist per-platform: the Ktor engine backing
 * [io.ktor.client.HttpClient] and the [com.russhwolf.settings.Settings] backing store.
 * See di/PlatformModule.<platform>.kt for each actual.
 */
expect val platformModule: Module
