package com.orgzly.android.di.module

import android.app.Application
import android.content.res.Resources
import com.orgzly.android.LocalStorage
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.data.logs.AppLogsRepository
import com.orgzly.android.data.logs.DatabaseAppLogsRepository
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.orgzly.android.data.observers.DataChangedSignal
import kotlinx.coroutines.CoroutineScope
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.reminders.RemindersDataObserver
import com.orgzly.android.repos.RepoFactory
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.android.widgets.ListWidgetDataObserver
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
internal open class DataModule {
    @Provides
    @Singleton
    internal fun providesDataRepository(
            app: Application,
            database: OrgzlyDatabase,
            repoFactory: RepoFactory,
            resources: Resources,
            localStorage: LocalStorage
    ): DataRepository {
        return DataRepository(app, database, repoFactory, resources, localStorage)
    }

    @Provides
    @Singleton
    internal fun providesRepoFactory(app: Application, dbRepoBookRepository: DbRepoBookRepository): RepoFactory {
        return RepoFactory(app, dbRepoBookRepository)
    }

    @Provides
    @Singleton
    internal fun providesUserActionRunnerFactory(): UseCaseRunner.Factory {
        return UseCaseRunner.Factory()
    }

    @Provides
    @Singleton
    internal fun providesLogsRepository(database: OrgzlyDatabase): AppLogsRepository {
        return DatabaseAppLogsRepository(database)
    }

    @Provides
    @Singleton
    internal fun providesAppScope(): CoroutineScope {
        return ProcessLifecycleOwner.get().lifecycleScope
    }

    @Provides
    @Singleton
    internal fun providesDataChangedSignal(
            database: OrgzlyDatabase,
            scope: CoroutineScope
    ): DataChangedSignal {
        return DataChangedSignal(database, scope)
    }

    @Provides
    @Singleton
    internal fun providesListWidgetDataObserver(
            signal: DataChangedSignal,
            app: Application
    ): ListWidgetDataObserver {
        return ListWidgetDataObserver(signal, app)
    }

    @Provides
    @Singleton
    internal fun providesRemindersDataObserver(
            signal: DataChangedSignal,
            app: Application
    ): RemindersDataObserver {
        return RemindersDataObserver(signal, app)
    }


//    @Provides
//    fun providesLocalStorage(context: Application): LocalStorage {
//        return LocalStorage(context.applicationContext)
//    }
}