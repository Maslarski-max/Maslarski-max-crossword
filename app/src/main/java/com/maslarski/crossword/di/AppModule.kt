package com.maslarski.crossword.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.maslarski.crossword.data.ads.AdsManager
import com.maslarski.crossword.data.ads.ConsentManager
import com.maslarski.crossword.BuildConfig
import com.maslarski.crossword.data.billing.PlayStoreRepository
import com.maslarski.crossword.data.billing.PurchaseSignatureVerifier
import com.maslarski.crossword.data.integrity.PlayIntegrityChecker
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.repository.AssetPuzzleRepository
import com.maslarski.crossword.data.repository.CoinLedger
import com.maslarski.crossword.data.repository.RoomArenaRepository
import com.maslarski.crossword.data.repository.DataStoreSettingsRepository
import com.maslarski.crossword.data.repository.RoomEntitlementRepository
import com.maslarski.crossword.data.repository.RoomProfileRepository
import com.maslarski.crossword.data.repository.RoomProgressRepository
import com.maslarski.crossword.data.repository.RoomWalletRepository
import com.maslarski.crossword.data.telemetry.FirebaseTelemetry
import com.maslarski.crossword.data.telemetry.Telemetry
import com.maslarski.crossword.domain.engine.GameRules
import com.maslarski.crossword.domain.parser.PuzzleParser
import com.maslarski.crossword.domain.profile.AchievementTracker
import com.maslarski.crossword.domain.repository.ProfileRepository
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.SettingsRepository
import com.maslarski.crossword.domain.repository.StoreRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CrosswordDatabase =
        Room.databaseBuilder(context, CrosswordDatabase::class.java, CrosswordDatabase.NAME)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("INSERT OR IGNORE INTO wallet (id, coins, lifetimeEarned) VALUES (0, ${GameRules.STARTING_COINS}, 0)")
                }
            })
            .build()

    @Provides @Singleton
    fun providePuzzleRepository(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): PuzzleRepository =
        AssetPuzzleRepository(context.assets, PuzzleParser(), io)

    @Provides @Singleton
    fun provideCoinLedger(): CoinLedger = CoinLedger()

    @Provides @Singleton
    fun provideProgressRepository(db: CrosswordDatabase, clock: Clock, ledger: CoinLedger): ProgressRepository =
        RoomProgressRepository(db, clock, ledger)

    @Provides @Singleton
    fun provideArenaRepository(db: CrosswordDatabase, clock: Clock, ledger: CoinLedger): ArenaRepository =
        RoomArenaRepository(db, clock, ledger)

    @Provides @Singleton
    fun provideProfileRepository(db: CrosswordDatabase, arena: ArenaRepository, clock: Clock, ledger: CoinLedger): ProfileRepository =
        RoomProfileRepository(db, arena, clock, ledger)

    @Provides @Singleton
    fun provideAchievementTracker(profile: ProfileRepository, clock: Clock, @ApplicationScope scope: CoroutineScope): AchievementTracker =
        AchievementTracker(profile, clock, scope)

    @Provides @Singleton
    fun provideWalletRepository(db: CrosswordDatabase, ledger: CoinLedger): WalletRepository = RoomWalletRepository(db.walletDao(), ledger)

    @Provides @Singleton
    fun provideEntitlementRepository(db: CrosswordDatabase, clock: Clock, ledger: CoinLedger): EntitlementRepository =
        RoomEntitlementRepository(db, clock, ledger)

    @Provides @Singleton
    fun provideStoreRepository(
        @ApplicationContext context: Context,
        entitlements: EntitlementRepository,
        @ApplicationScope scope: CoroutineScope,
    ): StoreRepository = PlayStoreRepository(context, entitlements, scope, PurchaseSignatureVerifier(BuildConfig.PLAY_LICENSE_KEY))

    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(context.settingsDataStore)

    @Provides @Singleton
    fun provideConsentManager(@ApplicationContext context: Context): ConsentManager = ConsentManager(context)

    @Provides @Singleton
    fun provideAdsManager(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher io: CoroutineDispatcher,
    ): AdsManager = AdsManager(context, scope, io)

    @Provides @Singleton
    fun provideFirebaseTelemetry(@ApplicationContext context: Context): FirebaseTelemetry = FirebaseTelemetry(context)

    @Provides
    fun provideTelemetry(telemetry: FirebaseTelemetry): Telemetry = telemetry

    @Provides @Singleton
    fun providePlayIntegrityChecker(@ApplicationContext context: Context): PlayIntegrityChecker = PlayIntegrityChecker(context)
}
