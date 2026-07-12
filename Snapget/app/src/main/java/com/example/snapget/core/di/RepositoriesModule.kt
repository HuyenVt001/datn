package com.example.snapget.core.di

import com.example.snapget.core.data.MainLog
import com.example.snapget.core.data.MainLogImpl
import com.example.snapget.core.data.Store
import com.example.snapget.core.data.StoreImpl2
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesModule {
    @Binds
    @Singleton
    abstract fun bindMainLog(mainLog: MainLogImpl): MainLog

    @Binds
    @Singleton
    abstract fun bindStore(store: StoreImpl2): Store
}
