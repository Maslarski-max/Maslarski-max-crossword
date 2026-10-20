package com.maslarski.crossword.di

import android.content.Context
import com.maslarski.crossword.BuildConfig
import com.maslarski.crossword.data.billing.PlayStoreRepository
import com.maslarski.crossword.data.billing.PurchaseSignatureVerifier
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.repository.StoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StoreModule {

    @Provides
    @Singleton
    fun provideStoreRepository(
        @ApplicationContext context: Context,
        entitlements: EntitlementRepository,
        @ApplicationScope scope: CoroutineScope,
    ): StoreRepository = PlayStoreRepository(
        context,
        entitlements,
        scope,
        PurchaseSignatureVerifier(BuildConfig.PLAY_LICENSE_KEY),
    )
}
