package com.maslarski.crossword.di

import com.maslarski.crossword.data.billing.FakeStoreRepository
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.repository.StoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StoreModule {

    @Provides
    @Singleton
    fun provideStoreRepository(
        entitlements: EntitlementRepository,
        @ApplicationScope scope: CoroutineScope,
    ): StoreRepository = FakeStoreRepository(entitlements, scope)
}
