package com.razer.neuron.di

import android.content.Context
import android.preference.PreferenceManager
import com.limelight.computers.ComputerDatabaseManager
import com.razer.neuron.provider.sources.NeuronAndroidCryptoSource
import com.razer.neuron.provider.sources.NeuronComputerDetailsSource
import com.razer.neuron.provider.sources.NeuronMetaDataSource
import com.razer.neuron.provider.sources.RemotePlaySettingsProviderSource
import com.razer.neuron.settings.StreamingManager
import com.razer.neuron.settings.remoteplay.RemotePlaySettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideSettingsContent() = RemotePlaySettingsProviderSource()

    @Singleton
    @Provides
    fun provideAndroidCrypto() = NeuronAndroidCryptoSource()

    @Singleton
    @Provides
    fun provideComputerDetailsSource(computerDatabaseManager: ComputerDatabaseManager) =
        NeuronComputerDetailsSource(
            manager = computerDatabaseManager
        )

    @Singleton
    @Provides
    fun provideMetaDataSource() =
        NeuronMetaDataSource()

    @Singleton
    @Provides
    fun provideComputerDatabaseManager(@ApplicationContext context: Context) =
        ComputerDatabaseManager(context)

    @Singleton
    @Provides
    fun provideStreamingManager(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        computerDatabaseManager: ComputerDatabaseManager) = StreamingManager(
        ioDispatcher = ioDispatcher,
        manager = computerDatabaseManager
    )

    @Singleton
    @Provides
    fun provideRemotePlaySettingsManager(
        @ApplicationContext context: Context
    ) = RemotePlaySettingsManager(
        context = context,
        moonlightPref = PreferenceManager.getDefaultSharedPreferences(context)
    )


}