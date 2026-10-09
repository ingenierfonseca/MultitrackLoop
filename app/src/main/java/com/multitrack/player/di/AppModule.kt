package com.multitrack.player.di

import android.content.ContentResolver
import android.content.Context
import com.multitrack.player.data.audio.MultitrackPlaybackEngine
import com.multitrack.player.data.datasource.ContentResolverDataSource
import com.multitrack.player.data.repository.AudioTrackRepositoryImpl
import com.multitrack.player.data.repository.PlaybackProjectRepositoryImpl
import com.multitrack.player.domain.repository.AudioTrackRepository
import com.multitrack.player.domain.repository.PlaybackEngine
import com.multitrack.player.domain.repository.PlaybackProjectRepository
import com.multitrack.player.domain.usecase.CreatePlaybackSessionUseCase
import com.multitrack.player.domain.usecase.LoadPlaybackProjectUseCase
import com.multitrack.player.domain.usecase.PlaybackControlUseCase
import com.multitrack.player.domain.usecase.ResolvePickedTrackUseCase
import com.multitrack.player.domain.usecase.SavePlaybackProjectUseCase
import com.multitrack.player.domain.usecase.ValidateBpmUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideContentResolverDataSource(resolver: ContentResolver): ContentResolverDataSource =
        ContentResolverDataSource(resolver)

    @Provides
    @Singleton
    fun provideAudioTrackRepository(
        dataSource: ContentResolverDataSource,
        resolver: ContentResolver
    ): AudioTrackRepository = AudioTrackRepositoryImpl(dataSource, resolver)

    @Provides
    @Singleton
    fun providePlaybackProjectRepository(resolver: ContentResolver): PlaybackProjectRepository =
        PlaybackProjectRepositoryImpl(resolver)

    @Provides
    @Singleton
    fun providePlaybackEngine(resolver: ContentResolver): PlaybackEngine =
        MultitrackPlaybackEngine(resolver)

    @Provides
    fun provideValidateBpmUseCase(): ValidateBpmUseCase = ValidateBpmUseCase()

    @Provides
    fun provideCreateSessionUseCase(): CreatePlaybackSessionUseCase = CreatePlaybackSessionUseCase()

    @Provides
    fun provideResolvePickedTrackUseCase(repo: AudioTrackRepository): ResolvePickedTrackUseCase =
        ResolvePickedTrackUseCase(repo)

    @Provides
    fun provideSavePlaybackProjectUseCase(
        repository: PlaybackProjectRepository
    ): SavePlaybackProjectUseCase = SavePlaybackProjectUseCase(repository)

    @Provides
    fun provideLoadPlaybackProjectUseCase(
        repository: PlaybackProjectRepository
    ): LoadPlaybackProjectUseCase = LoadPlaybackProjectUseCase(repository)

    @Provides
    fun providePlaybackControlUseCase(engine: PlaybackEngine): PlaybackControlUseCase =
        PlaybackControlUseCase(engine)
}
