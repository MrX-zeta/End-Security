package com.luis.end_security.feature.auth.data

import com.luis.end_security.feature.auth.domain.AuthRepository
import com.luis.end_security.feature.auth.domain.OnboardingRepository
import com.luis.end_security.feature.auth.domain.PasswordStrengthEstimator
import com.luis.end_security.feature.auth.domain.UnicodeCharacterData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    abstract fun bindPasswordStrengthEstimator(impl: ZxcvbnPasswordStrengthEstimator): PasswordStrengthEstimator

    @Binds
    abstract fun bindUnicodeCharacterData(impl: IcuUnicodeCharacterData): UnicodeCharacterData

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
