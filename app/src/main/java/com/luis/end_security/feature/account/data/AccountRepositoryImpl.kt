package com.luis.end_security.feature.account.data

import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.feature.account.domain.AccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
) : AccountRepository {

    override suspend fun hasLocalAccount(): Boolean = accountDao.exists()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountDataModule {

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
}
